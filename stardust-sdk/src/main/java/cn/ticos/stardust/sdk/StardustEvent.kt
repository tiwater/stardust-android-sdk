package cn.ticos.stardust.sdk

import kotlinx.serialization.json.JsonObject

sealed class StardustEvent(
    open val eventId: String?,
    open val rawJson: String,
) {
    data class Error(
        override val eventId: String?,
        override val rawJson: String,
        val errorType: String?,
        val message: String?,
        val payload: JsonObject,
    ) : StardustEvent(eventId, rawJson)

    data class SessionCreated(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class SessionUpdated(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ConversationCreated(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ConversationItemCreated(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioTranscriptionCompleted(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioTranscriptionFailed(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioBufferCommitted(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioBufferCleared(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioBufferSpeechStarted(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class InputAudioBufferSpeechStopped(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseCreated(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseOutputItemAdded(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseOutputItemDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseContentPartAdded(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseContentPartDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseTextDelta(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseTextDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseAudioTranscriptDelta(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseAudioTranscriptDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)

    data class ResponseAudioDelta(
        override val eventId: String?,
        override val rawJson: String,
        val deltaBase64: String?,
        val decodedPcm16: ByteArray?,
        val payload: JsonObject,
    ) : StardustEvent(eventId, rawJson)

    data class ResponseAudioDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseFunctionCallArgumentsDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)
    data class ResponseVideoDone(override val eventId: String?, override val rawJson: String, val payload: JsonObject) : StardustEvent(eventId, rawJson)

    data class Unknown(
        val type: String?,
        override val eventId: String?,
        override val rawJson: String,
        val payload: JsonObject,
    ) : StardustEvent(eventId, rawJson)
}
