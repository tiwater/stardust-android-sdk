package cn.ticos.stardust.sdk.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
sealed interface ToolConfig {
    val type: String
    val unknownFields: Map<String, JsonElement>
}

@Serializable
@SerialName("function")
data class FunctionToolConfig(
    override val type: String = "function",
    val name: String,
    val description: String? = null,
    val parameters: JsonElement? = null,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : ToolConfig

@Serializable
@SerialName("mcp")
data class McpToolConfig(
    override val type: String = "mcp",
    @SerialName("server_label") val serverLabel: String? = null,
    @SerialName("server_url") val serverUrl: String? = null,
    @SerialName("server_description") val serverDescription: String? = null,
    @SerialName("allowed_tools") val allowedTools: List<String>? = null,
    @SerialName("require_approval") val requireApproval: Boolean? = null,
    val authorization: String? = null,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : ToolConfig

@Serializable
@SerialName("ticos_mcp")
data class TicosMcpToolConfig(
    override val type: String = "ticos_mcp",
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonElement? = null,
    @SerialName("server_url") val serverUrl: String? = null,
    @SerialName("mcp_server_id") val mcpServerId: String? = null,
    @SerialName("mcp_api_key") val mcpApiKey: String? = null,
    @SerialName("server_label") val serverLabel: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("operation_mode") val operationMode: String? = null,
    @SerialName("execution_type") val executionType: String? = null,
    @SerialName("result_handling") val resultHandling: String? = null,
    val code: String? = null,
    val language: String? = null,
    val authorization: String? = null,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : ToolConfig
