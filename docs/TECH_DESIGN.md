# Stardust Android SDK 技术设计

## 1. 文档目的

本文档把 `PRD.md` 与 `SRS.md` 的新基线需求落地为 Android SDK 与 Sample App 的工程实现方案，明确工程结构、技术选型、模块边界、关键行为、数据流、测试策略和分阶段交付计划。

功能范围、协议细节和验收标准以 `PRD.md`、`SRS.md` 以及 Stardust 服务端协议文档为准。本文聚焦实现路径与设计决策。

## 2. 设计目标

1. 提供 Kotlin-first、线程安全、Android 友好的 Stardust Realtime SDK。
2. 屏蔽 WebSocket 生命周期、鉴权、JSON 事件拼装、事件解析、PCM base64、音频播放中断、采集数据输出和视频帧封包细节。
3. 保持协议模型可扩展，服务端新增字段或事件时 SDK 不因未知字段崩溃。
4. 建立采集/播放双音频状态流，移除信息有损的聚合 `AudioState`。
5. Sample App 作为稳定演示与联调入口，覆盖复杂 Session 配置、PTT、Vision + Voice、对话信息页和统一 UI。
6. SDK 不绑定 Activity、Fragment、Compose 或特定 UI 生命周期；Sample 层负责权限、页面和业务组合。

## 3. 工程形态

### 3.1 仓库结构

`android-sdk` 作为独立 Android SDK 工程建设，不依赖仓库根目录已有 Gradle 工程。

目标目录结构：

```text
android-sdk/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
  README.md
  docs/
    PRD.md
    SRS.md
    TECH_DESIGN.md
  stardust-sdk/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/cn/ticos/stardust/sdk/
    src/test/java/cn/ticos/stardust/sdk/
    src/androidTest/java/cn/ticos/stardust/sdk/
  sample/
    build.gradle.kts
    src/main/
```

### 3.2 Gradle 与 Android 配置

建议配置：

- SDK module：`com.android.library`。
- Sample module：`com.android.application`。
- `minSdk = 26`。
- Java/Kotlin target 优先使用 17；如工具链不支持则退回 11。
- Kotlin 使用当前稳定版本。
- WebSocket 使用 OkHttp。
- JSON 使用 kotlinx.serialization。
- Sample UI 使用 Compose 与 Material3。

### 3.3 Maven 坐标

预留：

- groupId：`cn.ticos.stardust`
- artifactId：`stardust-android-sdk`
- package：`cn.ticos.stardust.sdk`

Maven 发布自动化属于后续稳定阶段，不阻塞协议核心与 Sample 验证。

## 4. 技术选型

### 4.1 WebSocket

使用 OkHttp WebSocket。

原因：

- Android 生态成熟，依赖轻量。
- 支持自定义 Header，便于设置 `Authorization: Bearer <token>`。
- 支持子协议 Header，便于 Realtime 使用 `Sec-WebSocket-Protocol: realtime`。
- MockWebServer 可用于集成测试。

### 4.2 异步模型

使用 Kotlin Coroutines 与 Flow。

对外 API：

- 状态使用 `StateFlow`。
- 事件、错误、本地采集 PCM 使用 `SharedFlow`。
- 发送动作使用 `suspend fun`。

内部约束：

- Public API 可从主线程调用。
- 网络、JSON、音频、视频处理不得阻塞主线程。
- SDK 内部持有自己的 `CoroutineScope`，`close()` 必须取消 scope 并释放资源。
- WebSocket 写操作通过统一发送队列串行化，避免并发写导致时序不可控。

### 4.3 JSON

使用 `kotlinx.serialization`。

配置建议：

```kotlin
Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}
```

实现注意：

- `unknownFields` 在序列化时合并到同级 JSON object。
- 强类型字段优先级高于 `unknownFields`。
- 协议需要显式 null 的字段单独建模，例如 `hearing.turn_detection = null`。
- 日志层必须在 JSON 输出前或输出时脱敏敏感字段。

### 4.4 音频

使用 Android 原生 `AudioRecord` 与 `AudioTrack`。

策略：

- 采集目标格式：PCM16、24kHz、mono。
- 默认采集帧：40ms，1920 bytes。
- 设备不支持 24kHz 时，优先重采样；未实现重采样时返回 `AUDIO_RECORD_UNSUPPORTED_FORMAT`。
- `AudioTrack` 播放 24kHz mono PCM16。
- `input_audio_buffer.speech_started` 与 `cancelResponse()` 都触发本地播放 stop/flush/clear。
- 采集侧同时向 WebSocket 发送 PCM，并通过 `capturedPcm` / `captureLevel` 向业务输出。

### 4.5 视频

使用 OkHttp WebSocket 发送二进制消息。

策略：

- SDK 提供 `sendJpegFrame(jpeg: ByteArray)` 与 packetizer。
- SDK 内置发送帧率上限，默认 2 FPS。
- Sample 使用 CameraX 获取帧并编码 JPEG。
- CameraX helper 首版放在 Sample 层；稳定后可下沉为 SDK 可选 helper。

