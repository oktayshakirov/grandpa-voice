package com.grandpavoice.conversation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

/**
 * Puts the phone into "phone call" audio mode (which also enables the platform echo canceller),
 * routes to a Bluetooth/wired headset if one is connected, otherwise to the loudspeaker.
 */
class AudioRouter(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null
    private var savedVolume: Int? = null
    private var usedLegacySco = false
    private var active = false

    fun start(maxVolume: Boolean) {
        if (active) return
        active = true

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { am.requestAudioFocus(it) }

        am.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = am.availableCommunicationDevices
            val preferredTypes = listOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
            )
            val headset = preferredTypes.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
            val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            (headset ?: speaker)?.let { am.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION")
            run {
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val wired = outputs.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                        it.type == AudioDeviceInfo.TYPE_USB_HEADSET
                }
                val bluetooth = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                when {
                    bluetooth -> {
                        am.startBluetoothSco()
                        am.isBluetoothScoOn = true
                        usedLegacySco = true
                    }
                    wired -> am.isSpeakerphoneOn = false
                    else -> am.isSpeakerphoneOn = true
                }
            }
        }

        if (maxVolume) {
            val stream = AudioManager.STREAM_VOICE_CALL
            savedVolume = am.getStreamVolume(stream)
            runCatching { am.setStreamVolume(stream, am.getStreamMaxVolume(stream), 0) }
        }
    }

    fun stop() {
        if (!active) return
        active = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            run {
                if (usedLegacySco) {
                    am.isBluetoothScoOn = false
                    am.stopBluetoothSco()
                    usedLegacySco = false
                }
                am.isSpeakerphoneOn = false
            }
        }
        savedVolume?.let { runCatching { am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, it, 0) } }
        savedVolume = null
        am.mode = AudioManager.MODE_NORMAL
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }
}
