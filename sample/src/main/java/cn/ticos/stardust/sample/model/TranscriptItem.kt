package cn.ticos.stardust.sample.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
data class TranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)
