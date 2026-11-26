package com.grandpavoice.conversation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import com.grandpavoice.R
import com.grandpavoice.settings.forLanguage
import com.grandpavoice.settings.SettingsRepository
import com.grandpavoice.state.CompanionState
import com.grandpavoice.state.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Runs one conversation at a time: fetch key -> connect -> greet -> listen/speak loop -> goodbye.
 * All methods must be called on the main thread (the service's scope).
 *
 * Nothing is recorded or stored: audio streams to OpenAI and is played back, nothing else.
 */
class ConversationManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepo: SettingsRepository,
    private val sounds: Sounds,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    private val keys = KeyProvider(http)
    private val audioRouter = AudioRouter(context)

    private var client: RealtimeClient? = null
    private var sessionId = 0
    private var sessionInstructions = ""
    private var sessionSettings = settingsRepo.current

    private var connectJob: Job? = null
    private var inactivityJob: Job? = null
    private var maxDurationJob: Job? = null
    private var endingJob: Job? = null
    private var resetJob: Job? = null

    /** The model is saying goodbye; end the call as soon as its audio stops. */
    private var ending = false
    private var aiSpeaking = false

    var isActive = false
        private set
    var startedAtMs = 0L
        private set

    fun start() {
        if (isActive) return
        resetJob?.cancel()
        isActive = true
        ending = false
        aiSpeaking = false
        startedAtMs = SystemClock.elapsedRealtime()
        val mySession = ++sessionId
        val settings = settingsRepo.current
        sessionSettings = settings
        CompanionState.set(UiState.Connecting)

        connectJob = scope.launch {
            try {
                if (!hasMicPermission()) {
                    fail(R.string.error_microphone)
                    return@launch
                }
                if (!isOnline()) {
                    fail(R.string.error_no_internet)
                    return@launch
                }
                sessionInstructions = Instructions.build(settings)
                val ticket = withContext(Dispatchers.IO) { keys.fetch(settings, sessionInstructions) }
                if (mySession != sessionId) return@launch

                audioRouter.start(settings.maxVolume)
                val c = RealtimeClient(context, http, listenerFor(mySession))
                client = c
                withTimeout(CONNECT_TIMEOUT_MS) { c.connect(ticket) }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Connect timed out")
                if (mySession == sessionId) fail(R.string.error_connection_lost)
            } catch (e: CancellationException) {
                throw e
            } catch (e: KeyException) {
                Log.w(TAG, "Key: ${e.reason} ${e.message}")
                if (mySession == sessionId) fail(e.messageRes())
            } catch (e: Exception) {
                Log.w(TAG, "Connect failed", e)
                if (mySession == sessionId) fail(R.string.error_connection_lost)
            }
        }
    }

    /** User shook the phone (or tapped) during a conversation: end right away. */
    fun endByUser() {
        if (!isActive) return
        finish()
    }

    /** Service is being destroyed. */
    fun shutdown() {
        teardown()
        resetJob?.cancel()
        CompanionState.set(UiState.Standby)
    }

    // ---------------------------------------------------------------- events

    private fun listenerFor(session: Int) = object : RealtimeClient.Listener {
        override fun onChannelOpen() = post(session) { onChannelOpened() }
        override fun onServerEvent(event: JSONObject) = post(session) { handleEvent(event) }
        override fun onConnectionLost(reason: String) = post(session) {
            Log.w(TAG, "Connection lost: $reason")
            if (ending) finish() else fail(R.string.error_connection_lost)
        }
        override fun onMicrophoneError(reason: String) = post(session) {
            Log.w(TAG, "Microphone error: $reason")
            fail(R.string.error_microphone_busy)
        }
    }

    private fun post(session: Int, block: () -> Unit) {
        scope.launch { if (session == sessionId && isActive) block() }
    }

    private fun onChannelOpened() {
        CompanionState.set(UiState.Thinking)
        requestResponse(Instructions.greeting(sessionSettings), allowTools = false)
        val minutes = settingsRepo.current.maxSessionMinutes.coerceAtLeast(1)
        maxDurationJob?.cancel()
        maxDurationJob = scope.launch {
            delay(minutes * 60_000L)
            requestFarewell(Instructions.farewellTimeLimit(sessionSettings.language))
        }
    }

    private fun handleEvent(event: JSONObject) {
        when (event.optString("type")) {
            "input_audio_buffer.speech_started" -> {
                cancelInactivity()
                if (!ending) CompanionState.set(UiState.Listening)
            }
            // With "patient" turn detection the model may still wait for him to continue,
            // so keep showing "listening" until a response really starts.
            "response.created" -> {
                cancelInactivity()
                if (!aiSpeaking) CompanionState.set(UiState.Thinking)
            }
            "output_audio_buffer.started" -> {
                aiSpeaking = true
                cancelInactivity()
                CompanionState.set(UiState.Speaking)
            }
            "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> {
                aiSpeaking = false
                if (ending) {
                    finish()
                } else {
                    CompanionState.set(UiState.Listening)
                    armInactivity()
                }
            }
            "response.done" -> onResponseDone(event.optJSONObject("response"))
            "error" -> onServerError(event.optJSONObject("error"))
        }
    }

    private fun onResponseDone(response: JSONObject?) {
        val output = response?.optJSONArray("output")
        var spoke = false
        var endCallId: String? = null
        if (output != null) {
            for (i in 0 until output.length()) {
                val item = output.optJSONObject(i) ?: continue
                when (item.optString("type")) {
                    "message" -> spoke = true
                    "function_call" -> if (item.optString("name") == "end_conversation") {
                        endCallId = item.optString("call_id")
                    }
                }
            }
        }

        if (endCallId != null && !ending) {
            if (spoke) {
                beginEnding()
            } else {
                // The model wants to end but didn't say goodbye yet: ask for a short farewell first.
                client?.send(
                    JSONObject()
                        .put("type", "conversation.item.create")
                        .put(
                            "item",
                            JSONObject()
                                .put("type", "function_call_output")
                                .put("call_id", endCallId)
                                .put("output", "{\"ok\":true}"),
                        ),
                )
                requestFarewell(Instructions.farewellUser(sessionSettings.language))
            }
            return
        }

        if (!aiSpeaking && !ending && CompanionState.state.value == UiState.Thinking) {
            CompanionState.set(UiState.Listening)
            armInactivity()
        }
    }

    private fun onServerError(error: JSONObject?) {
        val code = error?.optString("code").orEmpty()
        Log.w(TAG, "Server error: $code ${error?.optString("message")}")
        if (code == "session_expired") finish()
        // Everything else (e.g. cancelling an already-finished response) is harmless; keep talking.
    }

    // ---------------------------------------------------------------- ending

    private fun requestResponse(extraInstructions: String, allowTools: Boolean) {
        val response = JSONObject()
            .put("instructions", sessionInstructions + "\n\n" + extraInstructions)
        if (!allowTools) response.put("tool_choice", "none")
        client?.send(JSONObject().put("type", "response.create").put("response", response))
    }

    private fun requestFarewell(instruction: String) {
        if (!isActive || ending) return
        if (client == null) {
            finish()
            return
        }
        ending = true
        cancelInactivity()
        CompanionState.set(UiState.Thinking)
        requestResponse(instruction, allowTools = false)
        endingJob?.cancel()
        endingJob = scope.launch {
            delay(FAREWELL_FALLBACK_MS)
            finish()
        }
    }

    /** The goodbye was spoken in the same response; end when the audio finishes. */
    private fun beginEnding() {
        ending = true
        cancelInactivity()
        endingJob?.cancel()
        endingJob = scope.launch {
            delay(2_500)
            if (!aiSpeaking) {
                finish()
                return@launch
            }
            delay(FAREWELL_FALLBACK_MS)
            finish()
        }
    }

    private fun armInactivity() {
        inactivityJob?.cancel()
        val seconds = settingsRepo.current.inactivitySeconds.coerceAtLeast(15)
        inactivityJob = scope.launch {
            delay(seconds * 1000L)
            requestFarewell(Instructions.farewellInactivity(sessionSettings.language))
        }
    }

    private fun cancelInactivity() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun finish() {
        if (!isActive) return
        teardown()
        sounds.conversationEnded()
        CompanionState.set(UiState.Goodbye)
        resetJob = scope.launch {
            delay(GOODBYE_SCREEN_MS)
            if (!isActive) CompanionState.set(UiState.Standby)
        }
    }

    private fun fail(@StringRes message: Int) {
        teardown()
        CompanionState.set(UiState.Error(message))
        val language = settingsRepo.current.language
        val localized = context.forLanguage(language)
        sounds.speak(localized.getString(message) + " " + localized.getString(R.string.hint_try_again), language)
        resetJob = scope.launch {
            delay(ERROR_SCREEN_MS)
            if (!isActive) CompanionState.set(UiState.Standby)
        }
    }

    private fun teardown() {
        isActive = false
        sessionId++ // invalidates any late callbacks from the old session
        connectJob?.cancel()
        inactivityJob?.cancel()
        maxDurationJob?.cancel()
        endingJob?.cancel()
        connectJob = null
        inactivityJob = null
        maxDurationJob = null
        endingJob = null
        client?.close()
        client = null
        audioRouter.stop()
        ending = false
        aiSpeaking = false
    }

    // ---------------------------------------------------------------- helpers

    private fun hasMicPermission() =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun KeyException.messageRes(): Int = when (reason) {
        KeyException.Reason.NOT_CONFIGURED -> R.string.error_not_configured
        KeyException.Reason.UNAUTHORIZED -> R.string.error_unauthorized
        KeyException.Reason.NO_CREDIT -> R.string.error_no_credit
        KeyException.Reason.RATE_LIMITED -> R.string.error_rate_limited
        KeyException.Reason.SERVER -> R.string.error_service
        KeyException.Reason.NETWORK -> R.string.error_no_internet
    }

    private companion object {
        const val TAG = "Conversation"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val FAREWELL_FALLBACK_MS = 20_000L
        const val GOODBYE_SCREEN_MS = 5_000L
        const val ERROR_SCREEN_MS = 10_000L
    }
}
