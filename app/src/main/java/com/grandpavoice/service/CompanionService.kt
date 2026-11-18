package com.grandpavoice.service

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.grandpavoice.GrandpaVoiceApp
import com.grandpavoice.R
import com.grandpavoice.conversation.ConversationManager
import com.grandpavoice.conversation.Sounds
import com.grandpavoice.settings.SettingsRepository
import com.grandpavoice.shake.ShakeDetector
import com.grandpavoice.shake.ShakeLogic
import com.grandpavoice.state.CompanionState
import com.grandpavoice.state.UiState
import com.grandpavoice.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Long-running foreground service that:
 *  - listens to the accelerometer (also with the screen off, via a partial wake lock),
 *  - owns the conversation, so it keeps running if the screen turns off mid-conversation.
 *
 * Android rule (11+): a foreground service may use the microphone from the background only
 * if it was promoted to the "microphone" type while the app was visible. The activity therefore
 * (re)starts this service every time it is shown, which upgrades it to microphone type.
 * If the service ever runs without that privilege (e.g. restarted by the system while the
 * screen was off), a shake wakes the screen and the conversation starts once the app is visible.
 */
class CompanionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var sounds: Sounds
    private lateinit var conversation: ConversationManager
    private lateinit var shakeDetector: ShakeDetector
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var pendingTimeoutJob: Job? = null

    private var foregroundMode = FG_NONE
    private var micCapable = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsRepo = (application as GrandpaVoiceApp).settings
        sounds = Sounds(this)
        conversation = ConversationManager(this, scope, settingsRepo, sounds)
        shakeDetector = ShakeDetector(getSystemService(SensorManager::class.java), ::onShake)

        scope.launch {
            settingsRepo.flow.collect { s ->
                shakeDetector.updateConfig(
                    ShakeLogic.Config(
                        thresholdG = s.shakeThresholdG,
                        requiredSpikes = s.shakeCount,
                        windowMs = s.shakeWindowMs.toLong(),
                        cooldownMs = s.cooldownMs.toLong(),
                    ),
                )
                setCpuWakeLock(s.detectWhenScreenOff)
            }
        }
        if (!shakeDetector.start()) Log.e(TAG, "No accelerometer on this device")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromUi = intent?.getBooleanExtra(EXTRA_FROM_UI, false) == true
        if (!goForeground(fromUi)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_TOGGLE -> if (conversation.isActive) conversation.endByUser() else startConversation()
            ACTION_START_PENDING -> if (CompanionState.pendingStart) {
                CompanionState.pendingStart = false
                pendingTimeoutJob?.cancel()
                startConversation()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        shakeDetector.stop()
        conversation.shutdown()
        sounds.release()
        setCpuWakeLock(false)
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- shake

    private fun onShake() {
        CompanionState.shakeTriggers.value += 1
        if (CompanionState.testMode) {
            sounds.shakeRecognised()
            return
        }
        if (conversation.isActive) {
            // Ignore the tail of the shake that started this conversation.
            if (SystemClock.elapsedRealtime() - conversation.startedAtMs > IGNORE_SHAKE_AFTER_START_MS) {
                conversation.endByUser()
            }
            return
        }
        sounds.shakeRecognised()
        startConversation()
    }

    private fun startConversation() {
        if (conversation.isActive) return
        wakeScreenAndShowUi()
        if (!micCapable) {
            CompanionState.pendingStart = true
            CompanionState.set(UiState.Connecting)
            pendingTimeoutJob?.cancel()
            pendingTimeoutJob = scope.launch {
                delay(PENDING_START_TIMEOUT_MS)
                if (CompanionState.pendingStart) {
                    CompanionState.pendingStart = false
                    CompanionState.set(UiState.Error(R.string.error_open_app))
                    sounds.speak(getString(R.string.error_open_app))
                    delay(8_000)
                    if (!conversation.isActive) CompanionState.set(UiState.Standby)
                }
            }
            return
        }
        conversation.start()
    }

    // ---------------------------------------------------------------- foreground

    /** @return false if the service could not become a foreground service at all. */
    private fun goForeground(fromUi: Boolean): Boolean {
        val notification = Notifications.standby(this)
        val hasMic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 8–10: no "while-in-use" microphone restriction.
            if (foregroundMode == FG_NONE) startForeground(Notifications.FOREGROUND_ID, notification)
            foregroundMode = FG_OTHER
            micCapable = hasMic
            return true
        }

        val wantMic = hasMic && (fromUi || micCapable)
        if (wantMic && foregroundMode == FG_MIC) return true
        if (wantMic) {
            try {
                ServiceCompat.startForeground(
                    this,
                    Notifications.FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
                foregroundMode = FG_MIC
                micCapable = true
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Could not start as microphone service", e)
            }
        }
        if (foregroundMode != FG_NONE) return true

        micCapable = false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    Notifications.FOREGROUND_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(Notifications.FOREGROUND_ID, notification)
            }
            foregroundMode = FG_OTHER
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
            false
        }
    }

    // ---------------------------------------------------------------- power

    /**
     * Keeps the CPU awake so accelerometer events keep arriving with the screen off.
     * Costs battery: intended for a phone that sits on its charger / stand.
     */
    private fun setCpuWakeLock(enabled: Boolean) {
        if (enabled) {
            if (cpuWakeLock?.isHeld == true) return
            cpuWakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GrandpaVoice:shake")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } else {
            cpuWakeLock?.let { if (it.isHeld) it.release() }
            cpuWakeLock = null
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreenAndShowUi() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isInteractive) {
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "GrandpaVoice:screen",
            ).acquire(10_000)
        }
        // Allowed when we are the home app in front, device owner, or already visible.
        // If Android refuses, the conversation still runs in this service.
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.grandpavoice.action.TOGGLE"
        const val ACTION_START_PENDING = "com.grandpavoice.action.START_PENDING"
        const val EXTRA_FROM_UI = "from_ui"

        private const val TAG = "CompanionService"
        private const val FG_NONE = 0
        private const val FG_MIC = 1
        private const val FG_OTHER = 2
        private const val IGNORE_SHAKE_AFTER_START_MS = 2_500L
        private const val PENDING_START_TIMEOUT_MS = 15_000L
    }
}
