package cn.ticos.stardust.sample.model

import androidx.compose.runtime.Immutable
import java.util.UUID

@Immutable
sealed class ConversationRecord {
    abstract val id: String
    abstract val timestamp: Long
    abstract val sessionId: Long

    @Immutable
    data class UserVoice(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val sessionId: Long,
        val text: String,
        val itemId: String? = null,
        val hasAudio: Boolean = false,
        val audioSegments: Int = 0,
        val audioDurationMs: Long = 0L,
    ) : ConversationRecord()

    @Immutable
    data class UserText(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val sessionId: Long,
        val text: String,
        val itemId: String? = null,
    ) : ConversationRecord()

    @Immutable
    data class AssistantVoice(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val sessionId: Long,
        val text: String,
        val itemId: String? = null,
        val responseId: String? = null,
        val hasAudio: Boolean = false,
        val audioSegments: Int = 0,
        val audioDurationMs: Long = 0L,
    ) : ConversationRecord()

    @Immutable
    data class FunctionCall(
        override val id: String = UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        override val sessionId: Long,
        val name: String,
        val arguments: String,
        val callId: String? = null,
        val itemId: String? = null,
        val responseId: String? = null,
    ) : ConversationRecord()
}
