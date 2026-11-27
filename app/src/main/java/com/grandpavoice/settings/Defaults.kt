package com.grandpavoice.settings

object Defaults {
    // Recognize saved defaults from older installations so they can use the current prompt.
    internal val LEGACY_SYSTEM_PROMPT_FEMALE = """
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

    private val LEGACY_SYSTEM_PROMPT_MALE = """
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
You are a friendly, patient voice assistant. Help with questions, ideas, speaking practice, or casual conversation, depending on what the person wants.
Ask natural follow-up questions when helpful.
Use short, clear sentences. Give the person time to think and speak, and gently ask them to repeat anything you miss.
Stay with the person's topic and speak in a friendly, everyday tone.
""".trim()

    /** True if the prompt matches a current or previously shipped default. */
    fun isDefaultPrompt(prompt: String): Boolean {
        val p = prompt.trim()
        return p.isEmpty() || p == SYSTEM_PROMPT_GENERAL ||
            p == LEGACY_SYSTEM_PROMPT_FEMALE || p == LEGACY_SYSTEM_PROMPT_MALE ||
            (p.startsWith(SYSTEM_PROMPT_LEGACY_START) && p.lines().size == 11)
    }

    fun effectivePrompt(settings: Settings): String =
        if (isDefaultPrompt(settings.systemPrompt)) SYSTEM_PROMPT_GENERAL else settings.systemPrompt

    /**
     * Realtime models offered in settings. gpt-realtime-1.5 is OpenAI's audio-in/audio-out voice model;
     * the 2.1 mini is a cheaper option. Any other model id can be typed in settings.
     */
    const val MODEL = "gpt-realtime-1.5"
    const val MODEL_ECONOMY = "gpt-realtime-2.1-mini"

    /** Voices accepted by the Realtime API (the backend enforces the same list). marin and cedar sound best. */
    val VOICES = listOf("marin", "cedar", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "alloy")
}
