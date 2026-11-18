package com.grandpavoice.conversation

import com.grandpavoice.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionConfigTest {

    @Test fun defaultSessionIsPatientBulgarianAudio() {
        val s = SessionConfig.build(Settings(), "инструкции")
        assertEquals("realtime", s.getString("type"))
        assertEquals("gpt-realtime-1.5", s.getString("model"))
        assertEquals("audio", s.getJSONArray("output_modalities").getString(0))
        val td = s.getJSONObject("audio").getJSONObject("input").getJSONObject("turn_detection")
        assertEquals("semantic_vad", td.getString("type"))
        assertEquals("low", td.getString("eagerness"))
        assertEquals("end_conversation", s.getJSONArray("tools").getJSONObject(0).getString("name"))
    }

    @Test fun unknownVoiceFallsBackAndSpeedIsClamped() {
        val s = SessionConfig.build(Settings(voice = "nope", speechSpeed = 3f), "x")
        val out = s.getJSONObject("audio").getJSONObject("output")
        assertEquals("marin", out.getString("voice"))
        assertEquals(1.2, out.getDouble("speed"), 0.001)
    }

    @Test fun interruptionsCanBeTurnedOff() {
        val td = SessionConfig.turnDetection(Settings.VAD_SERVER_PATIENT, allowInterrupt = false)
        assertEquals("server_vad", td.getString("type"))
        assertFalse(td.getBoolean("interrupt_response"))
    }

    @Test fun clientSecretKeyLivesOneMinute() {
        val body = SessionConfig.clientSecretRequest(Settings(), "x")
        assertEquals(60, body.getJSONObject("expires_after").getInt("seconds"))
    }
}
