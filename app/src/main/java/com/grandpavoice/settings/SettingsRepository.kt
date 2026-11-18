package com.grandpavoice.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small, synchronous settings store. Nothing about conversations is ever stored here. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("grandpa_settings", Context.MODE_PRIVATE)
    private val secrets = SecretStore(prefs)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val current: Settings get() = _flow.value

    fun save(input: Settings) {
        val s = input.copy(
            openAiKey = input.openAiKey.trim(),
            model = input.model.trim().ifBlank { Defaults.MODEL },
            backendUrl = input.backendUrl.trim(),
            deviceToken = input.deviceToken.trim(),
            userName = input.userName.trim(),
            systemPrompt = input.systemPrompt.trim().ifBlank { Defaults.SYSTEM_PROMPT },
        )
        val editor = prefs.edit()
        secrets.put(editor, K_OPENAI_KEY, s.openAiKey)
        secrets.put(editor, K_TOKEN, s.deviceToken)
        editor
            .remove(K_TOKEN) // older versions stored the device token in plain text
            .putString(K_MODE, s.connectionMode)
            .putString(K_MODEL, s.model)
            .putString(K_URL, s.backendUrl)
            .putString(K_NAME, s.userName)
            .putString(K_PROMPT, s.systemPrompt)
            .putString(K_VOICE, s.voice)
            .putFloat(K_SPEED, s.speechSpeed)
            .putString(K_VAD, s.vadMode)
            .putBoolean(K_INTERRUPT, s.allowInterrupt)
            .putInt(K_INACTIVITY, s.inactivitySeconds)
            .putInt(K_MAX_MIN, s.maxSessionMinutes)
            .putBoolean(K_MAX_VOL, s.maxVolume)
            .putFloat(K_THRESHOLD, s.shakeThresholdG)
            .putInt(K_COUNT, s.shakeCount)
            .putInt(K_WINDOW, s.shakeWindowMs)
            .putInt(K_COOLDOWN, s.cooldownMs)
            .putBoolean(K_SCREEN_OFF, s.detectWhenScreenOff)
            .putString(K_KEEP_ON, s.keepScreenOn)
            .putBoolean(K_DIM, s.dimWhenIdle)
            .apply()
        _flow.value = s
    }

    private fun load(): Settings {
        val d = Settings()
        return Settings(
            connectionMode = prefs.getString(K_MODE, null) ?: defaultMode(d),
            openAiKey = secrets.get(K_OPENAI_KEY).orEmpty(),
            model = prefs.getString(K_MODEL, null) ?: d.model,
            backendUrl = prefs.getString(K_URL, null)?.takeIf { it.isNotBlank() } ?: d.backendUrl,
            deviceToken = (secrets.get(K_TOKEN) ?: prefs.getString(K_TOKEN, null))
                ?.takeIf { it.isNotBlank() } ?: d.deviceToken,
            userName = prefs.getString(K_NAME, null) ?: d.userName,
            systemPrompt = prefs.getString(K_PROMPT, null) ?: d.systemPrompt,
            voice = prefs.getString(K_VOICE, null) ?: d.voice,
            speechSpeed = prefs.getFloat(K_SPEED, d.speechSpeed),
            vadMode = prefs.getString(K_VAD, null) ?: d.vadMode,
            allowInterrupt = prefs.getBoolean(K_INTERRUPT, d.allowInterrupt),
            inactivitySeconds = prefs.getInt(K_INACTIVITY, d.inactivitySeconds),
            maxSessionMinutes = prefs.getInt(K_MAX_MIN, d.maxSessionMinutes),
            maxVolume = prefs.getBoolean(K_MAX_VOL, d.maxVolume),
            shakeThresholdG = prefs.getFloat(K_THRESHOLD, d.shakeThresholdG),
            shakeCount = prefs.getInt(K_COUNT, d.shakeCount),
            shakeWindowMs = prefs.getInt(K_WINDOW, d.shakeWindowMs),
            cooldownMs = prefs.getInt(K_COOLDOWN, d.cooldownMs),
            detectWhenScreenOff = prefs.getBoolean(K_SCREEN_OFF, d.detectWhenScreenOff),
            keepScreenOn = prefs.getString(K_KEEP_ON, null) ?: d.keepScreenOn,
            dimWhenIdle = prefs.getBoolean(K_DIM, d.dimWhenIdle),
        )
    }

    /** A build with a baked-in server (gradle.properties) starts in server mode. */
    private fun defaultMode(d: Settings) =
        if (d.backendUrl.isNotBlank() && d.deviceToken.isNotBlank()) Settings.MODE_SERVER else d.connectionMode

    private companion object {
        const val K_MODE = "connectionMode"
        const val K_OPENAI_KEY = "openAiKey"
        const val K_MODEL = "model"
        const val K_URL = "backendUrl"
        const val K_TOKEN = "deviceToken"
        const val K_NAME = "userName"
        const val K_PROMPT = "systemPrompt"
        const val K_VOICE = "voice"
        const val K_SPEED = "speechSpeed"
        const val K_VAD = "vadMode"
        const val K_INTERRUPT = "allowInterrupt"
        const val K_INACTIVITY = "inactivitySeconds"
        const val K_MAX_MIN = "maxSessionMinutes"
        const val K_MAX_VOL = "maxVolume"
        const val K_THRESHOLD = "shakeThresholdG"
        const val K_COUNT = "shakeCount"
        const val K_WINDOW = "shakeWindowMs"
        const val K_COOLDOWN = "cooldownMs"
        const val K_SCREEN_OFF = "detectWhenScreenOff"
        const val K_KEEP_ON = "keepScreenOn"
        const val K_DIM = "dimWhenIdle"
    }
}
