package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sdk.StardustEvent
import cn.ticos.stardust.sample.model.ConversationRecord
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private val SENSITIVE_KEYS = setOf("token", "secret", "password", "key", "credential", "api_key")

fun StardustEvent.ConversationItemCreated.toConversationRecord(
    sessionId: Long,
): ConversationRecord? {
    val payload = this.payload
    val item = payload["item"] as? JsonObject ?: return null
    val roleStr = (item["role"] as? JsonPrimitive)?.contentOrNull ?: return null
    val itemId = (item["id"] as? JsonPrimitive)?.contentOrNull
    val itemType = (item["type"] as? JsonPrimitive)?.contentOrNull

    return when (roleStr.lowercase()) {
        "user" -> {
            val text = extractConvText(item)
            val isVoiceInput = isVoiceInputItem(item)
            if (isVoiceInput) {
                ConversationRecord.UserVoice(
                    sessionId = sessionId,
                    text = text ?: "",
                    itemId = itemId,
                    hasAudio = true,
                )
            } else {
                ConversationRecord.UserText(
                    sessionId = sessionId,
                    text = text ?: "",
                    itemId = itemId,
                )
            }
        }
        "assistant", "agent" -> {
            if (itemType == "function_call") {
                val name = (item["name"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
                val arguments = (item["arguments"] as? JsonPrimitive)?.contentOrNull ?: ""
                val callId = (item["call_id"] as? JsonPrimitive)?.contentOrNull
                ConversationRecord.FunctionCall(
                    sessionId = sessionId,
                    name = name,
                    arguments = sanitizeArguments(arguments),
                    callId = callId,
                    itemId = itemId,
                    responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull,
                )
            } else {
                val text = extractConvText(item)
                ConversationRecord.AssistantVoice(
                    sessionId = sessionId,
                    text = text ?: "",
                    itemId = itemId,
                    responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull,
                )
            }
        }
        else -> null
    }
}

fun StardustEvent.ResponseFunctionCallArgumentsDone.toFunctionCallRecord(
    sessionId: Long,
): ConversationRecord? {
    val payload = this.payload
    val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull
    val name = (payload["name"] as? JsonPrimitive)?.contentOrNull ?: "unknown"
    val arguments = (payload["arguments"] as? JsonPrimitive)?.contentOrNull ?: ""
    val callId = (payload["call_id"] as? JsonPrimitive)?.contentOrNull
    val responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull

    return ConversationRecord.FunctionCall(
        id = this.eventId ?: java.util.UUID.randomUUID().toString(),
        sessionId = sessionId,
        name = name,
        arguments = sanitizeArguments(arguments),
        callId = callId,
        itemId = itemId,
        responseId = responseId,
    )
}

fun StardustEvent.ResponseOutputItemDone.toFunctionCallRecord(
    sessionId: Long,
): ConversationRecord.FunctionCall? {
    val payload = this.payload
    val item = payload["item"] as? JsonObject ?: return null
    val itemType = item.stringOrNull("type")
    if (itemType != "function_call") return null

    val name = item.stringOrNull("name")
        ?: payload.stringOrNull("name")
        ?: "unknown"
    val arguments = item.stringOrNull("arguments")
        ?: payload.stringOrNull("arguments")
        ?: ""
    val callId = item.stringOrNull("call_id")
        ?: payload.stringOrNull("call_id")
    val itemId = item.stringOrNull("id")
        ?: item.stringOrNull("item_id")
        ?: payload.stringOrNull("item_id")

    return ConversationRecord.FunctionCall(
        id = this.eventId ?: java.util.UUID.randomUUID().toString(),
        sessionId = sessionId,
        name = name,
        arguments = sanitizeArguments(arguments),
        callId = callId,
        itemId = itemId,
        responseId = payload.stringOrNull("response_id"),
    )
}

fun StardustEvent.ResponseAudioDelta.toAssistantAudioDeltaRecord(
    sessionId: Long,
): ConversationRecord.AssistantVoice? {
    val payload = this.payload
    val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return null
    val responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull
    return ConversationRecord.AssistantVoice(
        sessionId = sessionId,
        text = "",
        itemId = itemId,
        responseId = responseId,
        hasAudio = true,
        audioSegments = 1,
    )
}

fun StardustEvent.Unknown.toFunctionCallRecordIfApplicable(
    sessionId: Long,
    existingItemIds: Set<String>,
): ConversationRecord.FunctionCall? {
    val typeStr = this.type ?: return null
    if (!typeStr.contains("function", ignoreCase = true) &&
        !typeStr.contains("tool", ignoreCase = true)) return null

    val payload = this.payload
    val name = payload.stringOrNull("name")
        ?: (payload["function"] as? JsonObject)?.stringOrNull("name")
        ?: return null
    val arguments = payload.stringOrNull("arguments")
        ?: (payload["function"] as? JsonObject)?.stringOrNull("arguments")
        ?: ""
    val itemId = payload.stringOrNull("item_id")

    if (itemId != null && itemId in existingItemIds) return null

    return ConversationRecord.FunctionCall(
        id = this.eventId ?: java.util.UUID.randomUUID().toString(),
        sessionId = sessionId,
        name = name,
        arguments = sanitizeArguments(arguments),
        itemId = itemId,
        responseId = payload.stringOrNull("response_id"),
    )
}

private fun extractConvText(item: JsonObject): String? {
    val content = item["content"] ?: return item.stringOrNull("text")
    return when (content) {
        is JsonPrimitive -> content.contentOrNull
        is JsonArray -> content.joinToString(separator = "") { part ->
            when (part) {
                is JsonObject -> (part.stringOrNull("text") ?: part.stringOrNull("transcript")).orEmpty()
                is JsonPrimitive -> part.contentOrNull.orEmpty()
                else -> ""
            }
        }.ifBlank { null }
        else -> null
    }
}

private fun isVoiceInputItem(item: JsonObject): Boolean {
    val content = item["content"]
    if (content is JsonArray) {
        return content.any { part ->
            val partObj = part as? JsonObject ?: return@any false
            val type = (partObj["type"] as? JsonPrimitive)?.contentOrNull
            type == "input_audio" || type == "audio"
        }
    }
    val type = (item["type"] as? JsonPrimitive)?.contentOrNull
    return type != "input_text" && type != "text"
}

fun sanitizeArguments(arguments: String): String {
    if (arguments.isBlank()) return arguments
    return try {
        val json = kotlinx.serialization.json.Json.parseToJsonElement(arguments)
        if (json is JsonObject) {
            sanitizeJsonObject(json).toString()
        } else {
            arguments
        }
    } catch (_: Throwable) {
        arguments
    }
}

private fun sanitizeJsonObject(obj: JsonObject): JsonObject {
    val sanitized = obj.entries.associate { (key, value) ->
        val isSensitive = SENSITIVE_KEYS.any { key.lowercase().contains(it) }
        key to when {
            isSensitive -> JsonPrimitive("***REDACTED***")
            value is JsonObject -> sanitizeJsonObject(value)
            else -> value
        }
    }
    return JsonObject(sanitized)
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
