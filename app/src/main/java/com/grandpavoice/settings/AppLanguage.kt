package com.grandpavoice.settings

import java.util.Locale

enum class AppLanguage(val tag: String, val label: String, val spokenName: String) {
    ENGLISH("en", "English", "English"),
    BULGARIAN("bg", "Български", "Bulgarian"),
    SPANISH("es", "Español", "Spanish"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    PORTUGUESE("pt", "Português", "Portuguese");

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?) = entries.firstOrNull { it.tag == tag } ?: ENGLISH
    }
}
