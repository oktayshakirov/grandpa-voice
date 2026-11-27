package com.grandpavoice.conversation

import com.grandpavoice.settings.AppLanguage
import com.grandpavoice.settings.Defaults
import com.grandpavoice.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {

    @Test fun englishIsTheDefaultForNewSettings() {
        val settings = Settings(userName = "Sam")
        assertEquals(AppLanguage.ENGLISH, settings.language)
        assertTrue(Instructions.build(settings).contains("Always speak in English"))
        assertTrue(Instructions.greeting(settings).contains("Sam"))
    }

    @Test fun selectedLanguageIsUsedInEveryStage() {
        for (language in AppLanguage.entries.filterNot { it == AppLanguage.BULGARIAN }) {
            val settings = Settings(language = language, systemPrompt = Defaults.LEGACY_SYSTEM_PROMPT_FEMALE)
            assertTrue(Instructions.build(settings).contains("Always speak in ${language.spokenName}"))
            assertFalse(Instructions.build(settings).contains("Винаги говори на български"))
            assertTrue(Instructions.greeting(settings).contains(language.spokenName))
            assertTrue(Instructions.farewellUser(language).contains(language.spokenName))
            assertTrue(Instructions.farewellInactivity(language).contains(language.spokenName))
            assertTrue(Instructions.farewellTimeLimit(language).contains(language.spokenName))
        }
    }

    @Test fun womanIsAddressedInFeminine() {
        val text = Instructions.build(Settings(language = AppLanguage.BULGARIAN, userGender = Settings.GENDER_FEMALE, userName = "Мария"))
        assertTrue(text.contains("женски род"))
        assertTrue(text.contains("уморена ли си"))
        assertTrue(text.contains("Не я прекъсвай"))
        assertTrue(text.contains("към нея по име"))
        assertFalse(text.contains("неговите"))
        assertFalse(text.contains("Не го прекъсвай"))
    }

    @Test fun manIsAddressedInMasculine() {
        val s = Settings(language = AppLanguage.BULGARIAN, userGender = Settings.GENDER_MALE, systemPrompt = Defaults.LEGACY_SYSTEM_PROMPT_FEMALE)
        val text = Instructions.build(s)
        assertTrue(text.contains("You are a friendly, patient voice assistant"))
        assertTrue(text.contains("мъжки род"))
        assertTrue(text.contains("уморен ли си"))
        assertTrue(text.contains("Не го прекъсвай"))
        assertFalse(text.contains("за възрастна жена"))
    }

    @Test fun editedPromptIsKeptAsWritten() {
        val custom = "Ти си весел събеседник. Разказвай вицове."
        val s = Settings(language = AppLanguage.BULGARIAN, userGender = Settings.GENDER_FEMALE, systemPrompt = custom)
        assertEquals(custom, Defaults.effectivePrompt(s))
    }

    @Test fun greetingUsesTheRightPronoun() {
        assertTrue(Instructions.greeting(Settings(language = AppLanguage.BULGARIAN, userGender = Settings.GENDER_FEMALE)).contains("защото тя разклати"))
        assertTrue(Instructions.greeting(Settings(language = AppLanguage.BULGARIAN, userGender = Settings.GENDER_MALE)).contains("защото той разклати"))
    }
}
