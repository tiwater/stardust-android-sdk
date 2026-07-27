package cn.ticos.stardust.sdk.internal

/**
 * Tracks assistant responses whose late audio must no longer reach playback.
 *
 * WebSocket delivery is ordered, but cancellation and server-VAD interruption can leave
 * already-produced audio deltas in flight. The gate is deliberately keyed by the protocol's
 * existing response/item ids, so no chunk sequence field is required.
 */
internal class ResponseAudioGate(
    private val maxRememberedIds: Int = 64,
) {
    private val obsoleteResponseIds = LinkedHashSet<String>()
    private val obsoleteItemIds = LinkedHashSet<String>()

    private var activeResponseId: String? = null
    private var activeItemId: String? = null
    private var suppressUntilNextResponse = false

    @Synchronized
    fun onResponseCreated(responseId: String?, itemId: String?) {
        activeResponseId = responseId
        activeItemId = itemId
        suppressUntilNextResponse = false
    }

    /**
     * Returns true when this delta belongs to the current playable response.
     * Accepted ids also refresh the active response in case response.created omitted the item id.
     */
    @Synchronized
    fun acceptDelta(responseId: String?, itemId: String?): Boolean {
        if (suppressUntilNextResponse ||
            responseId?.let(obsoleteResponseIds::contains) == true ||
            itemId?.let(obsoleteItemIds::contains) == true
        ) {
            return false
        }
        if (responseId != null) activeResponseId = responseId
        if (itemId != null) activeItemId = itemId
        return true
    }

    @Synchronized
    fun isSuppressed(responseId: String?, itemId: String?): Boolean {
        return suppressUntilNextResponse ||
            responseId?.let(obsoleteResponseIds::contains) == true ||
            itemId?.let(obsoleteItemIds::contains) == true
    }

    /** Suppresses both known ids and id-less late deltas until the next response.created. */
    @Synchronized
    fun suppressActive() {
        activeResponseId?.let {
            obsoleteResponseIds.add(it)
            trimOldest(obsoleteResponseIds)
        }
        activeItemId?.let {
            obsoleteItemIds.add(it)
            trimOldest(obsoleteItemIds)
        }
        suppressUntilNextResponse = true
    }

    @Synchronized
    fun onResponseFinished(responseId: String?, itemId: String?) {
        if (responseId == null || responseId == activeResponseId) activeResponseId = null
        if (itemId == null || itemId == activeItemId) activeItemId = null
    }

    @Synchronized
    fun clear() {
        obsoleteResponseIds.clear()
        obsoleteItemIds.clear()
        activeResponseId = null
        activeItemId = null
        suppressUntilNextResponse = false
    }

    private fun trimOldest(ids: LinkedHashSet<String>) {
        while (ids.size > maxRememberedIds) {
            val iterator = ids.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }
}
