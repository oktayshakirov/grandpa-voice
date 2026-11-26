package com.grandpavoice.settings

import com.grandpavoice.BuildConfig

data class Settings(
    // How the phone gets a short-lived OpenAI key.
    val connectionMode: String = MODE_OPENAI_KEY,
    /** Permanent OpenAI key, used only in [MODE_OPENAI_KEY]. Stored encrypted with the Android Keystore. */
    val openAiKey: String = "",
    /** Your own token broker (see backend/), used only in [MODE_SERVER]. */
    val backendUrl: String = BuildConfig.DEFAULT_BACKEND_URL,
    val deviceToken: String = BuildConfig.DEFAULT_DEVICE_TOKEN,

    // Conversation
    val language: AppLanguage = AppLanguage.ENGLISH,
    val languageChosen: Boolean = false,
    val userName: String = "",
    /** Bulgarian is gendered: the model must say "уморена" to a woman and "уморен" to a man. */
    val userGender: String = GENDER_FEMALE,
    val systemPrompt: String = "",
    val model: String = Defaults.MODEL,
    val voice: String = "marin",
    val speechSpeed: Float = 0.95f,
    val vadMode: String = VAD_SEMANTIC_LOW,
    val allowInterrupt: Boolean = true,
    val inactivitySeconds: Int = 120,
    val maxSessionMinutes: Int = 30,
    val maxVolume: Boolean = true,

    // Shake detection
    val shakeThresholdG: Float = 2.2f,
    val shakeCount: Int = 4,
    val shakeWindowMs: Int = 1500,
    val cooldownMs: Int = 4000,
    val detectWhenScreenOff: Boolean = true,

    // Screen
    val keepScreenOn: String = SCREEN_ON_CHARGING,
    val dimWhenIdle: Boolean = true,
) {
    /** True once the family member has entered what the phone needs to reach OpenAI. */
    val isConfigured: Boolean
        get() = when (connectionMode) {
            MODE_SERVER -> backendUrl.isNotBlank() && deviceToken.isNotBlank()
            else -> openAiKey.isNotBlank()
        }

    companion object {
        const val GENDER_FEMALE = "female"
        const val GENDER_MALE = "male"

        const val MODE_OPENAI_KEY = "openai_key"
        const val MODE_SERVER = "server"

        const val VAD_SEMANTIC_LOW = "semantic_low"
        const val VAD_SEMANTIC_MEDIUM = "semantic_medium"
        const val VAD_SERVER_PATIENT = "server_patient"

        const val SCREEN_ON_CHARGING = "charging"
        const val SCREEN_ON_ALWAYS = "always"
        const val SCREEN_ON_NEVER = "never"
    }
}
