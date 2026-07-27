package cn.ticos.stardust.sdk.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmPlayoutBufferTest {
    @Test
    fun append_prebuffersOnceThenReleasesEveryDelta() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 8,
            maximumStartThresholdBytes = 16,
        )

        assertNull(buffer.append(byteArrayOf(1, 2, 3, 4)))
        val start = buffer.append(byteArrayOf(5, 6, 7, 8))
        assertTrue(start?.startsPlayout == true)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), start?.bytes)

        val next = buffer.append(byteArrayOf(9, 10))
        assertFalse(next?.startsPlayout ?: true)
        assertArrayEquals(byteArrayOf(9, 10), next?.bytes)
    }

    @Test
    fun endSegment_flushesShortTailAndResetsStartWatermark() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 8,
            maximumStartThresholdBytes = 16,
        )

        assertNull(buffer.append(byteArrayOf(1, 2, 3, 4)))
        val tail = buffer.endSegment()
        assertTrue(tail?.startsPlayout == true)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), tail?.bytes)

        assertNull(buffer.append(byteArrayOf(5, 6)))
        assertEquals(2, buffer.pendingBytes())
    }

    @Test
    fun append_largeFirstDelta_releasesWholeDeltaWithoutPeriodicBatching() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 8,
            maximumStartThresholdBytes = 16,
        )
        val largeDelta = ByteArray(14) { it.toByte() }

        val start = buffer.append(largeDelta)

        assertTrue(start?.startsPlayout == true)
        assertArrayEquals(largeDelta, start?.bytes)
        assertEquals(0, buffer.pendingBytes())
    }

    @Test
    fun increaseStartThreshold_isFrameAlignedAndCapped() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 5,
            maximumStartThresholdBytes = 10,
        )

        assertEquals(4, buffer.currentStartThresholdBytes())
        assertEquals(10, buffer.increaseStartThreshold())
        assertEquals(10, buffer.increaseStartThreshold())
    }

    @Test
    fun deviceMinimum_canRaiseThresholdAboveDefaultMaximum() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 8,
            maximumStartThresholdBytes = 16,
        )

        buffer.ensureStartThresholdAtLeast(24)

        assertEquals(24, buffer.currentStartThresholdBytes())
        assertEquals(24, buffer.increaseStartThreshold())
    }

    @Test
    fun clear_discardsPendingAndStartsFresh() {
        val buffer = PcmPlayoutBuffer(
            initialStartThresholdBytes = 8,
            maximumStartThresholdBytes = 16,
        )
        buffer.append(byteArrayOf(1, 2, 3, 4))
        buffer.clear()

        assertEquals(0, buffer.pendingBytes())
        assertNull(buffer.append(byteArrayOf(5, 6, 7, 8)))
    }
}
