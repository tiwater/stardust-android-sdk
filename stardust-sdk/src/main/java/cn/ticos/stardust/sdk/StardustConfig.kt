package cn.ticos.stardust.sdk

data class StardustConfig(
    val realtimeUrl: String = "wss://stardust.ticos.cn/realtime",
    val videoUrl: String = "wss://stardust.ticos.cn/video",
    /** 返回空字符串时可不发送 Authorization 头，仅依赖 [terminalSecretProvider] 的 `terminal_secret` 查询参数（与 Stardust 服务端约定一致）。 */
    val tokenProvider: suspend () -> String,
    val terminalSecretProvider: (suspend () -> String)? = null,
    val queryParams: Map<String, String> = emptyMap(),
    val connectTimeoutMs: Long = 10_000,
    val writeTimeoutMs: Long = 10_000,
    val readTimeoutMs: Long = 0,
    val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    val logLevel: StardustLogLevel = StardustLogLevel.INFO,
    val autoPlayAudio: Boolean = true,
    val autoReconnectRealtime: Boolean = true,
    val autoReconnectVideo: Boolean = false,
    val videoConnectPolicy: VideoConnectPolicy = VideoConnectPolicy.RequireSessionCreated,
    /** `/video` 通道发送 JPEG 的最大帧率（1–30），与 Sample 视觉帧率选择对齐。 */
    val videoMaxFps: Int = 2,
)

data class ReconnectPolicy(
    val enabled: Boolean = true,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 10_000,
    val maxAttempts: Int = 5,
    val maxElapsedMs: Long? = null,
    val resendLastSessionUpdate: Boolean = true,
)

enum class VideoConnectPolicy {
    RequireSessionCreated,
    RequireSessionUpdated,
}

enum class StardustLogLevel {
    NONE,
    ERROR,
    WARN,
    INFO,
    DEBUG,
}
