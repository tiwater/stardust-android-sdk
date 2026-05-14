package cn.ticos.stardust.sample.model

import cn.ticos.stardust.sdk.model.HearingConfig
import cn.ticos.stardust.sdk.model.ModelConfig
import cn.ticos.stardust.sdk.model.SessionConfig
import cn.ticos.stardust.sdk.model.SpeechConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Modalities 固定为 text + audio，不开放用户配置。 */
private val ADVANCED_SESSION_MODALITIES = listOf("text", "audio")

fun AdvancedSessionSettings.toSessionConfig(): SessionConfig = SessionConfig(
    agentId = null,
    model = ModelConfig(
        provider = modelProvider,
        name = modelName,
        modalities = ADVANCED_SESSION_MODALITIES,
        instructions = JsonPrimitive(instructions),
        temperature = temperature,
        topP = topP,
        topK = topK,
        maxResponseOutputTokens = maxResponseOutputTokens,
        historyConversationLength = historyConversationLength,
    ),
    speech = SpeechConfig(
        voice = speechVoice,
        outputAudioFormat = "pcm16",
        emotion = speechEmotion,
        speedRatio = speechSpeedRatio,
        pitchRatio = speechPitchRatio,
        volumeRatio = speechVolumeRatio,
    ),
    hearing = HearingConfig(
        provider = hearingProvider.ifBlank { null },
        inputAudioFormat = "pcm16",
        turnDetection = buildJsonObject {
            put("type", "server_vad")
            put("threshold", hearingSensitivity)
            put("prefix_padding_ms", hearingPrefixPaddingMs)
            put("silence_duration_ms", hearingSilenceDurationMs)
        },
    ),
)
