package com.grandpavoice.conversation

import com.grandpavoice.settings.AppLanguage
import com.grandpavoice.settings.Defaults
import com.grandpavoice.settings.Settings
import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenAI Realtime session the phone asks for. backend/server.js builds the same thing
 * when the phone uses your own server instead of a key on the phone.
 */
object SessionConfig {

    const val VOICE_FALLBACK = "marin"

    fun build(settings: Settings, instructions: String): JSONObject {
        val voice = settings.voice.takeIf { it in Defaults.VOICES } ?: VOICE_FALLBACK
        val speed = settings.speechSpeed.coerceIn(0.7f, 1.2f).toDouble()
        return JSONObject()
            .put("type", "realtime")
            .put("model", settings.model.ifBlank { Defaults.MODEL })
            .put("instructions", instructions)
            .put("output_modalities", JSONArray().put("audio"))
            .put(
                "audio",
                JSONObject()
                    .put(
                        "input",
                        JSONObject()
                            // The phone lies on a table or is held at arm's length, not at the ear.
                            .put("noise_reduction", JSONObject().put("type", "far_field"))
                            .put("turn_detection", turnDetection(settings.vadMode, settings.allowInterrupt)),
                    )
                    .put("output", JSONObject().put("voice", voice).put("speed", speed)),
            )
            .put("tools", JSONArray().put(endTool(settings.language)))
            .put("tool_choice", "auto")
    }

    /** Request body for POST /v1/realtime/client_secrets. */
    fun clientSecretRequest(settings: Settings, instructions: String, ttlSeconds: Int = 60): JSONObject =
        JSONObject()
            .put("expires_after", JSONObject().put("anchor", "created_at").put("seconds", ttlSeconds))
            .put("session", build(settings, instructions))

    fun turnDetection(mode: String, allowInterrupt: Boolean): JSONObject = when (mode) {
        Settings.VAD_SEMANTIC_MEDIUM -> JSONObject()
            .put("type", "semantic_vad")
            .put("eagerness", "medium")
            .put("create_response", true)
            .put("interrupt_response", allowInterrupt)
        Settings.VAD_SERVER_PATIENT -> JSONObject()
            .put("type", "server_vad")
            .put("threshold", 0.6)
            .put("prefix_padding_ms", 400)
            .put("silence_duration_ms", 1200)
            .put("create_response", true)
            .put("interrupt_response", allowInterrupt)
        // "low" waits longest before deciding he has finished: best for slow speech and long pauses.
        else -> JSONObject()
            .put("type", "semantic_vad")
            .put("eagerness", "low")
            .put("create_response", true)
            .put("interrupt_response", allowInterrupt)
    }

    private fun endTool(language: AppLanguage) = JSONObject()
        .put("type", "function")
        .put("name", "end_conversation")
        .put(
            "description",
            if (language == AppLanguage.BULGARIAN) {
                "Приключва разговора. Извикай я само когато човекът ясно иска да приключи разговора " +
                    "и вече си му казал довиждане."
            } else {
                "End the conversation after saying goodbye when the person clearly wants to finish."
            },
        )
        .put(
            "parameters",
            JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
                .put("additionalProperties", false),
        )
}
