package cn.ticos.stardust.sdk.internal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseAudioGateTest {
    @Test
    fun suppressActive_rejectsLateDeltaAndAllowsNextResponse() {
        val gate = ResponseAudioGate()
        gate.onResponseCreated(responseId = "response-1", itemId = "item-1")
        assertTrue(gate.acceptDelta("response-1", "item-1"))

        gate.suppressActive()
        assertFalse(gate.acceptDelta("response-1", "item-1"))

        gate.onResponseCreated(responseId = "response-2", itemId = "item-2")
        assertTrue(gate.acceptDelta("response-2", "item-2"))
        assertFalse(gate.acceptDelta("response-1", "item-1"))
    }

    @Test
    fun suppressWithoutKnownIds_rejectsUntilNextResponseCreated() {
        val gate = ResponseAudioGate()
        gate.suppressActive()

        assertFalse(gate.acceptDelta("late-response", "late-item"))

        gate.onResponseCreated(responseId = "new-response", itemId = null)
        assertTrue(gate.acceptDelta("new-response", "new-item"))
    }

    @Test
    fun rememberedIdsAreBoundedWithoutReallowingMostRecentResponse() {
        val gate = ResponseAudioGate(maxRememberedIds = 2)
        repeat(3) { index ->
            gate.onResponseCreated("response-$index", "item-$index")
            gate.suppressActive()
        }
        gate.onResponseCreated("response-new", "item-new")

        assertFalse(gate.acceptDelta("response-2", "item-2"))
        assertTrue(gate.acceptDelta("response-0", "item-0"))
    }
}
