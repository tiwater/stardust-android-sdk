package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sdk.StardustEvent
import cn.ticos.stardust.sample.model.Role
import cn.ticos.stardust.sample.model.TranscriptItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val MAX_TEXT = 2000

fun StardustEvent.ConversationItemCreated.toTranscriptItemOrNull(): TranscriptItem? {
    val text = extractText(payload) ?: return null
    if (text.isBlank()) return null
    val role = extractRole(payload) ?: return null
    return TranscriptItem(role = role, text = text.take(MAX_TEXT))
}

private fun extractRole(root: JsonObject): Role? {
    val item = root["item"] as? JsonObject ?: return null
    val roleStr = item.stringOrNull("role") ?: return null
    return when (roleStr.lowercase()) {
        "user" -> Role.User
        "assistant", "agent" -> Role.Agent
        else -> null
    }
}

private fun extractText(root: JsonObject): String? {
    val item = root["item"] as? JsonObject ?: return null
    val content = item["content"] ?: return item.stringOrNull("text")
    return when (content) {
        is JsonPrimitive -> content.contentOrNull
        is JsonArray -> content.joinToString(separator = "") { part ->
            when (part) {
                is JsonObject -> {
                    when (part.stringOrNull("type")) {
                        "input_text", "text" -> part.stringOrNull("text").orEmpty()
                        else -> part.stringOrNull("text").orEmpty()
                    }
                }
                is JsonPrimitive -> part.contentOrNull.orEmpty()
                else -> ""
            }
        }.ifBlank { null }
        else -> null
    }
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
