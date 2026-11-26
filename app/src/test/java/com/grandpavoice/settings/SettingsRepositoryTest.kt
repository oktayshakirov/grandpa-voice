package com.grandpavoice.settings

import android.content.ContextWrapper
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRepositoryTest {
    @Test
    fun freshInstallStartsOnboardingInEnglish() {
        val settings = load()
        assertEquals(AppLanguage.ENGLISH, settings.language)
        assertFalse(settings.languageChosen)
    }

    @Test
    fun upgradeWithoutLanguagePreferenceDefaultsToEnglishAndRetainsSetup() {
        val settings = load(
            "connectionMode" to Settings.MODE_SERVER,
            "backendUrl" to "https://example.com",
            "userName" to "Alex",
        )
        assertEquals(AppLanguage.ENGLISH, settings.language)
        assertTrue(settings.languageChosen)
        assertEquals(Settings.MODE_SERVER, settings.connectionMode)
        assertEquals("https://example.com", settings.backendUrl)
        assertEquals("Alex", settings.userName)
    }

    @Test
    fun upgradeRetainsSavedBulgarianPreference() {
        val settings = load(
            "connectionMode" to Settings.MODE_OPENAI_KEY,
            "language" to "bg",
            "languageChosen" to true,
        )
        assertEquals(AppLanguage.BULGARIAN, settings.language)
        assertTrue(settings.languageChosen)
    }

    private fun load(vararg preferences: Pair<String, Any>): Settings {
        val values = mapOf(*preferences)
        val prefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            val key = args[0] as String
            when (method.name) {
                "contains" -> values.containsKey(key)
                "getString", "getBoolean", "getInt", "getFloat" -> values[key] ?: args[1]
                else -> error("Unexpected preferences call: ${method.name}")
            }
        } as SharedPreferences
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int) = prefs
        }
        return SettingsRepository(context).current
    }
}
