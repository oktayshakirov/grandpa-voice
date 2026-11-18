package com.grandpavoice.conversation

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One WebRTC call to the OpenAI Realtime API.
 *
 * Audio: microphone -> WebRTC (with echo cancellation, noise suppression, gain control) -> OpenAI,
 *        and the model's voice comes back as a remote audio track that WebRTC plays automatically.
 * Events: JSON over the "oai-events" data channel.
 *
 * Every WebRTC object is touched only on a single dedicated thread to avoid native races.
 * Create a new instance per conversation; [close] releases the microphone completely.
 */
class RealtimeClient(
    private val context: Context,
    private val http: OkHttpClient,
    private val listener: Listener,
) {
    interface Listener {
        fun onChannelOpen()
        fun onServerEvent(event: JSONObject)
        fun onConnectionLost(reason: String)
        fun onMicrophoneError(reason: String)
    }

    @Volatile private var closed = false
    private var adm: AudioDeviceModule? = null
    private var factory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var peer: PeerConnection? = null
    @Volatile private var channel: DataChannel? = null
    private val gatheringDone = CompletableDeferred<Unit>()

    suspend fun connect(ticket: RealtimeTicket) = withContext(RTC_DISPATCHER) {
        ensureInitialized(context.applicationContext)
        checkOpen()

        val deviceModule = JavaAudioDeviceModule.builder(context.applicationContext)
            .setUseHardwareAcousticEchoCanceler(JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported())
            .setUseHardwareNoiseSuppressor(JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported())
            .setAudioRecordErrorCallback(recordErrors)
            .createAudioDeviceModule()
        adm = deviceModule

        val pcf = PeerConnectionFactory.builder()
            .setAudioDeviceModule(deviceModule)
            .createPeerConnectionFactory()
        factory = pcf

        val micConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val source = pcf.createAudioSource(micConstraints)
        audioSource = source
        val track = pcf.createAudioTrack("grandpa-mic", source)
        track.setEnabled(true)
        audioTrack = track

        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = pcf.createPeerConnection(config, peerObserver)
            ?: throw IOException("Could not create PeerConnection")
        peer = pc
        pc.addTrack(track, listOf("grandpa-stream"))

        val dc = pc.createDataChannel("oai-events", DataChannel.Init())
        channel = dc
        dc.registerObserver(channelObserver)

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        val offer = pc.createOfferAsync(offerConstraints)
        checkOpen()
        pc.setLocalAsync(offer)
        checkOpen()

        // OpenAI does not accept trickled ICE candidates, so give host-candidate gathering a moment.
        withTimeoutOrNull(ICE_GATHER_TIMEOUT_MS) { gatheringDone.await() }
        checkOpen()

        val localSdp = pc.localDescription?.description ?: offer.description
        val answerSdp = withContext(Dispatchers.IO) { exchangeSdp(ticket, localSdp) }
        checkOpen()
        pc.setRemoteAsync(SessionDescription(SessionDescription.Type.ANSWER, answerSdp))
        checkOpen()
    }

    /** Sends a client event over the data channel. Safe to call from any thread. */
    fun send(event: JSONObject) {
        val bytes = event.toString().toByteArray(Charsets.UTF_8)
        RTC_EXECUTOR.execute {
            val dc = channel
            if (!closed && dc != null && dc.state() == DataChannel.State.OPEN) {
                dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
            }
        }
    }

    /** Tears everything down and releases the microphone. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        RTC_EXECUTOR.execute {
            channel?.let { dc ->
                runCatching { dc.unregisterObserver() }
                runCatching { dc.close() }
                runCatching { dc.dispose() }
            }
            channel = null
            runCatching { peer?.dispose() }
            peer = null
            audioTrack = null // owned and released by the PeerConnection's sender
            runCatching { audioSource?.dispose() }
            audioSource = null
            runCatching { factory?.dispose() }
            factory = null
            runCatching { adm?.release() }
            adm = null
        }
    }

    private fun checkOpen() {
        if (closed) throw CancellationException("Realtime client closed")
    }

    private fun exchangeSdp(ticket: RealtimeTicket, offerSdp: String): String {
        val request = Request.Builder()
            .url(ticket.callsUrl)
            .header("Authorization", "Bearer ${ticket.ephemeralKey}")
            .post(offerSdp.toByteArray(Charsets.UTF_8).toRequestBody(SDP_MEDIA_TYPE))
            .build()
        return http.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            // e.g. an account without credit is only refused here, when the call actually starts.
            if (!r.isSuccessful) throw KeyProvider.errorFor(r, text)
            if (text.isBlank()) throw IOException("Empty SDP answer")
            text
        }
    }

    private fun lost(reason: String) {
        if (!closed) listener.onConnectionLost(reason)
    }

    private val peerObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED) lost("ice_failed")
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            if (state == PeerConnection.IceGatheringState.COMPLETE) gatheringDone.complete(Unit)
        }
        override fun onIceCandidate(candidate: IceCandidate?) = Unit
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
        override fun onAddStream(stream: MediaStream?) = Unit
        override fun onRemoveStream(stream: MediaStream?) = Unit
        override fun onDataChannel(dataChannel: DataChannel?) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            if (newState == PeerConnection.PeerConnectionState.FAILED) lost("peer_failed")
        }
    }

    private val channelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit

        override fun onStateChange() {
            when (channel?.state()) {
                DataChannel.State.OPEN -> if (!closed) listener.onChannelOpen()
                DataChannel.State.CLOSED -> lost("data_channel_closed")
                else -> Unit
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (closed) return
            val data = buffer.data
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            val json = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrNull() ?: return
            listener.onServerEvent(json)
        }
    }

    private val recordErrors = object : JavaAudioDeviceModule.AudioRecordErrorCallback {
        override fun onWebRtcAudioRecordInitError(errorMessage: String?) = micError(errorMessage)
        override fun onWebRtcAudioRecordStartError(
            errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
            errorMessage: String?,
        ) = micError(errorMessage)
        override fun onWebRtcAudioRecordError(errorMessage: String?) = micError(errorMessage)
    }

    private fun micError(message: String?) {
        if (!closed) listener.onMicrophoneError(message ?: "microphone error")
    }

    private suspend fun PeerConnection.createOfferAsync(constraints: MediaConstraints): SessionDescription =
        suspendCancellableCoroutine { cont ->
            createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp) else cont.resumeWithException(IOException("Empty offer"))
                }
                override fun onCreateFailure(error: String?) =
                    cont.resumeWithException(IOException("createOffer failed: $error"))
                override fun onSetSuccess() = Unit
                override fun onSetFailure(error: String?) = Unit
            }, constraints)
        }

    private suspend fun PeerConnection.setLocalAsync(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(IOException("setLocalDescription failed: $error"))
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            }, sdp)
        }

    private suspend fun PeerConnection.setRemoteAsync(sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() = cont.resume(Unit)
                override fun onSetFailure(error: String?) =
                    cont.resumeWithException(IOException("setRemoteDescription failed: $error"))
                override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            }, sdp)
        }

    companion object {
        private val SDP_MEDIA_TYPE = "application/sdp".toMediaType()
        private const val ICE_GATHER_TIMEOUT_MS = 2000L

        private val RTC_EXECUTOR = Executors.newSingleThreadExecutor { r -> Thread(r, "realtime-rtc") }
        private val RTC_DISPATCHER = RTC_EXECUTOR.asCoroutineDispatcher()

        @Volatile private var initialized = false

        private fun ensureInitialized(appContext: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .createInitializationOptions(),
                )
                initialized = true
            }
        }
    }
}
