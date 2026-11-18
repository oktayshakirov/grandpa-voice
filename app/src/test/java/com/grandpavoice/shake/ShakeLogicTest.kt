package com.grandpavoice.shake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeLogicTest {

    /** Feeds a 50 Hz stream: resting 1 g, with a spike of [peakG] at each of [spikeTimesMs]. */
    private fun run(logic: ShakeLogic, spikeTimesMs: List<Long>, peakG: Float = 3f, untilMs: Long? = null): Int {
        val end = untilMs ?: ((spikeTimesMs.maxOrNull() ?: 0L) + 100)
        var triggers = 0
        var t = 0L
        while (t <= end) {
            val g = if (spikeTimesMs.any { it in t until t + 20 }) peakG else 1f
            if (logic.onSample(g, t)) triggers++
            t += 20
        }
        return triggers
    }

    @Test fun deliberateShakeTriggersOnce() {
        assertEquals(1, run(ShakeLogic(), listOf(100, 400, 700, 1000)))
    }

    @Test fun pickingUpThePhoneDoesNotTrigger() {
        // One or two bumps, like lifting it off the table and putting it down.
        assertEquals(0, run(ShakeLogic(), listOf(200, 600)))
    }

    @Test fun gentleMovementBelowThresholdDoesNotTrigger() {
        assertEquals(0, run(ShakeLogic(), listOf(100, 300, 500, 700, 900, 1100), peakG = 1.8f))
    }

    @Test fun spikesSpreadTooFarApartDoNotTrigger() {
        assertEquals(0, run(ShakeLogic(), listOf(0, 800, 1600, 2400)))
    }

    @Test fun oneLongSwingCountsAsOneSpike() {
        val logic = ShakeLogic()
        // 10 consecutive high samples (200 ms) are one swing: never 4 spikes.
        var fired = false
        for (i in 0 until 10) fired = fired or logic.onSample(3f, i * 20L)
        assertFalse(fired)
    }

    @Test fun continuedShakingWithinCooldownDoesNotTriggerTwice() {
        // Shaking for 3 seconds straight: one conversation, not several.
        val spikes = (0 until 12).map { 100L + it * 250 }
        assertEquals(1, run(ShakeLogic(), spikes))
    }

    @Test fun secondShakeAfterCooldownTriggersAgain() {
        val first = listOf(100L, 400, 700, 1000)
        val second = first.map { it + 6000 }
        assertEquals(2, run(ShakeLogic(), first + second))
    }

    @Test fun sensitivityIsConfigurable() {
        val sensitive = ShakeLogic(ShakeLogic.Config(thresholdG = 1.6f, requiredSpikes = 3))
        assertTrue(run(sensitive, listOf(100, 400, 700), peakG = 1.8f) == 1)
    }
}
