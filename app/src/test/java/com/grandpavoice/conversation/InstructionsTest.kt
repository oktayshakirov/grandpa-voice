package com.grandpavoice.conversation

import com.grandpavoice.settings.Defaults
import com.grandpavoice.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstructionsTest {

    @Test fun womanIsAddressedInFeminine() {
        val text = Instructions.build(Settings(userGender = Settings.GENDER_FEMALE, userName = "Мария"))
        assertTrue(text.contains("възрастна жена"))
        assertTrue(text.contains("женски род"))
        assertTrue(text.contains("уморена ли си"))
        assertTrue(text.contains("Не я прекъсвай"))
        assertTrue(text.contains("към нея по име"))
        assertFalse(text.contains("неговите"))
        assertFalse(text.contains("Не го прекъсвай"))
    }

    @Test fun manIsAddressedInMasculine() {
        val s = Settings(userGender = Settings.GENDER_MALE, systemPrompt = Defaults.SYSTEM_PROMPT_FEMALE)
        val text = Instructions.build(s)
        // An unedited default prompt follows the gender even if the other default is saved.
        assertTrue(text.contains("възрастен мъж"))
        assertTrue(text.contains("уморен ли си"))
        assertTrue(text.contains("Не го прекъсвай"))
        assertFalse(text.contains("нейните"))
    }

    @Test fun editedPromptIsKeptAsWritten() {
        val custom = "Ти си весел събеседник. Разказвай вицове."
        val s = Settings(userGender = Settings.GENDER_FEMALE, systemPrompt = custom)
        assertEquals(custom, Defaults.effectivePrompt(s))
    }

    @Test fun greetingUsesTheRightPronoun() {
        assertTrue(Instructions.greeting(Settings(userGender = Settings.GENDER_FEMALE)).contains("защото тя разклати"))
        assertTrue(Instructions.greeting(Settings(userGender = Settings.GENDER_MALE)).contains("защото той разклати"))
    }
}