### 4.6 Sample UI

Sample 使用 Compose + Material3。

设计策略：

- 统一 TopAppBar、Filled 输入框、卡片分组、间距 Token 和语义色 Token。
- 主 Orb 保持连接/断开职责，不复用为 PTT 采音。
- PTT、Vision、Session 配置模式都通过 ViewModel 聚合状态驱动 UI，避免 Composable 内散落业务条件。
- 用户可见字符串统一进入资源文件。

## 5. SDK 模块设计

首版使用单一 Android library module，内部按 package 划分。API 稳定后再考虑拆分多 module。

```text
cn.ticos.stardust.sdk
  core/
  realtime/
  model/
  audio/
  video/
  diagnostics/
  internal/
  testkit/
```

职责：

- `core`：`StardustClient`、配置、生命周期、统一状态、错误分发。
- `realtime`：Realtime WebSocket、鉴权、子协议、上行 writer、下行 reader。
- `model`：Session、Response、Tool、Conversation、Event 等协议模型。
- `audio`：PCM base64、AudioRecord、AudioTrack、播放队列、采集 PCM/RMS 输出。
- `video`：Video WebSocket、JPEG packetizer、帧率限制、连接顺序校验。
- `diagnostics`：日志脱敏、指标、诊断快照。
- `internal`：内部工具类，不作为 public API 承诺。
- `testkit`：Mock server、协议断言、假音频设备。首版可先放 test source set。

## 6. Public API 设计

Public API 以 `SRS.md` 第 6 章为准，核心对象包括：

- `StardustConfig`
- `StardustClient`
- `StardustAudio`
- `StardustVideo`
- `SessionConfig`
- `ModelConfig`
- `SpeechConfig`
- `HearingConfig`
- `VisionConfig`
- `ResponseConfig`
- `ToolConfig`
- `StardustEvent`
- `StardustSdkError`
- `StardustDiagnostics`

### 6.1 客户端行为

`connect()`：

- 建立 Realtime WebSocket。
- 调用 token provider。
- 设置 `Authorization: Bearer <token>`。
- 设置 Realtime 子协议 `realtime`。
- 连接成功后进入 `Connected`。
- 收到 `session.created` 后进入 `SessionCreated`。
- 不自动发送默认 `session.update`。

`updateSession(session)`：

- 若 Realtime 已达到 `Connected`，立即发送。
- 若处于 `Connecting`，默认挂起等待到 `Connected` 或连接失败。
- 若处于 `Idle`、`Closing`、`Closed`、`Failed`，返回明确错误。
- 记录最近一次 session，用于重连后可选重发。
- 收到 `session.updated` 后进入 `SessionUpdated`。

`sendText()` / `sendImage()` / `sendMultimodalMessage()`：

- 发送 `conversation.item.create`。
- 当 `previousItemId != null` 时写入顶层 `previous_item_id`。
- 不自动调用 `response.create`。

`createResponse()`：

- 默认使用 `ResponseConfig.audio()`。
- 发送 `response.create`。

`cancelResponse()`：

- 先调用 `audio.stopPlayback(clearQueue = true)`。
- 再发送 `response.cancel`。

`sendRawEvent(json)`：

- 原样发送调用方 JSON。
- 日志层脱敏音频、图片、视频、token、API key、MCP authorization 等字段。

`close()`：

- 关闭 Realtime、Video、AudioRecord、AudioTrack。
- 取消内部 coroutine scope。
- 关闭后 public API 再次调用应返回明确错误；如需复用，业务创建新的 client。

### 6.2 Token 获取

`tokenProvider` 类型为 `suspend () -> String`。

行为：

- 每次建立 Realtime 或 Video WebSocket 前调用。
- 401 不自动重连。
- 401 映射为 `AUTH_FAILED`，交给业务刷新 token 后重新连接。

兼容行为：

- 如配置 `terminalSecretProvider`，SDK 允许使用 `terminal_secret=<token>` query 参数。
- 默认优先使用 Authorization Header。

## 7. 状态机设计

### 7.1 Realtime 状态

状态：

- `Idle`
- `Connecting`
- `Connected`
- `SessionCreated`
- `SessionUpdated`
- `Reconnecting`
- `Closing`
- `Closed`
- `Failed`

关键规则：

- `connect()` 只允许从 `Idle`、`Closed` 或可恢复失败状态进入 `Connecting`。
- `session.created` 只在 WebSocket 已打开后有效。
- `session.updated` 表示最近一次 `session.update` 生效。
- 不可恢复错误进入 `Failed`。
- `close()` 可从任意非终态进入 `Closing`，最终进入 `Closed`。

### 7.2 音频状态

SDK 不再维护聚合 `AudioState`。

采集状态：

- `Idle`
- `Recording`
- `Stopping`

播放状态：

- `Idle`
- `Playing`
- `Failed`

设计原因：

