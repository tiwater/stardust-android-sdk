package cn.ticos.stardust.sdk.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcmPlaybackCoalescerTest {
    @Test
    fun drainIfReady_belowThreshold_returnsNullUnlessForced() {
        val coalescer = PcmPlaybackCoalescer(flushThresholdBytes = 100)
        coalescer.append(ByteArray(40) { 1 })
        assertNull(coalescer.drainIfReady(force = false))
        val forced = coalescer.drainIfReady(force = true)
        assertEquals(40, forced?.size)
    }

    @Test
    fun drainIfReady_atThreshold_drainsOneBlockAndKeepsRemainder() {
        val coalescer = PcmPlaybackCoalescer(flushThresholdBytes = 100)
        coalescer.append(ByteArray(60) { 2 })
        coalescer.append(ByteArray(80) { 3 })
        val first = coalescer.drainIfReady(force = false)
        assertEquals(100, first?.size)
        assertEquals(40, coalescer.pendingBytes())
        assertNull(coalescer.drainIfReady(force = false))
        val tail = coalescer.drainIfReady(force = true)
        assertEquals(40, tail?.size)
    }

    @Test
    fun append_afterDrain_startsFreshBuffer() {
        val coalescer = PcmPlaybackCoalescer(flushThresholdBytes = 10)
        val first = ByteArray(10) { 7 }
        coalescer.append(first)
        assertArrayEquals(first, coalescer.drainIfReady(force = false))
        coalescer.append(byteArrayOf(9, 8))
        assertArrayEquals(byteArrayOf(9, 8), coalescer.drainIfReady(force = true))
    }

    @Test
    fun clear_discardsPending() {
        val coalescer = PcmPlaybackCoalescer(flushThresholdBytes = 10)
        coalescer.append(ByteArray(5))
        coalescer.clear()
        assertEquals(0, coalescer.pendingBytes())
        assertNull(coalescer.drainIfReady(force = true))
    }
}
