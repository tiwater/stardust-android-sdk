package cn.ticos.stardust.sample.model

import androidx.compose.runtime.Immutable

@Immutable
data class VisionResultItem(
    val id: String,
    val timestamp: Long,
    val faceInfo: FaceInfo? = null,
    val objectInfo: ObjectInfo? = null,
    val handInfo: HandInfo? = null,
    val imageInfo: ImageInfo? = null,
    val rawJson: String? = null,
)

@Immutable
data class FaceInfo(
    val faceCount: Int,
    val faces: List<FaceDetail> = emptyList(),
)

@Immutable
data class FaceDetail(
    val confidence: Float,
    val bbox: BoundingBox? = null,
)

@Immutable
data class ObjectInfo(
    val objects: List<ObjectDetail> = emptyList(),
)

@Immutable
data class ObjectDetail(
    val label: String,
    val confidence: Float,
    val bbox: BoundingBox? = null,
)

@Immutable
data class HandInfo(
    val hands: List<HandDetail> = emptyList(),
)

@Immutable
data class HandDetail(
    val gesture: String,
    val confidence: Float,
)

@Immutable
data class ImageInfo(
    val description: String? = null,
    val labels: List<String> = emptyList(),
)

@Immutable
data class BoundingBox(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)