- 全双工时可能同时 `Recording` 与 `Playing`。
- 聚合状态会丢失采集或播放任一侧信息。
- Sample 的 `VoicePhaseMapping` 在展示层自行决定优先级，例如播放优先于录音。

### 7.3 Video 状态

状态：

- `Idle`
- `WaitingRealtime`
- `Connecting`
- `Connected`
- `Disconnecting`
- `Disconnected`
- `Failed`

关键规则：

- `video.connect()` 默认要求 Realtime 至少达到 `SessionCreated`。
- `RequireSessionUpdated` 时必须等 `SessionUpdated`。
- Realtime 未就绪时返回或派发 `VIDEO_REALTIME_NOT_READY`，不得静默丢帧。
- Realtime 重连后，Video 不自动发帧，必须等待 Realtime 再次达到指定状态。

## 8. 协议实现

### 8.1 Realtime 上行 writer

统一 writer 把 public API 映射为上行 JSON：

- `updateSession()` -> `session.update`
- `audio.appendPcm()` -> `input_audio_buffer.append`
- `audio.commit()` -> `input_audio_buffer.commit`
- `audio.clear()` -> `input_audio_buffer.clear`
- `sendText()` / `sendImage()` / `sendMultimodalMessage()` -> `conversation.item.create`
- `createResponse()` -> `response.create`
- `cancelResponse()` -> `response.cancel`
- `sendRawEvent()` -> 原始 JSON

所有 writer 通过统一发送队列串行化。

### 8.2 Realtime 下行 parser

parser 支持 SRS 列出的已知事件，并把未知事件解析为 `StardustEvent.Unknown`。

解析要求：

- 所有事件保留 raw JSON。
- 已知事件解析失败时不崩溃，派发 `JSON_PARSE_FAILED`。
- 服务端 `error` 同时派发到 `events` 和 `errors`。
- 占位事件或未来事件进入 `Unknown`。
- `response.audio.delta` 可懒解码 decoded PCM，避免无订阅者时额外分配。

### 8.3 SessionConfig 序列化

核心要求：

- 支持最小 Agent ID 配置：`{"agent_id":"..."}`。
- Kotlin 字段 camelCase，对外 JSON snake_case。
- null 字段默认不序列化。
- `hearing.turn_detection = null` 等显式 null 字段必须保留。
- `unknownFields` 原样合并。
- ToolConfig 完整支持 `function`、`mcp`、`ticos_mcp`。
- `mcp` / `ticos_mcp` 鉴权字段参与日志脱敏。

### 8.4 Video packetizer

`sendJpegFrame(jpeg)` 生成：

```text
Byte 0      0x54
Byte 1      0x20
Byte 2-5    seq_id UInt32 Little Endian
Byte 6-9    msg_length UInt32 Little Endian
Byte 10..   jpeg payload
Last byte   padding 0x00
```

规则：

- `msg_length` 等于真实 JPEG payload 长度，不包含 header 和 padding。
- 服务端按 `message[10:-1]` 读取后必须得到原始 JPEG。
- `seq_id` 建议从 0 开始，必须单调递增。
- 默认帧率限制 2 FPS。
- 超过帧率的帧可丢弃并计入 diagnostics。

## 9. 音频实现设计

### 9.1 DefaultStardustAudio 结构

核心成员：

```kotlin
private val _captureState = MutableStateFlow(CaptureAudioState.Idle)
private val _playbackState = MutableStateFlow(PlaybackAudioState.Idle)
private val _capturedPcm = MutableSharedFlow<ByteArray>(
    extraBufferCapacity = 64,
    onBufferOverflow = BufferOverflow.DROP_OLDEST,
)
private val _captureLevel = MutableStateFlow(0f)
```

不再包含：

- `_state: MutableStateFlow<AudioState>`
- `publishAggregateLegacyState()`
- `AudioState` 枚举

### 9.2 采集循环

采集协程运行在 `Dispatchers.IO`：

```kotlin
captureJob = scope.launch(Dispatchers.IO) {
    val buffer = ByteArray(1920)
    while (isActive && captureActive.get()) {
        val read = recorder.read(buffer, 0, buffer.size)
        if (read > 0) {
            val frame = buffer.copyOf(read)
            _captureLevel.value = computeRmsLevel(frame)
            _capturedPcm.tryEmit(frame)
            appendPcm(frame)
        }
    }
}
```

设计要点：

- 使用 `tryEmit`，慢消费者不阻塞采集线程。
- `capturedPcm` 与实际上行帧保持一致。
- `stopCapture()` 释放 AudioRecord 后将 `captureLevel` 置 `0f`。
- PTT 间隙调用 `stopCapture()` 释放麦克风。

### 9.3 RMS 计算

PCM16 little-endian 解码为 signed sample 后计算 RMS：

