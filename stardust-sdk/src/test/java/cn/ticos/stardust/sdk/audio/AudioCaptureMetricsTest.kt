package cn.ticos.stardust.sdk.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class AudioCaptureMetricsTest {

    @Test
    fun computeRmsLevel_emptyOrTooShort_returnsZero() {
        assertEquals(0f, computeRmsLevel(byteArrayOf()), 1e-6f)
        assertEquals(0f, computeRmsLevel(byteArrayOf(0)), 1e-6f)
    }

    @Test
    fun computeRmsLevel_silence_returnsZero() {
        val silence = ByteArray(1920) { 0 }
        assertEquals(0f, computeRmsLevel(silence), 1e-6f)
    }

    @Test
    fun computeRmsLevel_maxAmplitude_nearOne() {
        // 32767, LE: FF 7F
        val frame = ByteArray(960 * 2)
        var i = 0
        while (i < frame.size) {
            frame[i] = 0xFF.toByte()
            frame[i + 1] = 0x7F.toByte()
            i += 2
        }
        val level = computeRmsLevel(frame)
        assertTrue("expected near 1.0, got $level", level > 0.99f && level <= 1f)
    }

    @Test
    fun computeRmsLevel_knownTwoSamples() {
        // samples: 1, 0 → RMS = sqrt(0.5)
        val pcm = byteArrayOf(1, 0, 0, 0)
        val expected = (sqrt(0.5) / 32768.0).toFloat()
        assertEquals(expected, computeRmsLevel(pcm), 1e-5f)
    }
}
