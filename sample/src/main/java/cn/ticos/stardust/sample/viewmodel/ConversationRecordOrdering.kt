package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sample.model.ConversationRecord

internal fun ConversationRecord.itemIdOrNull(): String? =
    when (this) {
        is ConversationRecord.UserVoice -> itemId
        is ConversationRecord.UserText -> itemId
        is ConversationRecord.AssistantVoice -> itemId
        is ConversationRecord.FunctionCall -> itemId
    }

internal fun List<ConversationRecord>.withInsertedAfterPreviousItem(
    record: ConversationRecord,
    previousItemId: String?,
    maxRecords: Int,
): List<ConversationRecord> {
    val previousIndex = previousItemId
        ?.takeIf { it.isNotBlank() }
        ?.let { targetItemId -> indexOfFirst { it.itemIdOrNull() == targetItemId } }
        ?: -1

    val inserted = if (previousIndex >= 0) {
        toMutableList().apply {
            add(previousIndex + 1, record)
        }
    } else {
        this + record
    }

    return inserted.takeLast(maxRecords)
}
