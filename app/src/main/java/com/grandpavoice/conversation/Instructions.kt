package com.grandpavoice.conversation

import com.grandpavoice.settings.AppLanguage
import com.grandpavoice.settings.Defaults
import com.grandpavoice.settings.Settings
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Builds the conversation instructions, including the grammatical forms used in Bulgarian. */
object Instructions {

    private val BULGARIAN = Locale.forLanguageTag("bg-BG")

    /** Bulgarian words that change with the person's gender. */
    private class G(female: Boolean) {
        val him = if (female) "нея" else "него" // към нея / към него
        val shortHim = if (female) "я" else "го" // не я прекъсвай / не го прекъсвай
        val toHim = if (female) "ѝ" else "му" // кажи ѝ / кажи му
        val he = if (female) "тя" else "той"
        val gender = if (female) "жена" else "мъж"
        val example = if (female) "„уморена ли си“, „радвам се, че си добре“" else "„уморен ли си“, „радвам се, че си добре“"
    }

    private fun g(settings: Settings) = G(settings.userGender == Settings.GENDER_FEMALE)

    fun build(settings: Settings, now: ZonedDateTime = ZonedDateTime.now()): String {
        if (settings.language != AppLanguage.BULGARIAN) return generalInstructions(settings, now)
        val date = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'г.', HH:mm 'ч.'", BULGARIAN).format(now)
        val name = settings.userName.trim()
        val g = g(settings)
        return buildString {
            append(Defaults.effectivePrompt(settings).trim())
            append("\n\n")
            append("Говориш с възрастен ${g.gender}. Когато се обръщаш към ${g.him}, използвай ${if (g.gender == "жена") "женски" else "мъжки"} род, например ${g.example}.\n")
            if (name.isNotEmpty()) {
                append("Казва се $name. Обръщай се към ${g.him} по име от време на време, но не във всяко изречение.\n")
            }
            append("Днес е $date. Използвай това само ако е уместно, например ако те попита кой ден е.\n\n")
            append(languageRules())
            append("\n\n")
            append(speechRules(g))
            append("\n\n")
            append(endingRules(g))
        }
    }

    private fun generalInstructions(settings: Settings, now: ZonedDateTime) = buildString {
        val language = settings.language
        val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)
            .withLocale(language.locale).format(now)
        appendLine(Defaults.effectivePrompt(settings))
        appendLine()
        appendLine("Always speak in ${language.spokenName}, even when the person speaks another language.")
        val name = settings.userName.trim()
        if (name.isNotEmpty()) appendLine("Their preferred name is $name. Use it occasionally.")
        appendLine("The current date and time are $date. Use this only when relevant.")
        appendLine("All responses are spoken aloud. Avoid lists, symbols and abbreviations.")
        appendLine("Allow time for pauses and repetitions. Follow topic changes and do not correct accents or dialects.")
        appendLine("For health concerns suggest speaking with a trusted person or a clinician. In an emergency, suggest calling local emergency services.")
        appendLine("When the person clearly wants to end the conversation, say a short, warm goodbye and then call end_conversation.")
        appendLine("If they want to talk again, they can shake the phone or tap the microphone.")
    }

    fun greeting(settings: Settings): String {
        val name = settings.userName.trim()
        if (settings.language != AppLanguage.BULGARIAN) {
            val who = if (name.isEmpty()) "the person" else "the person by their preferred name, $name"
            return "Greet $who warmly in ${settings.language.spokenName} and ask how they are. Use one or two short sentences."
        }
        val g = g(settings)
        val who = if (name.isNotEmpty()) "Поздрави $name по име" else "Поздрави ${g.shortHim}"
        return "Разговорът тъкмо започва, защото ${g.he} разклати телефона, за да си поговорите. " +
            "$who топло и кратко, както би ${g.shortHim} поздравил добър приятел, и ${g.shortHim} попитай как е днес. " +
            "Само едно или две кратки изречения на български."
    }

    fun farewellUser(language: AppLanguage) = if (language == AppLanguage.BULGARIAN) FAREWELL_USER
        else "The person wants to finish. Say a short, warm goodbye in ${language.spokenName}."

    fun farewellInactivity(language: AppLanguage) = if (language == AppLanguage.BULGARIAN) FAREWELL_INACTIVITY
        else "The person has been quiet for a while. In ${language.spokenName}, briefly say they can shake the phone to talk again, then say goodbye."

    fun farewellTimeLimit(language: AppLanguage) = if (language == AppLanguage.BULGARIAN) FAREWELL_TIME_LIMIT
        else "In ${language.spokenName}, gently suggest taking a break, explain they can shake the phone to talk again, and say goodbye."

    private const val FAREWELL_USER =
        "Човекът иска да приключи разговора. Кажи едно кратко и топло изречение за довиждане на български."

    private const val FAREWELL_INACTIVITY =
        "Човекът не е казал нищо от известно време. Кажи кратко и топло на български, " +
            "че си тук винаги, когато поиска пак да си поговорите, като просто разклати телефона, и се сбогувай. " +
            "Най-много две кратки изречения."

    private const val FAREWELL_TIME_LIMIT =
        "Разговорът продължи доста дълго. Кажи мило на български, че е време за малка почивка " +
            "и че може да разклати телефона пак, когато пожелае. Сбогувай се кратко."

    private fun languageRules() = """
Правила за езика:
- Говори и отговаряй винаги и само на български език.
- Никога не преминавай на английски или на друг език, дори ако чуеш чужда дума, име или цяло изречение на друг език. Ако човекът каже нещо на друг език, отговори на български.
- Използвай естествен, топъл, разговорен български, като в разговор между близки хора, а не официален или книжовен език.
- Човекът може да има регионален говор или диалект. Разбирай го спокойно и не го поправяй.
""".trim()

    private fun speechRules(g: G) = """
Как да говориш:
- Всичко, което казваш, се чува на глас по телефона. Не използвай списъци, символи, емоджита или съкращения.
- Говори с кратки, ясни изречения. Казвай едно нещо наведнъж.
- Човекът може да говори бавно, да прави дълги паузи, да се повтаря или да започва изречения отначало. Това е напълно нормално. Не ${g.shortHim} прекъсвай и не бързай.
- Ако не си разбрал, кажи го мило и ${g.shortHim} помоли да повтори, без да ${g.shortHim} караш да се чувства неудобно.
- Ако смени темата, следвай ${g.shortHim} спокойно.
- Ако те пита за здравословен проблем или нещо спешно, отговори внимателно и ${g.shortHim} посъветвай да се обади на близките си или на лекар. При спешен случай ${g.toHim} кажи да звънне на 112.
""".trim()

    private fun endingRules(g: G) = """
Край на разговора:
- Когато човекът ясно покаже, че иска да приключи (например каже „довиждане“, „чао“, „лека нощ“, „стига толкова“ или „хайде, затварям“), кажи кратко и топло довиждане и след това извикай функцията end_conversation.
- Не извиквай end_conversation, ако не си сигурен, че ${g.he} иска да приключи.
- Никога не казвай, че трябва да се натиска бутон. За нов разговор е достатъчно да разклати телефона.
""".trim()
}
