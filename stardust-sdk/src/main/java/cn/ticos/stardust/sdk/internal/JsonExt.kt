package cn.ticos.stardust.sdk.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

val StardustJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

fun JsonElement?.asObjectOrEmpty(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())

fun JsonObject.stringOrNull(key: String): String? = this[key]?.let { (it as? JsonPrimitive)?.contentOrNull }

fun mergeUnknown(base: JsonObject, unknownFields: Map<String, JsonElement>): JsonObject {
    if (unknownFields.isEmpty()) return base
    return buildJsonObject {
        base.forEach { (k, v) -> put(k, v) }
        unknownFields.forEach { (k, v) ->
            if (!base.containsKey(k)) {
                put(k, v)
            }
        }
    }
}

fun buildConversationItemPayload(
    userId: String,
    previousItemId: String?,
    text: String?,
    imageUrls: List<String>,
): JsonObject {
    val payload = buildJsonObject {
        put("type", "conversation.item.create")
        if (previousItemId != null) {
            put("previous_item_id", previousItemId)
        }
        putJsonObject("item") {
            put("type", "message")
            put("role", "user")
            put("user_id", userId)
            putJsonArray("content") {
                if (!text.isNullOrBlank()) {
                    add(
                        buildJsonObject {
                            put("type", "input_text")
                            put("text", text)
                        },
                    )
                }
                imageUrls.forEach { imageUrl ->
                    add(
                        buildJsonObject {
                            put("type", "input_image")
                            put("image_url", imageUrl)
                        },
                    )
                }
            }
        }
    }
    return payload
}
