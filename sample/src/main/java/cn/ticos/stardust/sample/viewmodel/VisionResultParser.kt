package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sample.model.BoundingBox
import cn.ticos.stardust.sample.model.FaceDetail
import cn.ticos.stardust.sample.model.FaceInfo
import cn.ticos.stardust.sample.model.HandDetail
import cn.ticos.stardust.sample.model.HandInfo
import cn.ticos.stardust.sample.model.ImageInfo
import cn.ticos.stardust.sample.model.ObjectDetail
import cn.ticos.stardust.sample.model.ObjectInfo
import cn.ticos.stardust.sample.model.VisionResultItem
import cn.ticos.stardust.sdk.StardustEvent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object VisionResultParser {

    fun parse(event: StardustEvent.ResponseVideoDone): VisionResultItem? {
        val payload = event.payload
        val faceInfo = payload.parseFaceInfo()
        val objectInfo = payload.parseObjectInfo()
        val handInfo = payload.parseHandInfo()
        val imageInfo = payload.parseImageInfo()
        if (faceInfo == null && objectInfo == null && handInfo == null && imageInfo == null) {
            return null
        }
        return VisionResultItem(
            id = event.eventId ?: System.currentTimeMillis().toString(),
            timestamp = System.currentTimeMillis(),
            faceInfo = faceInfo,
            objectInfo = objectInfo,
            handInfo = handInfo,
            imageInfo = imageInfo,
            rawJson = event.rawJson,
        )
    }
}

private fun JsonObject.parseFaceInfo(): FaceInfo? {
    val faceRoot = this["face_info"] as? JsonObject ?: return null
    val facesEl = faceRoot["faces"] as? JsonArray ?: return null
    val details = facesEl.mapNotNull { it.parseFaceDetail() }
    if (details.isEmpty()) return null
    return FaceInfo(faceCount = details.size, faces = details)
}

private fun JsonElement.parseFaceDetail(): FaceDetail? {
    val o = this as? JsonObject ?: return null
    val conf = o.floatField("confidence") ?: 0f
    val bbox = o.parseFaceBbox()
    return FaceDetail(confidence = conf, bbox = bbox)
}

private fun JsonObject.parseFaceBbox(): BoundingBox? {
    val x = floatField("x") ?: return null
    val y = floatField("y") ?: return null
    val w = floatField("width") ?: return null
    val h = floatField("height") ?: return null
    return BoundingBox(x, y, w, h)
}

private fun JsonObject.parseObjectInfo(): ObjectInfo? {
    val root = this["object_info"] as? JsonObject ?: return null
    val arr = root["objects"] as? JsonArray ?: return null
    val objects = arr.mapNotNull { it.parseObjectDetail() }
    if (objects.isEmpty()) return null
    return ObjectInfo(objects = objects)
}

private fun JsonElement.parseObjectDetail(): ObjectDetail? {
    val o = this as? JsonObject ?: return null
    val label = o.stringField("label") ?: return null
    val conf = o.floatField("confidence") ?: 0f
    val bbox = (o["bbox"] as? JsonArray)?.parseBboxFromArray()
    return ObjectDetail(label = label, confidence = conf, bbox = bbox)
}

private fun JsonArray.parseBboxFromArray(): BoundingBox? {
    if (size < 4) return null
    val nums = mapNotNull { it.toFloatOrNull() }
    if (nums.size < 4) return null
    return BoundingBox(nums[0], nums[1], nums[2], nums[3])
}

private fun JsonObject.parseHandInfo(): HandInfo? {
    val root = this["hand_info"] as? JsonObject ?: return null
    val arr = root["hands"] as? JsonArray ?: return null
    val hands = arr.mapNotNull { it.parseHandDetail() }
    if (hands.isEmpty()) return null
    return HandInfo(hands = hands)
}

private fun JsonElement.parseHandDetail(): HandDetail? {
    val o = this as? JsonObject ?: return null
    val gesture = o.stringField("gesture") ?: return null
    val conf = o.floatField("confidence") ?: return null
    return HandDetail(gesture = gesture, confidence = conf)
}

private fun JsonObject.parseImageInfo(): ImageInfo? {
    val root = this["image_info"] as? JsonObject ?: return null
    val desc = root.stringField("description")
    val labelsEl = root["labels"] as? JsonArray
    val labels = labelsEl?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    if (desc == null && labels.isEmpty()) return null
    return ImageInfo(description = desc, labels = labels)
}

private fun JsonObject.floatField(key: String): Float? =
    (this[key] as? JsonPrimitive)?.toFloatOrNull()

private fun JsonObject.stringField(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonPrimitive.toFloatOrNull(): Float? =
    contentOrNull?.toFloatOrNull() ?: contentOrNull?.toDoubleOrNull()?.toFloat()

private fun JsonElement.toFloatOrNull(): Float? =
    (this as? JsonPrimitive)?.toFloatOrNull()
