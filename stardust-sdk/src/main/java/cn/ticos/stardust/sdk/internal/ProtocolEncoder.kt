package cn.ticos.stardust.sdk.internal

import cn.ticos.stardust.sdk.model.FunctionToolConfig
import cn.ticos.stardust.sdk.model.McpToolConfig
import cn.ticos.stardust.sdk.model.ModelConfig
import cn.ticos.stardust.sdk.model.ResponseConfig
import cn.ticos.stardust.sdk.model.SessionConfig
import cn.ticos.stardust.sdk.model.TicosMcpToolConfig
import cn.ticos.stardust.sdk.model.ToolConfig
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal object ProtocolEncoder {
    fun sessionUpdatePayload(session: SessionConfig): JsonObject {
        return buildJsonObject {
            put("type", "session.update")
            put("session", sessionToJson(session))
        }
    }

    fun responseCreatePayload(response: ResponseConfig): JsonObject {
        return buildJsonObject {
            put("type", "response.create")
            put("response", responseToJson(response))
        }
    }

    private fun responseToJson(response: ResponseConfig): JsonObject {
        val base = StardustJson.encodeToJsonElement(response).asObjectOrEmpty()
        val withTools = if (response.tools != null) {
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                putJsonArray("tools") {
                    response.tools.forEach { add(toolToJson(it)) }
                }
            }
        } else {
            base
        }
        return mergeUnknown(withTools, response.unknownFields)
    }

    private fun sessionToJson(session: SessionConfig): JsonObject {
        val base = StardustJson.encodeToJsonElement(session).asObjectOrEmpty()
        val withModel = if (session.model != null) {
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                put("model", modelToJson(session.model))
            }
        } else {
            base
        }
        return mergeUnknown(withModel, session.unknownFields)
    }

    private fun modelToJson(model: ModelConfig): JsonObject {
        val base = StardustJson.encodeToJsonElement(model).asObjectOrEmpty()
        val withTools = if (model.tools != null) {
            buildJsonObject {
                base.forEach { (k, v) -> put(k, v) }
                putJsonArray("tools") {
                    model.tools.forEach { add(toolToJson(it)) }
                }
            }
        } else {
            base
        }
        return mergeUnknown(withTools, model.unknownFields)
    }

    private fun toolToJson(tool: ToolConfig): JsonElement {
        return when (tool) {
            is FunctionToolConfig -> mergeUnknown(
                StardustJson.encodeToJsonElement(tool).asObjectOrEmpty(),
                tool.unknownFields,
            )

            is McpToolConfig -> mergeUnknown(
                StardustJson.encodeToJsonElement(tool).asObjectOrEmpty(),
                tool.unknownFields,
            )

            is TicosMcpToolConfig -> mergeUnknown(
                StardustJson.encodeToJsonElement(tool).asObjectOrEmpty(),
                tool.unknownFields,
            )
        }
    }
}