```kotlin
private fun computeRmsLevel(pcm16: ByteArray): Float {
    if (pcm16.size < 2) return 0f
    val sampleCount = pcm16.size / 2
    var sumSquares = 0L
    for (i in 0 until pcm16.size step 2) {
        val low = pcm16[i].toInt() and 0xFF
        val high = pcm16[i + 1].toInt()
        val signed = (low or (high shl 8)).toShort().toInt()
        val s = signed.toLong()
        sumSquares += s * s
    }
    val rms = kotlin.math.sqrt(sumSquares.toDouble() / sampleCount)
    return (rms / 32768.0).toFloat().coerceIn(0f, 1f)
}
```

### 9.4 播放队列

实现策略：

- `response.audio.delta` 解码后进入播放队列。
- 单独 playback coroutine 顺序消费队列并写入 AudioTrack。
- `input_audio_buffer.speech_started`、`cancelResponse()`、`stopPlayback(clearQueue=true)` 都触发 stop/flush/clear。
- AudioTrack 被 stop 后，下次可播放音频到达时重新调用 `play()`。
- `response.audio.done` 标记当前 response 音频完成，不清空后续 response。

## 10. 错误、重连、日志与诊断

### 10.1 错误分发

- SDK 内部错误通过 `errors` Flow 派发。
- 协议 `error` 事件通过 `events` 派发为 `StardustEvent.Error`，同时转换为 `StardustSdkError` 派发到 `errors`。
- JSON 解析失败不得导致 SDK 崩溃。
- 401 鉴权失败不得自动重连。
- 音频和视频错误必须有明确错误码。
- Video 错误默认不关闭 Realtime。

### 10.2 重连策略

建议模型：

```kotlin
data class ReconnectPolicy(
    val enabled: Boolean = true,
    val initialDelayMs: Long = 500,
    val maxDelayMs: Long = 10_000,
    val maxAttempts: Int = 5,
    val maxElapsedMs: Long? = null,
    val resendLastSessionUpdate: Boolean = true
)
```

规则：

- 仅可恢复网络错误参与重连。
- 401、配置错误、协议不可恢复错误不参与自动重连。
- 重连期间不得继续采集并发送音频。
- 重连成功后可按配置重发最近一次 `session.update`。
- Realtime 重连后，Video 必须等待 Realtime 再次达到就绪状态。

### 10.3 日志脱敏

脱敏字段包含：

- `Authorization`
- `terminal_secret`
- `token`
- `secret`
- `password`
- `api_key`
- `mcp_api_key`
- `authorization`
- 音频 base64
- 图片 base64
- 视频 payload

Debug 日志只打印事件类型、id、payload size、错误码和状态变化。

### 10.4 诊断快照

`StardustDiagnostics` 使用线程安全数据结构维护，允许任意线程读取。

字段包括：

- Realtime 状态。
- Video 状态。
- CaptureAudio 状态。
- PlaybackAudio 状态。
- 连接次数、重连次数。
- 最近连接耗时。
- 已发送/接收音频字节数。
- 采集 PCM 帧数与当前 captureLevel。
- 已发送/丢弃视频帧数。
- 最近错误码。

## 11. Sample App 架构设计

### 11.1 总体结构

```text
sample/
  data/
    AppSettings
    SettingsRepository
    TtsApiClient
  voice/
    VoiceViewModel
    VoiceUiState
    VoicePhaseMapping
  session/
    ComplexSessionConfigScreen
    AdvancedSessionSettingsMapper
  vision/
    CameraFrameCapture
    VisionResultParser
    VisionResultItem
  conversationinfo/
    ConversationRecord
    ConversationInfoScreen
  ui/
    MainScreen
    SettingsScreen
    components/
    theme/
```

### 11.2 VoiceViewModel 职责

`VoiceViewModel` 是 Sample 语音、PTT、Vision、对话信息的统一状态持有者。

职责：

- 读取 `AppSettings`。
- 构建 `StardustConfig` 与 `SessionConfig`。
- 管理 SDK client 生命周期。
- 订阅 SDK state、audio state、captureLevel、events、errors。
- 管理 PTT 状态与手势。
- 管理 Vision pipeline。
- 生成对话信息记录。
- 聚合 `VoiceUiState`。

原则：

- 不为对话信息页创建第二个 SDK client。
- PTT、Vision 模式在连接时快照，会话期间不可变。
- 断开时释放音频、视频和 CameraX 资源。

## 12. Sample 配置中心设计

### 12.1 AppSettings 与 DataStore

新增字段：

```kotlin
enum class SessionConfigMode { AgentId, Advanced }

data class AdvancedSessionSettings(...)

data class AppSettings(
    val serverUrl: String,
    val terminalSecret: String,
    val groupId: String,
    val robotId: String,
    val agentId: String,
    val autoPlayAudio: Boolean,
    val sessionConfigMode: SessionConfigMode = SessionConfigMode.AgentId,
    val advancedSession: AdvancedSessionSettings = AdvancedSessionSettings(),
)
```

DataStore 策略：

- 可将复杂配置拆成多个 key，便于单字段更新。
- 也可整体 JSON 保存，但需保留版本号用于迁移。
- 老版本缺少字段时使用默认值。
- 切换模式不删除另一模式数据。

