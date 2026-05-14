package cn.ticos.stardust.sdk.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JpegFramePacketizerTest {
    @Test
    fun packetize_writesHeaderPayloadAndPad() {
        val packetizer = JpegFramePacketizer(startSeq = 1)
        val jpeg = byteArrayOf(0x01, 0x02, 0x03, 0x7F.toByte(), 0xD9.toByte())

        val packet = packetizer.packetize(jpeg)

        assertEquals(0x54.toByte(), packet[0])
        assertEquals(0x20.toByte(), packet[1])
        val seq = ByteBuffer.wrap(packet, 2, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFF_FFFFL
        assertEquals(1L, seq)
        val msgLen = ByteBuffer.wrap(packet, 6, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(jpeg.size, msgLen)
        assertArrayEquals(jpeg, packet.copyOfRange(10, packet.size - 1))
        assertEquals(0x00.toByte(), packet.last())
    }
}
