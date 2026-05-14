package cn.ticos.stardust.sdk.internal

import android.util.Base64
import cn.ticos.stardust.sdk.StardustEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal object EventParser {
    fun parse(raw: String): StardustEvent {
        val root = StardustJson.parseToJsonElement(raw).jsonObject
        val type = root.stringOrNull("type")
        val eventId = root.stringOrNull("event_id")
        return when (type) {
            "error" -> StardustEvent.Error(
                eventId = eventId,
                rawJson = raw,
                errorType = root["error"]?.jsonObject?.stringOrNull("type"),
                message = root["error"]?.jsonObject?.stringOrNull("message"),
                payload = root,
            )

            "session.created" -> StardustEvent.SessionCreated(eventId, raw, root)
            "session.updated" -> StardustEvent.SessionUpdated(eventId, raw, root)
            "conversation.created" -> StardustEvent.ConversationCreated(eventId, raw, root)
            "conversation.item.created" -> StardustEvent.ConversationItemCreated(eventId, raw, root)
            "conversation.item.input_audio_transcription.completed" -> StardustEvent.InputAudioTranscriptionCompleted(eventId, raw, root)
            "conversation.item.input_audio_transcription.failed" -> StardustEvent.InputAudioTranscriptionFailed(eventId, raw, root)
            "input_audio_buffer.committed" -> StardustEvent.InputAudioBufferCommitted(eventId, raw, root)
            "input_audio_buffer.cleared" -> StardustEvent.InputAudioBufferCleared(eventId, raw, root)
            "input_audio_buffer.speech_started" -> StardustEvent.InputAudioBufferSpeechStarted(eventId, raw, root)
            "input_audio_buffer.speech_stopped" -> StardustEvent.InputAudioBufferSpeechStopped(eventId, raw, root)
            "response.created" -> StardustEvent.ResponseCreated(eventId, raw, root)
            "response.done" -> StardustEvent.ResponseDone(eventId, raw, root)
            "response.output_item.added" -> StardustEvent.ResponseOutputItemAdded(eventId, raw, root)
            "response.output_item.done" -> StardustEvent.ResponseOutputItemDone(eventId, raw, root)
            "response.content_part.added" -> StardustEvent.ResponseContentPartAdded(eventId, raw, root)
            "response.content_part.done" -> StardustEvent.ResponseContentPartDone(eventId, raw, root)
            "response.text.delta" -> StardustEvent.ResponseTextDelta(eventId, raw, root)
            "response.text.done" -> StardustEvent.ResponseTextDone(eventId, raw, root)
            "response.audio_transcript.delta" -> StardustEvent.ResponseAudioTranscriptDelta(eventId, raw, root)
            "response.audio_transcript.done" -> StardustEvent.ResponseAudioTranscriptDone(eventId, raw, root)
            "response.audio.delta" -> parseAudioDelta(eventId, raw, root)
            "response.audio.done" -> StardustEvent.ResponseAudioDone(eventId, raw, root)
            "response.function_call_arguments.done" -> StardustEvent.ResponseFunctionCallArgumentsDone(eventId, raw, root)
            "response.video.done" -> StardustEvent.ResponseVideoDone(eventId, raw, root)
            else -> StardustEvent.Unknown(type, eventId, raw, root)
        }
    }

    private fun parseAudioDelta(eventId: String?, raw: String, root: JsonObject): StardustEvent.ResponseAudioDelta {
        val delta = (root["delta"] as? JsonPrimitive)?.contentOrNull
        val decoded = if (delta.isNullOrBlank()) null else Base64.decode(delta, Base64.DEFAULT)
        return StardustEvent.ResponseAudioDelta(
            eventId = eventId,
            rawJson = raw,
            deltaBase64 = delta,
            decodedPcm16 = decoded,
            payload = root,
        )
    }
}
