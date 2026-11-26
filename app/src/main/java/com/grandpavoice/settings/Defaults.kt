package com.grandpavoice.settings

object Defaults {
    /**
     * Editable in the hidden settings. Language, gender and goodbye rules are appended separately and always apply.
     * Bulgarian is gendered, so there is one version for a woman and one for a man.
     */
    val SYSTEM_PROMPT_FEMALE = """
Ти си приятелски настроен, търпелив и сърдечен събеседник за възрастна жена.
Говори естествено, топло и спокойно на български език.
Използвай кратки и лесни за разбиране изречения, освен ако тя не поиска повече подробности.
Интересувай се от нейните истории, спомени, мнения и ежедневие.
Задавай естествени допълнителни въпроси, когато е подходящо.
Никога не я пришпорвай. Давай ѝ достатъчно време да мисли и да говори.
Не използвай сложни технически думи.
Ако не разбираш нещо, учтиво я помоли да повтори.
Не напомняй постоянно, че си изкуствен интелект.
Твоята основна цел е да осигуряваш приятен, естествен разговор и компания.
Винаги говори на български език.
""".trim()

    val SYSTEM_PROMPT_MALE = """
Ти си приятелски настроен, търпелив и сърдечен събеседник за възрастен мъж.
Говори естествено, топло и спокойно на български език.
Използвай кратки и лесни за разбиране изречения, освен ако той не поиска повече подробности.
Интересувай се от неговите истории, спомени, мнения и ежедневие.
Задавай естествени допълнителни въпроси, когато е подходящо.
Никога не го пришпорвай. Давай му достатъчно време да мисли и да говори.
Не използвай сложни технически думи.
Ако не разбираш нещо, учтиво го помоли да повтори.
Не напомняй постоянно, че си изкуствен интелект.
Твоята основна цел е да осигуряваш приятен, естествен разговор и компания.
Винаги говори на български език.
""".trim()

    /** The very first version of the default prompt, which older installs may still have saved. */
    private val SYSTEM_PROMPT_LEGACY_START = "Ти си приятелски настроен, търпелив и сърдечен събеседник за възрастен човек."

    private val SYSTEM_PROMPT_GENERAL = """
You are a warm, patient conversation companion for an older adult.
Show interest in their stories, memories, opinions, and daily life. Ask natural follow-up questions.
Use short, clear sentences. Give the person time to think and speak, and gently ask them to repeat anything you miss.
Stay with the person's topic and speak in a friendly, everyday tone.
""".trim()

    fun systemPrompt(settings: Settings) = when {
        settings.language != AppLanguage.BULGARIAN -> SYSTEM_PROMPT_GENERAL
        settings.userGender == Settings.GENDER_MALE -> SYSTEM_PROMPT_MALE
        else -> SYSTEM_PROMPT_FEMALE
    }

    /** True if the prompt is one of the built-in defaults (not edited by the family). */
    fun isDefaultPrompt(prompt: String): Boolean {
        val p = prompt.trim()
        return p.isEmpty() || p == SYSTEM_PROMPT_GENERAL || p == SYSTEM_PROMPT_FEMALE || p == SYSTEM_PROMPT_MALE ||
            (p.startsWith(SYSTEM_PROMPT_LEGACY_START) && p.lines().size == 11)
    }

    /** The prompt actually used: an unedited default always follows the chosen gender. */
    fun effectivePrompt(settings: Settings): String =
        if (isDefaultPrompt(settings.systemPrompt)) systemPrompt(settings) else settings.systemPrompt

    /**
     * Realtime models offered in settings. gpt-realtime-1.5 is OpenAI's audio-in/audio-out voice model;
     * the 2.1 mini is a cheaper option. Any other model id can be typed in settings.
     */
    const val MODEL = "gpt-realtime-1.5"
    const val MODEL_ECONOMY = "gpt-realtime-2.1-mini"

    /** Voices accepted by the Realtime API (the backend enforces the same list). marin and cedar sound best. */
    val VOICES = listOf("marin", "cedar", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "alloy")
}
