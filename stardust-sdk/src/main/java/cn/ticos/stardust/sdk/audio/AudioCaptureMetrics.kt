package cn.ticos.stardust.sdk.audio

import kotlin.math.sqrt

/** PCM16 LE 整帧的 RMS，归一化到 [0, 1]（满幅参考 32768）。 */
internal fun computeRmsLevel(pcm16: ByteArray): Float {
    if (pcm16.size < 2) return 0f
    val frameSize = pcm16.size and 0xFFFE
    val sampleCount = frameSize / 2
    var sumSquares = 0L
    for (i in 0 until frameSize step 2) {
        val low = pcm16[i].toInt() and 0xFF
        val high = pcm16[i + 1].toInt()
        val signed = (low or (high shl 8)).toShort().toInt()
        val s = signed.toLong()
        sumSquares += s * s
    }
    val rms = sqrt(sumSquares.toDouble() / sampleCount)
    return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
}