### 12.2 SessionConfig 构建

```kotlin
val session = when (settings.sessionConfigMode) {
    SessionConfigMode.AgentId -> SessionConfig(
        agentId = settings.agentId.takeIf { it.isNotBlank() },
    )
    SessionConfigMode.Advanced -> settings.advancedSession.toSessionConfig()
}
```

复杂配置转换保证：

- `agentId == null`。
- `model.modalities = ["text", "audio"]`。
- `speech.output_audio_format = "pcm16"`。
- `hearing.input_audio_format = "pcm16"`。
- 如果 PTT 同时启用，还需合并 `hearing.turn_detection = null`。

### 12.3 TTS API Client

使用 OkHttp 或项目现有网络层实现轻量 client。

接口：

```kotlin
class TtsApiClient(
    private val okHttpClient: OkHttpClient,
    private val tokenProvider: suspend () -> String,
) {
    suspend fun listSpeakers(query: TtsSpeakerQuery): TtsSpeakerPage
}
```

baseUrl 派生：

- 优先从设置中的 server/realtime URL 派生 HTTP baseUrl。
- 需要明确 `ws/wss` 到 `http/https` 的转换规则。
- 请求 Header 复用 token 或 terminal secret 规则，但日志不得输出明文。

失败策略：

- 展示错误提示。
- 允许手动输入 voice ID。
- 不阻止保存其他复杂配置。

## 13. Sample PTT 设计

### 13.1 状态字段

```kotlin
private val _pttModeEnabled = MutableStateFlow(false)
private var _pttSessionActive = false
private val _pttPressed = MutableStateFlow(false)
private var _pttDownTimestamp: Long = 0L
private var _pttCancelFired: Boolean = false
private var _pttThresholdJob: Job? = null
```

常量：

```kotlin
const val FALSE_TOUCH_MS = 250L
```

### 13.2 连接流程

```kotlin
_pttSessionActive = _pttModeEnabled.value

val sessionConfig = buildSessionConfig(settings).let { base ->
    if (_pttSessionActive) {
        base.withHearingTurnDetectionNull()
    } else {
        base
    }
}

client.connect()
client.updateSession(sessionConfig)

if (!_pttSessionActive) {
    client.audio.startCapture()
}
```

设计要点：

- PTT 模式不自动采集。
- Server VAD 模式保持原有自动采集。
- `hearing.turn_detection = null` 需与 Agent ID 或复杂配置的 `hearing` 合并，不能覆盖掉复杂配置中的 `input_audio_format`。

### 13.3 手势处理

按下：

```kotlin
fun pttPressDown() {
    if (!_pttSessionActive || _pttPressed.value) return
    val client = _client.value ?: return

    _pttPressed.value = true
    _pttDownTimestamp = SystemClock.elapsedRealtime()
    _pttCancelFired = false

    viewModelScope.launch { client.audio.startCapture() }

    _pttThresholdJob?.cancel()
    _pttThresholdJob = viewModelScope.launch {
        delay(FALSE_TOUCH_MS)
        if (_pttPressed.value && !_pttCancelFired) {
            _pttCancelFired = true
            runCatching { client.cancelResponse() }
        }
    }
}
```

松开：

```kotlin
fun pttPressUp() {
    if (!_pttSessionActive) return
    val client = _client.value ?: return

    _pttPressed.value = false
    _pttThresholdJob?.cancel()

    val elapsed = SystemClock.elapsedRealtime() - _pttDownTimestamp
    viewModelScope.launch {
        if (elapsed >= FALSE_TOUCH_MS) {
            if (!_pttCancelFired) {
                _pttCancelFired = true
                runCatching { client.cancelResponse() }
            }
            client.audio.stopCapture(commit = true)
            client.createResponse()
        } else {
            client.audio.stopCapture(commit = false)
            client.audio.clear()
        }
    }
}
```

断开：

- 取消 `_pttThresholdJob`。
- `_pttPressed = false`。
- `stopCapture(commit = false)`。
- 不强制 commit 未完成片段。

### 13.4 UI 组件

组件：

- `PttModeCheckbox`：仅未连接时显示。
- `PttButton`：仅 `pttSessionActive && isConnected` 时显示。

主 Orb：

- 只负责连接/断开。
- 不承担 PTT 按下/抬起语义。

PTT Button：

- 使用 `pointerInput`、`awaitFirstDown`、`waitForUpOrCancellation`。
- Cancel 事件等同松开。
- 按下期间展示明确视觉反馈，可联动 `audioLevel`。

## 14. Sample Vision + Voice 设计

### 14.1 状态字段

```kotlin
private val _visionModeEnabled = MutableStateFlow(false)
private var _visionSessionActive = false
private val _visionResults = MutableStateFlow<List<VisionResultItem>>(emptyList())
private val _visionFps = MutableStateFlow(2)
private val _visionStreaming = MutableStateFlow(false)
private var _visionCaptureJob: Job? = null
private var _cameraFrameCapture: CameraFrameCapture? = null
```

常量：

