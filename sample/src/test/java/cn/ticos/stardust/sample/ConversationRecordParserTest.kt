package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sample.model.ConversationRecord
import cn.ticos.stardust.sdk.StardustEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRecordParserTest {

    @Test
    fun conversationRecord_insertAfterPreviousItem_placesLateUserBeforeAssistant() {
        val firstUser = ConversationRecord.UserVoice(
            sessionId = 1L,
            text = "first",
            itemId = "item_user_1",
        )
        val assistant = ConversationRecord.AssistantVoice(
            sessionId = 1L,
            text = "answer",
            itemId = "item_assistant_1",
        )
        val lateUser = ConversationRecord.UserVoice(
            sessionId = 1L,
            text = "late",
            itemId = "item_user_2",
        )

        val ordered = listOf(firstUser, assistant).withInsertedAfterPreviousItem(
            record = lateUser,
            previousItemId = "item_user_1",
            maxRecords = 20,
        )

        assertEquals(
            listOf("item_user_1", "item_user_2", "item_assistant_1"),
            ordered.map { it.itemIdOrNull() },
        )
    }

    @Test
    fun conversationRecord_insertAfterPreviousItem_appendsWhenPreviousMissing() {
        val firstUser = ConversationRecord.UserVoice(
            sessionId = 1L,
            text = "first",
            itemId = "item_user_1",
        )
        val assistant = ConversationRecord.AssistantVoice(
            sessionId = 1L,
            text = "answer",
            itemId = "item_assistant_1",
        )

        val ordered = listOf(firstUser).withInsertedAfterPreviousItem(
            record = assistant,
            previousItemId = "missing_item",
            maxRecords = 20,
        )

        assertEquals(
            listOf("item_user_1", "item_assistant_1"),
            ordered.map { it.itemIdOrNull() },
        )
    }

    @Test
    fun responseOutputItemDone_parsesFunctionCallWithoutRole() {
        val payload = Json.parseToJsonElement(
            """
            {
              "type": "response.output_item.done",
              "event_id": "evt_1",
              "response_id": "resp_1",
              "output_index": 0,
              "item": {
                "id": "item_1",
                "object": "realtime.item",
                "type": "function_call",
                "status": "completed",
                "call_id": "function_id",
                "name": "挥手",
                "arguments": "{}"
              }
            }
            """.trimIndent()
        ).jsonObject

        val record = StardustEvent.ResponseOutputItemDone(
            eventId = "evt_1",
            rawJson = payload.toString(),
            payload = payload,
        ).toFunctionCallRecord(sessionId = 7L)

        assertEquals("evt_1", record?.id)
        assertEquals(7L, record?.sessionId)
        assertEquals("挥手", record?.name)
        assertEquals("{}", record?.arguments)
        assertEquals("function_id", record?.callId)
        assertEquals("item_1", record?.itemId)
        assertEquals("resp_1", record?.responseId)
    }

    @Test
    fun responseFunctionCallArgumentsDone_redactsSensitiveArguments() {
        val payload = Json.parseToJsonElement(
            """
            {
              "type": "response.function_call_arguments.done",
              "event_id": "evt_2",
              "response_id": "resp_2",
              "item_id": "item_2",
              "call_id": "call_2",
              "name": "login",
              "arguments": "{\"password\":\"secret\",\"city\":\"shanghai\"}"
            }
            """.trimIndent()
        ).jsonObject

        val record = StardustEvent.ResponseFunctionCallArgumentsDone(
            eventId = "evt_2",
            rawJson = payload.toString(),
            payload = payload,
        ).toFunctionCallRecord(sessionId = 8L)

        assertTrue(record is ConversationRecord.FunctionCall)
        record as ConversationRecord.FunctionCall
        assertEquals("login", record.name)
        assertEquals("call_2", record.callId)
        assertEquals("item_2", record.itemId)
        assertEquals("""{"password":"***REDACTED***","city":"shanghai"}""", record.arguments)
    }
}
