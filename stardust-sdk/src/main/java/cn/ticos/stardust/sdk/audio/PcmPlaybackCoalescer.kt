package cn.ticos.stardust.sdk.audio

import java.io.ByteArrayOutputStream

/**
 * ~600 ms of 24 kHz mono PCM16 (24000 * 0.6 * 2).
 * Server emits ~40 ms (1920 B) deltas; larger batches reduce AudioTrack underruns.
 */
internal const val PLAYBACK_COALESCE_BYTES = 28_800

/**
 * Accumulates assistant PCM deltas. Non-forced [drainIfReady] emits at most one
 * [flushThresholdBytes] block so multiple chunks can sit in the playback queue.
 */
internal class PcmPlaybackCoalescer(
    private val flushThresholdBytes: Int = PLAYBACK_COALESCE_BYTES,
) {
    private val buffer = ByteArrayOutputStream()

    @Synchronized
    fun append(pcm: ByteArray) {
        buffer.write(pcm)
    }

    @Synchronized
    fun pendingBytes(): Int = buffer.size()

    @Synchronized
    fun drainIfReady(force: Boolean = false): ByteArray? {
        val size = buffer.size()
        if (size == 0) return null
        if (!force && size < flushThresholdBytes) return null
        val drainSize = if (force) size else flushThresholdBytes
        val all = buffer.toByteArray()
        val out = all.copyOfRange(0, drainSize)
        buffer.reset()
        if (drainSize < all.size) {
            buffer.write(all, drainSize, all.size - drainSize)
        }
        return out
    }

    @Synchronized
    fun clear() {
        buffer.reset()
    }
}