```kotlin
const val DEFAULT_VISION_FPS = 2
const val MAX_VISION_RESULTS = 10
val ALLOWED_FPS_OPTIONS = listOf(1, 2, 5)
```

### 14.2 连接流程

```kotlin
_visionSessionActive = _visionModeEnabled.value

client.connect()
client.updateSession(sessionConfig)

if (!_pttSessionActive) {
    client.audio.startCapture()
}

if (_visionSessionActive) {
    startVisionPipeline(client)
}
```

视觉不强制额外写入 `session.vision.enable_*` 字段。需要显式控制时，通过 `VisionConfig.unknownFields` 透传。

### 14.3 视觉管线

```kotlin
private suspend fun startVisionPipeline(client: StardustClient) {
    try {
        client.video.connect()

        val capture = CameraFrameCapture()
        _cameraFrameCapture = capture
        capture.start(context, _visionFps.value)
        _visionStreaming.value = true

        _visionCaptureJob = viewModelScope.launch(Dispatchers.IO) {
            capture.frames.collect { jpegBytes ->
                try {
                    client.video.sendJpegFrame(jpegBytes)
                } catch (t: Throwable) {
                    handleVisionSendError(t)
                }
            }
        }
    } catch (t: Throwable) {
        showTransientError("[vision] ${t.message ?: "Video connect failed"}")
        stopVisionPipeline()
    }
}
```

停止：

```kotlin
private fun stopVisionPipeline() {
    _visionCaptureJob?.cancel()
    _visionCaptureJob = null
    _cameraFrameCapture?.stop()
    _cameraFrameCapture = null
    _visionStreaming.value = false
    viewModelScope.launch { runCatching { _client.value?.video?.disconnect() } }
}
```

错误处理：

- `video.connect()` 失败：提示并降级纯语音。
- `sendJpegFrame()` 失败：停止视觉管线，保留 Realtime 语音。
- `VideoState.Failed`：提示、停止采集与发送。
- App 后台：停止 CameraX 和帧发送。

### 14.4 CameraFrameCapture

职责：

- 使用 CameraX `ImageAnalysis` 获取帧。
- 后台线程编码 JPEG。
- 通过 `SharedFlow<ByteArray>` 输出 JPEG bytes。
- 使用 `DROP_OLDEST` 避免堆积。
- 支持 fps 更新或重启。

建议结构：

```kotlin
class CameraFrameCapture {
    private val _frames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    fun start(context: Context, fps: Int)
    fun stop()
    fun updateFps(fps: Int)
}
```

帧率双层控制：

- Sample 层控制 CameraX 分析/编码频率。
- SDK 层 `DefaultStardustVideo` 用 `maxFps` 兜底。

### 14.5 VisionResultParser

从 `StardustEvent.ResponseVideoDone` 中解析：

- `face_info.faces[]`
- `object_info.objects[]`
- `hand_info.hands[]`
- `image_info.description`
- `image_info.labels[]`

要求：

- 字段缺失返回 null 或空列表。
- 非法结构不得崩溃。
- raw JSON 可保留用于调试，但 UI 展示需避免敏感内容。
- `_visionResults` 使用 `takeLast(MAX_VISION_RESULTS)`。

### 14.6 UI 组件

组件：

- `VisionModeCheckbox`：仅未连接时显示。
- `VisionFpsSelector`：仅未连接且视觉启用时显示，选项 1/2/5。
- `VisionResultPanel`：视觉会话中显示最近结果。

权限：

- Orb 点击连接前检查 `RECORD_AUDIO`。
- 若 `visionModeEnabled`，同时检查 `CAMERA`。
- 任一权限缺失时单独提示，不混淆错误来源。

生命周期：

- `ON_STOP` 停止视觉采集。
- `ON_START` 如会话仍有效且 Video 连接正常，可恢复采集。
- `disconnect` 和 `onCleared` 必须释放视觉资源。

## 15. Sample 对话信息页设计

### 15.1 数据模型

```kotlin
sealed interface ConversationRecord {
    val id: String
    val timestamp: Long
    val rawSummary: String?
}

data class UserVoiceRecord(...)
data class UserTextRecord(...)
data class AssistantVoiceRecord(...)
data class FunctionCallRecord(...)
```

建议字段：

- `id`
- `timestamp`
- `text`
- `audioChunkCount`
- `audioByteCount`
- `durationMs`
- `functionName`
- `arguments`
- `callId`
- `responseId`
- `itemId`
- `eventType`
- `rawSummary`

### 15.2 状态管理

`VoiceViewModel` 维护：

```kotlin
private val _conversationRecords = MutableStateFlow<List<ConversationRecord>>(emptyList())
private var conversationRoundId: Long = 0L
private val seenConversationEventKeys = mutableSetOf<String>()
```

规则：

- `connectInternal()` 开始新对话时递增 `conversationRoundId` 并清空记录。
- `disconnectSuspend()`、连接失败、`onCleared()` 不清空记录。
- 事件处理前校验 round id，旧 client 延迟事件不得写入新列表。
- 列表超过 100 条时丢弃最旧记录。

