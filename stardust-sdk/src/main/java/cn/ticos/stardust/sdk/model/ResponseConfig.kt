package cn.ticos.stardust.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResponseConfig(
    val modalities: List<String>? = null,
    val instructions: String? = null,
    val voice: String? = null,
    val tools: List<ToolConfig>? = null,
    val temperature: Double? = null,
    @SerialName("max_output_tokens") val maxOutputTokens: Int? = null,
    val conversation: JsonElement? = null,
    val input: JsonElement? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        fun audio(): ResponseConfig = ResponseConfig(modalities = listOf("audio"))
        fun text(): ResponseConfig = ResponseConfig(modalities = listOf("text"))
    }
}
