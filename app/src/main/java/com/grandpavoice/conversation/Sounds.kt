package com.grandpavoice.conversation

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Local feedback that works even without internet:
 * a short beep + buzz when a shake is recognised, and Bulgarian text-to-speech
 * (the phone's own engine) to say error messages aloud.
 */
class Sounds(context: Context) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    @Volatile private var ttsReady = false
    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }.getOrNull()
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Vibrator::class.java)
        }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale("bg", "BG"))
        ttsReady = result >= TextToSpeech.LANG_AVAILABLE
        tts.setSpeechRate(0.9f)
    }

    /** True if a Bulgarian voice is installed for the phone's own text-to-speech. */
    val hasBulgarianTts: Boolean get() = ttsReady

    fun shakeRecognised() {
        vibrator?.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        tone?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
    }

    fun conversationEnded() {
        tone?.startTone(ToneGenerator.TONE_PROP_PROMPT, 250)
    }

    fun speak(text: String) {
        if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "grandpa-msg")
    }

    fun release() {
        runCatching { tts.shutdown() }
        runCatching { tone?.release() }
    }
}
