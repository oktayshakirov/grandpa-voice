package com.grandpavoice.conversation

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyErrorTest {
    private fun response(code: Int) = Response.Builder()
        .request(Request.Builder().url("https://api.openai.com/v1/realtime/calls").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("x").build()

    @Test fun openAiOutOfCreditIsNoCredit() {
        // Real response from OpenAI for an account with no credit (2026-11).
        val body = """{"error":{"message":"You have no credits remaining.","type":"insufficient_quota","param":null,"code":"credit_balance_exhausted"}}"""
        assertEquals(KeyException.Reason.NO_CREDIT, KeyProvider.errorFor(response(429), body).reason)
    }

    @Test fun olderQuotaCodeIsNoCredit() {
        val body = """{"error":{"type":"insufficient_quota","code":"insufficient_quota"}}"""
        assertEquals(KeyException.Reason.NO_CREDIT, KeyProvider.errorFor(response(429), body).reason)
    }

    @Test fun brokerNoCreditIsNoCredit() {
        assertEquals(KeyException.Reason.NO_CREDIT, KeyProvider.errorFor(response(402), """{"error":"no_credit"}""").reason)
    }

    @Test fun plainRateLimitStaysRateLimited() {
        val body = """{"error":{"type":"requests","code":"rate_limit_exceeded"}}"""
        assertEquals(KeyException.Reason.RATE_LIMITED, KeyProvider.errorFor(response(429), body).reason)
    }

    @Test fun badKeyIsUnauthorized() {
        val body = """{"error":{"type":"invalid_request_error","code":"invalid_api_key"}}"""
        assertEquals(KeyException.Reason.UNAUTHORIZED, KeyProvider.errorFor(response(401), body).reason)
    }
}
