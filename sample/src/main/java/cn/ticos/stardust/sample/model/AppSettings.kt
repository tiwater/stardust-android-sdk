package cn.ticos.stardust.sample.model

const val DEFAULT_REALTIME_URL = "wss://stardust.ticos.cn/realtime"

/**
 * 与 `stardust/src/websockethandlers/websocket_handler_base.py` 中
 * `Authorization: Bearer X-Tiwater-Debug` 分支一致，用于无终端密钥时以 group_id + robot_id 连接。
 */
const val STARDUST_DEBUG_BEARER_TOKEN = "X-Tiwater-Debug"

enum class SessionConfigMode {
    AgentId,
    Advanced,
}

data class AdvancedSessionSettings(
    val modelProvider: String = "tiwater",
    val modelName: String = "stardust-2.5-max",
    val instructions: String = "你是一个友好的语音助手，请用简洁自然的中文回答。",
    val temperature: Double = 0.7,
    val topP: Double = 1.0,
    val topK: Int = 40,
    val maxResponseOutputTokens: Int = 1024,
    val historyConversationLength: Int = 30,
    val speechVoice: String = "zh_female_wanwanxiaohe_moon_bigtts",
    val speechEmotion: String = "neutral",
    val speechSpeedRatio: Int = 50,
    val speechPitchRatio: Int = 50,
    val speechVolumeRatio: Int = 50,
    val hearingProvider: String = "",
    val hearingPrefixPaddingMs: Int = 300,
    val hearingSilenceDurationMs: Int = 520,
    val hearingSensitivity: Double = 0.03,
)

data class AppSettings(
    val agentId: String = "",
    val serverUrl: String = DEFAULT_REALTIME_URL,
    val terminalSecret: String = "",
    val groupId: String = "",
    val robotId: String = "",
    val autoPlayAudio: Boolean = true,
    val language: String = "en",
    val sessionConfigMode: SessionConfigMode = SessionConfigMode.AgentId,
    val advancedSession: AdvancedSessionSettings = AdvancedSessionSettings(),
)
