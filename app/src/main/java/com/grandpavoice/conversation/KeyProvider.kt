package com.grandpavoice.conversation

import com.grandpavoice.settings.Settings
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class KeyException(val reason: Reason, message: String) : Exception(message) {
    enum class Reason { NOT_CONFIGURED, UNAUTHORIZED, NO_CREDIT, RATE_LIMITED, SERVER, NETWORK }
}

/** A short-lived key (ek_...) and where to open the WebRTC call with it. */
data class RealtimeTicket(val ephemeralKey: String, val callsUrl: String)

/**
 * Gets a short-lived OpenAI Realtime key, either
 *  - straight from OpenAI with the key stored (encrypted) on this phone, or
 *  - from your own token broker (backend/), so the permanent key never touches the phone.
 * Either way the WebRTC call itself only ever sees a key that expires after a minute.
 */
class KeyProvider(private val http: OkHttpClient) {

    /** Blocking; call from Dispatchers.IO. */
    fun fetch(settings: Settings, instructions: String): RealtimeTicket =
        if (settings.connectionMode == Settings.MODE_SERVER) fromServer(settings, instructions)
        else fromOpenAi(settings, instructions)

    private fun fromOpenAi(settings: Settings, instructions: String): RealtimeTicket {
        val apiKey = settings.openAiKey.trim()
        if (apiKey.isBlank()) throw KeyException(KeyException.Reason.NOT_CONFIGURED, "OpenAI key missing")
        val request = Request.Builder()
            .url(OPENAI_CLIENT_SECRETS_URL)
            .header("Authorization", "Bearer $apiKey")
            .post(SessionConfig.clientSecretRequest(settings, instructions).toBody())
            .build()
        return execute(request) { json ->
            RealtimeTicket(json.optString("value"), OPENAI_CALLS_URL)
        }
    }

    private fun fromServer(settings: Settings, instructions: String): RealtimeTicket {
        if (settings.backendUrl.isBlank() || settings.deviceToken.isBlank()) {
            throw KeyException(KeyException.Reason.NOT_CONFIGURED, "Backend URL or device token missing")
        }
        val url = try {
            (settings.backendUrl.trim().trimEnd('/') + "/session").toHttpUrl()
        } catch (e: IllegalArgumentException) {
            throw KeyException(KeyException.Reason.NOT_CONFIGURED, "Invalid backend URL")
        }
        val body = JSONObject()
            .put("instructions", instructions)
            .put("model", settings.model)
            .put("voice", settings.voice)
            .put("speed", settings.speechSpeed.toDouble())
            .put("vad", settings.vadMode)
            .put("allowInterrupt", settings.allowInterrupt)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${settings.deviceToken.trim()}")
            .post(body.toBody())
            .build()
        return execute(request) { json ->
            // A broker may point the call somewhere else (used by the local test server in tools/).
            val calls = json.optString("calls_url").takeIf { it.toHttpUrlOrNull() != null } ?: OPENAI_CALLS_URL
            RealtimeTicket(json.optString("value"), calls)
        }
    }

    private fun execute(request: Request, parse: (JSONObject) -> RealtimeTicket): RealtimeTicket {
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw KeyException(KeyException.Reason.NETWORK, e.message ?: "network error")
        }
        return response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw errorFor(r, text)
            val ticket = runCatching { parse(JSONObject(text)) }.getOrNull()
            if (ticket == null || ticket.ephemeralKey.isBlank()) {
                throw KeyException(KeyException.Reason.SERVER, "No key in response")
            }
            ticket
        }
    }

    companion object {
        const val OPENAI_CLIENT_SECRETS_URL = "https://api.openai.com/v1/realtime/client_secrets"
        const val OPENAI_CALLS_URL = "https://api.openai.com/v1/realtime/calls"
        private val JSON = "application/json".toMediaType()

        private fun JSONObject.toBody() = toString().toByteArray().toRequestBody(JSON)

        /** Maps an HTTP error from OpenAI or the broker to something we can say to the user. */
        fun errorFor(r: Response, body: String): KeyException {
            val json = runCatching { JSONObject(body) }.getOrNull()
            val error = json?.optJSONObject("error")
            // OpenAI: {"error":{"type":"insufficient_quota","code":"credit_balance_exhausted"|"insufficient_quota"}}
            // Our broker: {"error":"no_credit"}
            val code = error?.optString("code") ?: json?.optString("error").orEmpty()
            val type = error?.optString("type").orEmpty()
            val message = "HTTP ${r.code} $code ${error?.optString("message").orEmpty()}".trim()
            val noCredit = r.code == 402 || type == "insufficient_quota" ||
                code in setOf("insufficient_quota", "credit_balance_exhausted", "no_credit")
            val reason = when {
                noCredit -> KeyException.Reason.NO_CREDIT
                r.code == 401 || r.code == 403 -> KeyException.Reason.UNAUTHORIZED
                r.code == 429 -> KeyException.Reason.RATE_LIMITED
                else -> KeyException.Reason.SERVER
            }
            return KeyException(reason, message)
        }
    }
}
