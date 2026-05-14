package cn.ticos.stardust.sdk.internal

import cn.ticos.stardust.sdk.model.HearingConfig
import cn.ticos.stardust.sdk.model.SpeechConfig
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsSerializationTest {

    @Test
    fun speechConfig_serializes_output_audio_format_and_ratios() {
        val s = SpeechConfig(
            voice = "zh_test",
            outputAudioFormat = "pcm16",
            emotion = "neutral",
            speedRatio = 55,
            pitchRatio = 50,
            volumeRatio = 60,
        )
        val obj = StardustJson.encodeToJsonElement(s).jsonObject
        assertEquals("pcm16", obj["output_audio_format"]?.jsonPrimitive?.content)
        assertEquals("neutral", obj["emotion"]?.jsonPrimitive?.content)
        assertEquals("55", obj["speed_ratio"]?.jsonPrimitive?.content)
    }

    @Test
    fun hearingConfig_serializes_input_audio_format() {
        val h = HearingConfig(inputAudioFormat = "pcm16", turnDetection = JsonNull)
        val obj = StardustJson.encodeToJsonElement(h).jsonObject
        assertEquals("pcm16", obj["input_audio_format"]?.jsonPrimitive?.content)
        assertTrue(obj.containsKey("turn_detection"))
    }

    @Test
    fun speechConfig_does_not_encode_null_speed() {
        val s = SpeechConfig(voice = "x", outputAudioFormat = "pcm16")
        val obj = StardustJson.encodeToJsonElement(s).jsonObject
        assertFalse(obj.containsKey("speed_ratio"))
    }
}
