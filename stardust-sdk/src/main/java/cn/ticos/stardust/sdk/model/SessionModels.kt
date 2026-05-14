package cn.ticos.stardust.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SessionConfig(
    @SerialName("agent_id") val agentId: String? = null,
    val model: ModelConfig? = null,
    val speech: SpeechConfig? = null,
    val hearing: HearingConfig? = null,
    val vision: VisionConfig? = null,
    val knowledge: KnowledgeConfig? = null,
    val webhook: WebhookConfig? = null,
    val triggers: List<TriggerConfig>? = null,
    val extra: Map<String, JsonElement>? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ModelConfig(
    val provider: String? = null,
    val name: String? = null,
    val modalities: List<String>? = null,
    val instructions: JsonElement? = null,
    @SerialName("ext_config") val extConfig: Map<String, JsonElement>? = null,
    @SerialName("include_initial_prompt") val includeInitialPrompt: Boolean? = null,
    @SerialName("initial_user_prompt") val initialUserPrompt: String? = null,
    @SerialName("initial_assistant_prompt") val initialAssistantPrompt: String? = null,
    @SerialName("history_conversation_length") val historyConversationLength: Int? = null,
    val tools: List<ToolConfig>? = null,
    @SerialName("tool_choice") val toolChoice: JsonElement? = null,
    @SerialName("use_inner_tools") val useInnerTools: Boolean? = null,
    @SerialName("use_inner_view_tools") val useInnerViewTools: Boolean? = null,
    @SerialName("emotion_classifier") val emotionClassifier: Boolean? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    @SerialName("top_k") val topK: Int? = null,
    @SerialName("max_response_output_tokens") val maxResponseOutputTokens: Int? = null,
    val messages: Map<String, List<ConversationMessage>>? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class ConversationMessage(
    val role: String,
    val content: String,
)

@Serializable
data class SpeechConfig(
    val voice: String? = null,
    @Deprecated("Use outputAudioFormat", ReplaceWith("outputAudioFormat"))
    val format: String? = null,
    @SerialName("output_audio_format") val outputAudioFormat: String? = null,
    val emotion: String? = null,
    @SerialName("speed_ratio") val speedRatio: Int? = null,
    @SerialName("pitch_ratio") val pitchRatio: Int? = null,
    @SerialName("volume_ratio") val volumeRatio: Int? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class HearingConfig(
    val provider: String? = null,
    @SerialName("input_audio_format") val inputAudioFormat: String? = null,
    @SerialName("turn_detection") val turnDetection: JsonElement? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class VisionConfig(
    val enabled: Boolean? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class KnowledgeConfig(
    val enabled: Boolean? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class WebhookConfig(
    val url: String? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class TriggerConfig(
    val type: String? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
)
