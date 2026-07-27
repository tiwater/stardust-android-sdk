package cn.ticos.stardust.sdk.audio

import java.io.ByteArrayOutputStream

internal const val PCM_SAMPLE_RATE_HZ = 24_000
internal const val PCM_BYTES_PER_FRAME = 2
internal const val PCM_BYTES_PER_MILLISECOND =
    PCM_SAMPLE_RATE_HZ * PCM_BYTES_PER_FRAME / 1_000

/** Initial network-jitter allowance: 160 ms of 24 kHz mono PCM16. */
internal const val PLAYBACK_INITIAL_BUFFER_BYTES = 160 * PCM_BYTES_PER_MILLISECOND

/** One server delta / one adaptive step: 40 ms. */
internal const val PLAYBACK_BUFFER_STEP_BYTES = 40 * PCM_BYTES_PER_MILLISECOND

/** Maximum application playout target: 320 ms (device minimum may be larger). */
internal const val PLAYBACK_MAX_BUFFER_BYTES = 320 * PCM_BYTES_PER_MILLISECOND

internal data class PlayoutChunk(
    val bytes: ByteArray,
    val startsPlayout: Boolean,
)

/**
 * Applies a start watermark once per audio segment.
 *
 * Before playout starts, PCM is accumulated to absorb arrival jitter. Once the watermark is
 * reached, every following delta is released immediately; unlike the old coalescer, it never
 * waits for another full watermark-sized block.
 */
internal class PcmPlayoutBuffer(
    initialStartThresholdBytes: Int = PLAYBACK_INITIAL_BUFFER_BYTES,
    maximumStartThresholdBytes: Int = PLAYBACK_MAX_BUFFER_BYTES,
) {
    private val buffer = ByteArrayOutputStream()
    private var started = false
    private var maximumStartThresholdBytes =
        frameAligned(maximumStartThresholdBytes.coerceAtLeast(PCM_BYTES_PER_FRAME))
    private var startThresholdBytes = sanitizeThreshold(initialStartThresholdBytes)

    @Synchronized
    fun append(pcm: ByteArray): PlayoutChunk? {
        if (pcm.isEmpty()) return null
        if (started) return PlayoutChunk(bytes = pcm, startsPlayout = false)

        buffer.write(pcm)
        if (buffer.size() < startThresholdBytes) return null

        started = true
        return PlayoutChunk(bytes = drainPending(), startsPlayout = true)
    }

    /**
     * Flushes a short final segment even when it never reached the start watermark.
     * The next append belongs to a fresh segment and will prebuffer again.
     */
    @Synchronized
    fun endSegment(): PlayoutChunk? {
        val tail = if (!started && buffer.size() > 0) {
            PlayoutChunk(bytes = drainPending(), startsPlayout = true)
        } else {
            null
        }
        buffer.reset()
        started = false
        return tail
    }

    @Synchronized
    fun pendingBytes(): Int = buffer.size()

    @Synchronized
    fun currentStartThresholdBytes(): Int = startThresholdBytes

    @Synchronized
    fun ensureStartThresholdAtLeast(bytes: Int) {
        if (bytes > maximumStartThresholdBytes) {
            maximumStartThresholdBytes = frameAligned(bytes)
        }
        startThresholdBytes = sanitizeThreshold(maxOf(startThresholdBytes, bytes))
    }

    /** Raises the next segment's start watermark after a measured AudioTrack underrun. */
    @Synchronized
    fun increaseStartThreshold(): Int {
        startThresholdBytes = sanitizeThreshold(startThresholdBytes + PLAYBACK_BUFFER_STEP_BYTES)
        return startThresholdBytes
    }

    @Synchronized
    fun clear() {
        buffer.reset()
        started = false
    }

    private fun drainPending(): ByteArray {
        val out = buffer.toByteArray()
        buffer.reset()
        return out
    }

    private fun sanitizeThreshold(bytes: Int): Int {
        val positive = bytes.coerceAtLeast(PCM_BYTES_PER_FRAME)
        val capped = positive.coerceAtMost(
            maximumStartThresholdBytes.coerceAtLeast(PCM_BYTES_PER_FRAME),
        )
        return frameAligned(capped)
    }

    private fun frameAligned(bytes: Int): Int =
        bytes - (bytes % PCM_BYTES_PER_FRAME)
}
