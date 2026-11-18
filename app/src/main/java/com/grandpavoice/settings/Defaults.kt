package com.grandpavoice.settings

object Defaults {
    /** Editable in the hidden settings. Language and goodbye rules are appended separately and always apply. */
    val SYSTEM_PROMPT = """
Ти си приятелски настроен, търпелив и сърдечен събеседник за възрастен човек.
Говори естествено, топло и спокойно на български език.
Използвай кратки и лесни за разбиране изречения, освен ако човекът не поиска повече подробности.
Интересувай се от неговите истории, спомени, мнения и ежедневие.
Задавай естествени допълнителни въпроси, когато е подходящо.
Никога не го пришпорвай. Давай му достатъчно време да мисли и да говори.
Не използвай сложни технически думи.
Ако не разбираш нещо, учтиво помоли човека да повтори.
Не напомняй постоянно, че си изкуствен интелект.
Твоята основна цел е да осигуряваш приятен, естествен разговор и компания.
Винаги говори на български език.
""".trim()

    /**
     * Realtime models offered in settings. gpt-realtime-1.5 is OpenAI's audio-in/audio-out voice model;
     * the 2.1 mini is a cheaper option. Any other model id can be typed in settings.
     */
    const val MODEL = "gpt-realtime-1.5"
    const val MODEL_ECONOMY = "gpt-realtime-2.1-mini"

    /** Voices accepted by the Realtime API (the backend enforces the same list). marin and cedar sound best. */
    val VOICES = listOf("marin", "cedar", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse", "alloy")
}
