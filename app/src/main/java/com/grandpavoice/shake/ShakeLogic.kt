package com.grandpavoice.shake

/**
 * The shake algorithm without any Android types, so it can be unit-tested.
 *
 *  - A spike is any sample whose total acceleration exceeds [Config.thresholdG] (1 g = resting).
 *  - Spikes closer than [MIN_SPIKE_GAP_MS] count as one (one swing, not many samples of it).
 *  - [Config.requiredSpikes] spikes inside [Config.windowMs] = a shake.
 *  - After a shake, nothing triggers again for [Config.cooldownMs].
 *
 * Picking up or putting down the phone gives one or two moderate spikes: not enough.
 */
class ShakeLogic(var config: Config = Config()) {

    data class Config(
        val thresholdG: Float = 2.2f,
        val requiredSpikes: Int = 4,
        val windowMs: Long = 1500,
        val cooldownMs: Long = 4000,
    )

    private val spikes = ArrayDeque<Long>()
    private var lastSpikeMs = Long.MIN_VALUE / 2
    private var lastTriggerMs = Long.MIN_VALUE / 2

    /** Feed one sample (total acceleration in g). @return true if this sample completes a shake. */
    fun onSample(g: Float, nowMs: Long): Boolean {
        val c = config
        if (g < c.thresholdG) return false
        if (nowMs - lastSpikeMs < MIN_SPIKE_GAP_MS) return false
        lastSpikeMs = nowMs

        spikes.addLast(nowMs)
        while (spikes.isNotEmpty() && nowMs - spikes.first() > c.windowMs) spikes.removeFirst()

        if (spikes.size < c.requiredSpikes) return false
        spikes.clear()
        if (nowMs - lastTriggerMs < c.cooldownMs) return false
        lastTriggerMs = nowMs
        return true
    }

    fun reset() {
        spikes.clear()
    }

    companion object {
        const val MIN_SPIKE_GAP_MS = 120L
    }
}