### 15.3 事件采集

用户语音：

- 优先使用 `ConversationItemCreated` 中 `role=user` 且内容来自 ASR 的文本。
- 可结合 `InputAudioTranscriptionCompleted` 更新已有记录。
- PTT 短按误触且未 commit 不生成有效用户语音记录。

用户文本：

- 当前首版可无文本输入 UI。
- 如果收到用户文本事件，应生成 `UserText`，不能误归为 `UserVoice`。

助手语音：

- 使用助手文本、`ResponseAudioDelta`、`ResponseAudioDone` 聚合。
- 优先按 `response_id`、`item_id` 关联。
- 无文本时创建占位，后续补全。

FunctionCall：

- 优先使用强类型 `ResponseOutputItemAdded` / `ResponseFunctionCallArgumentsDone`。
- 兜底从 raw JSON 中查找 function/tool 相关字段。
- 参数非法 JSON 时展示原始字符串。
- 敏感字段脱敏。

### 15.4 UI 页面

`ConversationInfoScreen`：

- TopAppBar + 返回按钮。
- 概要卡片：总数、用户语音、用户文本、助手语音、function 数量。
- 过滤器可选：全部 / 用户 / 助手 / Function。
- 时间线列表：按时间升序或最新在下方。
- 长文本、长 JSON 支持折叠/展开。
- 空态：未开始对话或当前无记录。

## 16. Sample UI 统一设计

### 16.1 主题 Token

建议扩展：

- `AppColors`：背景、表面、主色、警告、错误、弱文本、边框、卡片阴影。
- `AppSpacing`：4、8、12、16、24、32。
- `AppShapes`：卡片圆角、按钮圆角、BottomSheet 圆角。

深色模式：

- 使用语义色，不直接硬编码浅色灰阶。
- Snackbar、输入框、卡片、Chip 均需适配。

### 16.2 页面规范

SettingsScreen：

- 使用 TopAppBar。
- 连接配置、Session 配置模式、安全提示分组为卡片。
- Terminal Secret 使用隐藏输入。
- 复杂配置模式显示摘要卡片和编辑按钮。

ComplexSessionConfigScreen：

- TopAppBar 返回。
- 保存、恢复默认值等操作区清晰固定。
- 模型、语音、听觉分组卡片。
- Slider 与数字输入范围校验即时反馈。

MainScreen：

- Orb 居中且只负责连接/断开。
- 未连接时显示模式选择区：PTT、Vision、FPS。
- 已连接时显示 PTT 按钮或 Vision 结果面板。
- 顶部提供设置与对话信息入口。

## 17. 并发与资源管理

### 17.1 SDK 并发

- WebSocket 写队列串行化。
- Event parser 在后台 dispatcher 执行。
- Flow emit 不阻塞主线程。
- AudioRecord 和 AudioTrack 各自独立 coroutine/job。
- Video send 受状态锁或 mutex 保护，避免 close/send 竞态。
- `close()` 幂等。

### 17.2 Sample 并发

- `VoiceViewModel` 只维护一个 active client。
- 新连接前取消旧 observe job。
- PTT 阈值 Job 断开时必须取消。
- Vision capture job 断开、后台、失败时必须取消。
- 对话信息事件使用 round id 隔离旧事件。

### 17.3 生命周期释放

`close()` / `disconnectSuspend()` / `onCleared()` 必须释放：

- Realtime WebSocket。
- Video WebSocket。
- AudioRecord。
- AudioTrack。
- CameraX binding。
- Capture / playback / vision / observe coroutines。

## 18. 测试策略

### 18.1 SDK 单元测试

优先覆盖：

- SessionConfig、ToolConfig、ResponseConfig 序列化。
- unknownFields 合并与强类型字段优先级。
- 显式 null 序列化。
- Realtime 已知事件与 Unknown 事件解析。
- PCM base64。
- Audio playback interrupt。
- RMS 计算与 captureLevel 归零。
- capturedPcm Flow 行为。
- JPEG packetizer。
- 错误映射与日志脱敏。

### 18.2 SDK 集成测试

使用 OkHttp MockWebServer。

覆盖：

- Realtime 握手 Header 和子协议。
- `session.update` 时序。
- 文本、图片、response.create JSON。
- 音频 append / commit / clear。
- 音频响应流和播放中断。
- 401 与网络断开。
- Video 未就绪与 packet 还原。

### 18.3 Sample ViewModel 测试

覆盖：

- DataStore 默认值与迁移。
- Agent ID / Advanced 模式分支。
- 复杂配置校验。
- TTS API 查询参数。
- PTT 长按、短按、重复按、断开。
- Vision 启动、失败、停止、结果解析。
- 对话信息记录生成、去重、脱敏、清理时机。

### 18.4 UI 与手动测试

覆盖：

