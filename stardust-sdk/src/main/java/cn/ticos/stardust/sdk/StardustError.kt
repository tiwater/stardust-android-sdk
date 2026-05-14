package cn.ticos.stardust.sdk

data class StardustSdkError(
    val code: StardustErrorCode,
    val message: String,
    val cause: Throwable? = null,
    val rawEvent: String? = null,
    val recoverable: Boolean,
)

enum class StardustErrorCode {
    AUTH_FAILED,
    NETWORK_UNAVAILABLE,
    WEBSOCKET_CONNECT_FAILED,
    WEBSOCKET_CLOSED,
    SESSION_UPDATE_FAILED,
    AUDIO_RECORD_FAILED,
    AUDIO_RECORD_UNSUPPORTED_FORMAT,
    AUDIO_PLAYBACK_FAILED,
    JSON_PARSE_FAILED,
    UNSUPPORTED_EVENT,
    VIDEO_REALTIME_NOT_READY,
    VIDEO_NOT_CONNECTED,
    VIDEO_FRAME_ENCODE_FAILED,
    VIDEO_PACKETIZE_FAILED,
    SERVER_ERROR,
}

class StardustSdkException(val sdkError: StardustSdkError) : RuntimeException(sdkError.message, sdkError.cause)
