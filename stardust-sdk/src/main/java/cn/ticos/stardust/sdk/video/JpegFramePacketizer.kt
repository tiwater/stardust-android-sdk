package cn.ticos.stardust.sdk.video

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

internal class JpegFramePacketizer(
    startSeq: Long = 0,
    private val pad: Byte = 0x00,
) {
    private val seq = AtomicLong(startSeq)

    fun packetize(jpeg: ByteArray): ByteArray {
        val current = seq.getAndUpdate { (it + 1) and 0xFFFF_FFFFL }
        val buffer = ByteBuffer.allocate(10 + jpeg.size + 1).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(0x54.toByte())
        buffer.put(0x20.toByte())
        buffer.putInt(current.toInt())
        buffer.putInt(jpeg.size)
        buffer.put(jpeg)
        buffer.put(pad)
        return buffer.array()
    }
}