- 设置页配置模式切换。
- 复杂配置页保存与恢复默认值。
- 发音人选择器。
- PTT Checkbox 和 PTT Button 显隐。
- Vision Checkbox、FPS 和结果面板。
- 对话信息页时间线。
- 深色模式、横竖屏基本适配、contentDescription。

### 18.5 E2E 测试

E2E 依赖测试 token 和 Stardust 测试环境，不作为普通 PR 必跑测试。

覆盖：

- Agent ID 语音问答。
- 复杂配置语音问答。
- 发音人变化验证。
- 文本和图片问答。
- Server VAD 打断。
- PTT 误触、打断、提交。
- Vision + Voice 上传与结果展示。
- Video 失败降级。
- 对话信息断开后回看。

## 19. 分阶段实现计划

### M1：协议核心

目标：不用真实麦克风/扬声器/摄像头也能跑通 Realtime 协议核心。

范围：

- Gradle Android library 工程。
- `StardustConfig`、`StardustClient` 基础 API。
- Realtime WebSocket 连接、鉴权、子协议和状态机。
- `SessionConfig`、`ToolConfig`、`ResponseConfig` 序列化。
- 事件 parser，支持已知事件与 Unknown。
- `sendText()`、`sendImage()`、`sendMultimodalMessage()`、`createResponse()`。
- PCM append/commit/clear 协议发送。
- 错误模型、基础日志脱敏。
- 单元测试和 MockWebServer 集成测试。

### M2：语音能力与 PTT

范围：

- AudioRecord 采集。
- AudioTrack 播放队列。
- `response.audio.delta` 解码播放。
- 播放中断与恢复。
- `capturedPcm` 与 `captureLevel`。
- 移除 `AudioState`，双状态流重构。
- Sample 使用真实音量替代模拟音量。
- Sample PTT。

### M3：配置中心与视觉

范围：

- Sample Agent ID / Advanced 配置模式。
- AdvancedSessionSettings、DataStore 迁移、复杂配置页面。
- TTS API client 与发音人选择器。
- Video WebSocket、JPEG packetizer、帧率限制。
- `response.video.done` 事件解析。
- Sample Vision + Voice。

### M4：对话信息、UI 统一与稳定性

范围：

- 对话信息页面。
- Sample UI 统一、深色模式、可访问性。
- 完整重连策略。
- 诊断统计补齐。
- README、API 文档、迁移指南。
- Maven artifact 发布。

## 20. 开发顺序建议

建议第一批代码按以下顺序实现：

1. 初始化 Gradle Android library 与 sample 工程。
2. 建立 public API 空壳和包结构。
3. 实现 JSON 基础设施与 `SessionConfig(agentId)` 序列化测试。
4. 实现 ToolConfig、ResponseConfig 和 unknownFields 合并。
5. 实现 Realtime event parser。
6. 实现 Realtime WebSocket transport。
7. 实现 `StardustClient.connect()`、`updateSession()`、`sendText()`、`createResponse()`。
8. 加入 MockWebServer 集成测试。
9. 实现 PCM base64 与音频协议发送。
10. 实现错误模型、日志脱敏和基础 diagnostics。
11. 实现 AudioRecord、AudioTrack、captureLevel、capturedPcm。
12. 重构 Sample 音频状态映射，移除 AudioState 依赖。
13. 实现 PTT。
14. 实现复杂配置与 TTS 发音人选择。
15. 实现 Video packetizer 与 Vision + Voice。
16. 实现对话信息页。
17. 统一 UI 风格与深色模式。

## 21. 待确认事项

以下事项不阻塞 M1，但需要在进入对应阶段前确认：

1. 是否要求首版内置重采样；如要求，需要确定实现方式。
2. `updateSession()` 在 `Connecting` 状态下挂起等待的超时时间是否复用连接超时。
3. `seq_id` 起始值是否需与其他端保持一致；当前建议从 0 开始。
4. TTS API 的鉴权、分页响应字段、baseUrl 派生规则。
5. CameraX 依赖版本与目标设备兼容性。
6. 是否需要 Java 示例；API 需要 Java 可调用，但示例可后置。
7. Maven 发布目标是内部仓库、GitHub Packages 还是 Maven Central。
8. E2E 测试 token、测试环境 URL 和 CI 机密注入方式。
9. 对话信息页未来是否需要本地音频缓存或导出；如需要，需单独隐私评估。

## 22. 基线完成定义

新基线完成时应满足：

- SDK 可作为 Android library 编译。
- 单元测试通过。
- MockWebServer 集成测试通过。
- 使用测试 token 可建立 Realtime 连接。
- 可发送 Agent ID 与复杂 `session.update`。
- 可发送文本、图片、音频并解析响应事件。
- 可采集、播放、打断音频，并输出真实 captureLevel。
- 不存在 `AudioState` public API。
- 可连接 Video 并上传 pad 兼容 JPEG packet。
- Sample 可演示 Agent ID、复杂配置、PTT、Vision + Voice、对话信息页。
- token、API key、terminal secret、音频 base64、视频 payload 等敏感内容不会出现在日志或 UI 中。
