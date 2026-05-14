# Stardust Android SDK SRS

## 1. 文档目的

本文是 Stardust Android SDK 与 Sample App 的软件需求规格说明书（Software Requirements Specification, SRS），用于把 `PRD.md` 中的新基线产品需求转化为可实现、可测试、可追踪的工程规格。

本文覆盖：

1. SDK 对外 Kotlin API 与数据模型。
2. SDK 内部状态机、协议映射、错误与诊断规格。
3. 音频、视频、Session、Tool、Event 的详细行为。
4. Sample App 的配置、PTT、Vision + Voice、对话信息页和 UI 状态规格。
5. 单元测试、集成测试、端到端测试与需求追踪。

## 2. 参考资料

- [PRD.md](PRD.md)
- [TECH_DESIGN.md](TECH_DESIGN.md)
- `stardust/docs/realtime_api_reference.md`
- `stardust/docs/session_config.md`
- `stardust/docs/api.md`
- `stardust/src/websockethandlers/realtime_handler.py`
- `stardust/src/websockethandlers/video_handler.py`
- `stardust/src/websockethandlers/websocket_handler_base.py`
- `android-sdk/docs` 下历史增量需求与设计文档

## 3. 范围

### 3.1 范围内

SDK 基线必须包含：

- Realtime WebSocket 连接、鉴权、状态管理和事件收发。
- Session 配置模型与 `session.update` 序列化。
- 文本、图片、音频输入事件封装。
- 服务端事件解析和强类型事件分发。
- PCM16 音频采集、发送、接收、播放辅助、播放中断。
- 采集侧 PCM 帧与 RMS 音量对外输出。
- 采集状态与播放状态双流，不再使用聚合 `AudioState`。
- Video WebSocket 连接、JPEG 帧封包和发送。
- `function`、`mcp`、`ticos_mcp` 工具配置序列化。
- 错误码、日志、诊断统计、资源释放和测试支撑。

Sample App 基线必须包含：

- 设置页与连接配置。
- Agent ID 与复杂 Session 配置模式。
- DataStore 持久化与老数据迁移。
- TTS 发音人列表获取、筛选和手动 voice ID 兜底。
- Server VAD 与 PTT 交互。
- Vision + Voice 交互、CameraX JPEG 采集、FPS 控制和结果展示。
- 对话信息页面。
- 统一 UI 设计、字符串资源、权限与脱敏。

### 3.2 范围外

- SDK 不实现 ASR、LLM、TTS 或视觉识别模型。
- SDK 不提供完整 UI 组件库。
- SDK 默认不持久化会话历史、音频、图片、视频帧或 raw event。
- Sample App 不实现完整 Agent 配置后台、发音人训练、声纹注册、人脸管理。
- Sample App 不提供跨进程历史会话管理、音频导出、分享或长期存档。
- 本地函数调用结果闭环依赖服务端补齐 `function_call_output` 上行处理。

## 4. 术语

- SDK：Stardust Android SDK。
- Sample：`android-sdk/sample` 示例应用。
- Realtime：`wss://stardust.ticos.cn/realtime` WebSocket 通道。
- Video：`wss://stardust.ticos.cn/video` WebSocket 通道。
- Server VAD：服务端检测语音起止，客户端持续发送音频。
- Client VAD：客户端控制音频片段起止，并主动 commit。
- PTT：Push to Talk，Sample 层 Client VAD 交互实现。
- PCM16：16-bit signed PCM audio。
- EOI：JPEG End Of Image 标记，通常为 `0xFF 0xD9`。
- MCP：Model Context Protocol。
- 对话信息：Sample 中用于展示当前一轮用户语音、助手语音、文本预留与 function/tool 调用的时间线数据。

## 5. 总体架构规格

### 5.1 SDK 模块划分

SDK 应拆分为以下包或模块：

- `core`：`StardustClient`、配置、状态、错误、生命周期。
- `realtime`：Realtime WebSocket、事件 writer、事件 parser。
- `model`：Session、Conversation、Response、Tool、Event 数据模型。
- `audio`：AudioRecord、AudioTrack、PCM 编解码、重采样、播放队列、采集输出。
- `video`：Video WebSocket、JPEG packetizer、帧率限制、CameraX helper 预留。
- `diagnostics`：日志、指标、诊断快照。
- `testkit`：Mock WebSocket Server、协议断言工具、假音频设备。

### 5.2 Sample 模块划分

Sample 应至少包含：

- `settings`：AppSettings、DataStore、SettingsRepository。
- `voice`：VoiceViewModel、VoiceUiState、VoicePhaseMapping。
- `session`：复杂 Session 配置页面与转换逻辑。
- `tts`：TTS API client、发音人列表与筛选。
- `vision`：CameraFrameCapture、VisionResultParser、视觉 UI 状态。
- `conversationinfo`：ConversationRecord、ConversationInfoUiState、页面 UI。
- `ui`：MainScreen、SettingsScreen、组件、主题与字符串资源。

### 5.3 技术约束

- Kotlin-first，保证 Java 可调用。
- 最低 Android API Level 26。
- WebSocket 推荐 OkHttp。
- 异步模型推荐 Kotlin Coroutines 与 Flow。
- JSON 推荐 kotlinx.serialization，必须支持未知字段透传。
- Public API 可从主线程调用，内部不得阻塞主线程。
- Sample UI 使用 Compose 与 StateFlow 风格。

## 6. SDK 外部接口

### 6.1 StardustConfig

必需字段：

- `realtimeUrl: String`，默认 `wss://stardust.ticos.cn/realtime`。
- `videoUrl: String`，默认 `wss://stardust.ticos.cn/video`。
- `tokenProvider: suspend () -> String`。

可选字段：

- `terminalSecretProvider: (suspend () -> String)?`。
- `queryParams: Map<String, String>`。
- `connectTimeoutMs: Long`。
- `writeTimeoutMs: Long`。
- `readTimeoutMs: Long`。
- `reconnectPolicy: ReconnectPolicy`。
- `logLevel: StardustLogLevel`。
- `autoPlayAudio: Boolean`，默认 `true`。
- `autoReconnectRealtime: Boolean`，默认 `true`。
- `autoReconnectVideo: Boolean`，默认 `false`。
- `videoConnectPolicy: VideoConnectPolicy`，默认 `RequireSessionCreated`。
- `videoMaxFps: Int`，默认 `2`，范围 `1..30`。

需求：

- SRS-CFG-001：SDK 必须在 Realtime 和 Video 握手时设置 `Authorization: Bearer <token>`。
- SRS-CFG-002：如果配置 `terminalSecretProvider`，SDK 必须允许使用 `terminal_secret=<token>` 兼容鉴权。
- SRS-CFG-003：SDK 日志不得输出 token、terminal secret、MCP authorization 或 API Key 明文。
- SRS-CFG-004：`videoMaxFps` 超出范围时必须钳制到 `1..30` 或返回配置错误，行为需文档化。

### 6.2 StardustClient

建议接口：

```kotlin
interface StardustClient {
    val state: StateFlow<StardustState>
    val events: SharedFlow<StardustEvent>
    val errors: SharedFlow<StardustSdkError>
    val diagnostics: StardustDiagnostics
    val audio: StardustAudio
    val video: StardustVideo

    suspend fun connect()
    suspend fun updateSession(session: SessionConfig)
    suspend fun sendText(text: String, userId: String = "nobody", previousItemId: String? = null)
    suspend fun sendImage(imageUrl: String, prompt: String? = null, userId: String = "nobody", previousItemId: String? = null)
    suspend fun sendMultimodalMessage(text: String?, imageUrls: List<String>, userId: String = "nobody", previousItemId: String? = null)
    suspend fun createResponse(response: ResponseConfig = ResponseConfig.audio())
    suspend fun cancelResponse(userId: String = "nobody")
    suspend fun sendRawEvent(json: String)
    suspend fun close()
}
```

需求：

- SRS-API-001：`connect()` 必须建立 Realtime WebSocket，并设置子协议 `realtime`。
- SRS-API-002：`updateSession()` 只能在 Realtime 至少达到 `Connected` 后调用；否则必须挂起等待、返回错误或按配置排队，不得静默丢弃。
- SRS-API-003：`close()` 必须关闭 Realtime、Video、音频采集、音频播放和内部 coroutine scope。
- SRS-API-004：`sendRawEvent()` 必须保留为调试逃生口，日志需脱敏音频、图片、视频、token、API key 字段。
- SRS-API-005：Public API 必须线程安全，允许主线程调用。
- SRS-API-006：文本、图片和多模态输入 API 必须允许 `previousItemId`，并支持 `initial_user_prompt` 与 `initial_assistant_prompt`。
- SRS-API-007：`cancelResponse()` 必须先停止本地播放队列，再发送 `response.cancel`。

### 6.3 StardustAudio

建议接口：

```kotlin
interface StardustAudio {
    val captureState: StateFlow<CaptureAudioState>
    val playbackState: StateFlow<PlaybackAudioState>
    val capturedPcm: SharedFlow<ByteArray>
    val captureLevel: StateFlow<Float>

    suspend fun startCapture()
    suspend fun stopCapture(commit: Boolean = true)
    suspend fun appendPcm(pcm16: ByteArray)
    suspend fun commit()
    suspend fun clear()
    suspend fun stopPlayback(clearQueue: Boolean = true)
    fun setPlaybackEnabled(enabled: Boolean)
}
```

需求：

- SRS-AUD-001：`StardustAudio` 不得暴露 `state: StateFlow<AudioState>`。
- SRS-AUD-002：`startCapture()` 必须以 PCM16、24kHz、单声道作为 SDK 对服务端的输出格式。
- SRS-AUD-003：如果设备不支持 24kHz，SDK 必须重采样或返回 `AUDIO_RECORD_UNSUPPORTED_FORMAT`。
- SRS-AUD-004：`appendPcm()` 必须把 PCM bytes base64 编码为 `input_audio_buffer.append`。
- SRS-AUD-005：`commit()` 必须发送 `input_audio_buffer.commit`。
- SRS-AUD-006：`clear()` 必须发送 `input_audio_buffer.clear`。
- SRS-AUD-007：收到 `response.audio.delta` 且 `delta != null` 时，SDK 必须解码为 PCM16，并在自动播放开启时写入播放队列。
- SRS-AUD-008：收到 `input_audio_buffer.speech_started` 时，SDK 必须立即停止 AudioTrack 并清空播放队列。
- SRS-AUD-009：`cancelResponse()` 时 SDK 必须先停止本地播放队列，再发送 `response.cancel`。
- SRS-AUD-010：`capturedPcm` 必须仅在采集期间发射本地麦克风 PCM 帧，不进入 `events`。
- SRS-AUD-011：`captureLevel` 必须与采集帧同频更新，范围 `[0.0, 1.0]`，停止采集后归零。

### 6.4 StardustVideo

建议接口：

```kotlin
interface StardustVideo {
    val state: StateFlow<VideoState>

    suspend fun connect()
    suspend fun sendJpegFrame(jpeg: ByteArray)
    suspend fun disconnect()
}
```

需求：

- SRS-VID-001：`connect()` 必须校验 Realtime 至少处于 `SessionCreated`；若配置 `RequireSessionUpdated`，必须等待 `SessionUpdated`。
- SRS-VID-002：Realtime 未就绪时，`connect()` 必须挂起等待、排队或返回 `VIDEO_REALTIME_NOT_READY`，不得静默建立丢帧通道。
- SRS-VID-003：`sendJpegFrame()` 必须在 Video 已连接且 Realtime 会话有效时发送，否则返回 `VIDEO_NOT_CONNECTED` 或 `VIDEO_REALTIME_NOT_READY`。
- SRS-VID-004：SDK 必须按服务端二进制协议封包 JPEG 帧。
- SRS-VID-005：SDK 必须在真实 JPEG payload 末尾追加 1 个 pad 字节。
- SRS-VID-006：服务端通过 `message[10:-1]` 读取后得到的 JPEG 必须与调用方传入的 `jpeg` 完全一致。
- SRS-VID-007：SDK 必须支持发送帧率限制，默认 2 FPS。

## 7. SDK 状态机

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

需求：

- SRS-STATE-001：初始状态必须为 `Idle`。
- SRS-STATE-002：调用 `connect()` 后必须进入 `Connecting`。
- SRS-STATE-003：WebSocket 握手成功后必须进入 `Connected`。
- SRS-STATE-004：收到 `session.created` 后必须进入 `SessionCreated`。
- SRS-STATE-005：收到 `session.updated` 后必须进入 `SessionUpdated`。
- SRS-STATE-006：调用 `close()` 后必须进入 `Closing`，释放完成后进入 `Closed`。
- SRS-STATE-007：401、协议不可恢复错误或重试超过上限时必须进入 `Failed`。
- SRS-STATE-008：启用重连时，可恢复网络错误必须进入 `Reconnecting` 后重新连接。

### 7.2 CaptureAudioState

状态：

- `Idle`
- `Recording`
- `Stopping`

需求：

- SRS-STATE-101：`startCapture()` 成功后必须进入 `Recording`。
- SRS-STATE-102：`stopCapture()` 执行期间必须进入 `Stopping`。
- SRS-STATE-103：停止完成后必须进入 `Idle`。
- SRS-STATE-104：采集失败必须通过 `errors` 派发并回到 `Idle` 或进入客户端失败状态，具体行为需文档化。

### 7.3 PlaybackAudioState

状态：

- `Idle`
- `Playing`
- `Failed`

需求：

- SRS-STATE-151：收到可播放 `response.audio.delta` 后，如自动播放开启，必须进入或保持 `Playing`。
- SRS-STATE-152：播放队列清空且当前响应完成后必须回到 `Idle`。
- SRS-STATE-153：播放失败必须进入 `Failed` 并派发 `AUDIO_PLAYBACK_FAILED`。
- SRS-STATE-154：`stopPlayback(clearQueue = true)` 后必须停止播放并清空队列。

### 7.4 Video 状态

状态：

- `Idle`
- `WaitingRealtime`
- `Connecting`
- `Connected`
- `Disconnecting`
- `Disconnected`
- `Failed`

需求：

- SRS-STATE-201：Realtime 未达到策略要求时调用 `video.connect()`，必须进入 `WaitingRealtime` 或返回明确错误。
- SRS-STATE-202：Video WebSocket 握手成功后必须进入 `Connected`。
- SRS-STATE-203：Realtime 关闭时，Video 必须自动断开并进入 `Disconnected`。
- SRS-STATE-204：Video 失败不得导致 Realtime 自动关闭。

## 8. 协议映射

### 8.1 上行事件映射

- SRS-PROTO-001：`updateSession(session)` 必须发送 `{"type":"session.update","session":{...}}`。
- SRS-PROTO-002：`audio.appendPcm(pcm16)` 必须发送 `{"type":"input_audio_buffer.append","audio":"<base64>"}`。
- SRS-PROTO-003：`audio.commit()` 必须发送 `{"type":"input_audio_buffer.commit"}`。
- SRS-PROTO-004：`audio.clear()` 必须发送 `{"type":"input_audio_buffer.clear"}`。
- SRS-PROTO-005：`sendText()` 必须发送 `conversation.item.create`，内容类型为 `input_text`。
- SRS-PROTO-006：`sendImage()` 必须发送 `conversation.item.create`，内容包含 `input_image`；当 `prompt != null` 时还必须包含 `input_text`。
- SRS-PROTO-007：`createResponse()` 必须发送 `response.create`。
- SRS-PROTO-008：`cancelResponse()` 必须发送 `response.cancel`。
- SRS-PROTO-009：`sendRawEvent(json)` 必须原样发送调用方提供的 JSON 字符串。
- SRS-PROTO-010：当 `previousItemId != null` 时，SDK 必须在 `conversation.item.create` 顶层写入 `previous_item_id`。
- SRS-PROTO-011：`previous_item_id` 必须允许 `initial_user_prompt` 与 `initial_assistant_prompt`。

### 8.2 下行事件映射

SDK 必须把以下服务端事件解析为强类型事件：

- `error` -> `StardustEvent.Error`
- `session.created` -> `StardustEvent.SessionCreated`
- `session.updated` -> `StardustEvent.SessionUpdated`
- `conversation.created` -> `StardustEvent.ConversationCreated`
- `conversation.item.created` -> `StardustEvent.ConversationItemCreated`
- `conversation.item.input_audio_transcription.completed` -> `StardustEvent.InputAudioTranscriptionCompleted`
- `conversation.item.input_audio_transcription.failed` -> `StardustEvent.InputAudioTranscriptionFailed`
- `input_audio_buffer.committed` -> `StardustEvent.InputAudioBufferCommitted`
- `input_audio_buffer.cleared` -> `StardustEvent.InputAudioBufferCleared`
- `input_audio_buffer.speech_started` -> `StardustEvent.InputAudioBufferSpeechStarted`
- `input_audio_buffer.speech_stopped` -> `StardustEvent.InputAudioBufferSpeechStopped`
- `response.created` -> `StardustEvent.ResponseCreated`
- `response.done` -> `StardustEvent.ResponseDone`
- `response.output_item.added` -> `StardustEvent.ResponseOutputItemAdded`
- `response.output_item.done` -> `StardustEvent.ResponseOutputItemDone`
- `response.content_part.added` -> `StardustEvent.ResponseContentPartAdded`
- `response.content_part.done` -> `StardustEvent.ResponseContentPartDone`
- `response.text.delta` -> `StardustEvent.ResponseTextDelta`
- `response.text.done` -> `StardustEvent.ResponseTextDone`
- `response.audio_transcript.delta` -> `StardustEvent.ResponseAudioTranscriptDelta`
- `response.audio_transcript.done` -> `StardustEvent.ResponseAudioTranscriptDone`
- `response.audio.delta` -> `StardustEvent.ResponseAudioDelta`
- `response.audio.done` -> `StardustEvent.ResponseAudioDone`
- `response.function_call_arguments.done` -> `StardustEvent.ResponseFunctionCallArgumentsDone`
- `response.video.done` -> `StardustEvent.ResponseVideoDone`

需求：

- SRS-PROTO-101：未知事件必须解析为 `StardustEvent.Unknown`，并保留 `type`、`event_id` 和 raw JSON。
- SRS-PROTO-102：占位事件如 `conversation.item.truncated`、`conversation.item.deleted`、`response.function_call_arguments.delta` 不得导致解析失败。
- SRS-PROTO-103：所有事件模型必须保留 raw JSON。
- SRS-PROTO-104：服务端 `error` 事件必须同时派发到 `events` 与 `errors`。

## 9. SDK 数据模型规格

### 9.1 SessionConfig

```kotlin
data class SessionConfig(
    val agentId: String? = null,
    val model: ModelConfig? = null,
    val speech: SpeechConfig? = null,
    val hearing: HearingConfig? = null,
    val vision: VisionConfig? = null,
    val knowledge: KnowledgeConfig? = null,
    val webhook: WebhookConfig? = null,
    val triggers: List<TriggerConfig>? = null,
    val extra: Map<String, JsonElement>? = null,
    val unknownFields: Map<String, JsonElement> = emptyMap()
)
```

需求：

- SRS-MODEL-001：`agentId` 必须序列化为 `agent_id`。
- SRS-MODEL-002：null 字段默认不序列化，除非协议需要显式 null，例如 `hearing.turn_detection = null`。
- SRS-MODEL-003：`unknownFields` 必须原样合并到 `session` 对象，且不得覆盖强类型字段。
- SRS-MODEL-004：SDK 必须支持只发送 `{"agent_id":"..."}` 的最小配置。
- SRS-MODEL-005：复杂配置模式下必须支持不发送 `agent_id`，仅发送 `model/speech/hearing`。

### 9.2 ModelConfig

必须支持字段：

- `provider`
- `name`
- `modalities`
- `instructions`
- `extConfig`
- `includeInitialPrompt`
- `initialUserPrompt`
- `initialAssistantPrompt`
- `historyConversationLength`
- `tools`
- `toolChoice`
- `useInnerTools`
- `useInnerViewTools`
- `emotionClassifier`
- `temperature`
- `topP`
- `topK`
- `maxResponseOutputTokens`
- `messages`
- `unknownFields`

需求：

- SRS-MODEL-101：`extConfig` 必须支持 `provider`、`model_name`、`model_url`、`api_key`，并允许 Map 扩展。
- SRS-MODEL-102：`messages.nobody` 必须支持 role 为 `system`、`user`、`assistant` 的历史消息。
- SRS-MODEL-103：`instructions` 必须支持 string。

### 9.3 SpeechConfig

必须支持字段：

- `voice`
- `emotion`
- `outputAudioFormat`
- `speedRatio`
- `pitchRatio`
- `volumeRatio`
- `unknownFields`

需求：

- SRS-MODEL-151：`outputAudioFormat` 必须序列化为 `output_audio_format`。
- SRS-MODEL-152：Sample 复杂配置模式下 `outputAudioFormat` 固定为 `pcm16`。
- SRS-MODEL-153：`speedRatio`、`pitchRatio`、`volumeRatio` 必须支持整数或服务端兼容数值类型，序列化字段名为 `speed_ratio`、`pitch_ratio`、`volume_ratio`。

### 9.4 HearingConfig

必须支持字段：

- `inputAudioFormat`
- `turnDetection`
- `turnVoiceprint`
- `unknownFields`

需求：

- SRS-MODEL-181：`inputAudioFormat` 必须序列化为 `input_audio_format`。
- SRS-MODEL-182：PTT 模式下 `turnDetection` 必须序列化为 JSON `null`。
- SRS-MODEL-183：Server VAD 默认模式不得无意覆盖服务端默认 `turn_detection`。

### 9.5 VisionConfig

必须支持字段或 unknownFields 透传：

- `enable_face_detection`
- `enable_face_identification`
- `enable_gesture_detection`
- `enable_object_detection`
- `object_detection_target_classes`
- `face_album_id`
- `unknownFields`

需求：

- SRS-MODEL-191：Sample Vision + Voice 主路径不强制发送 `vision` 字段。
- SRS-MODEL-192：如需显式控制视觉开关，Sample 必须通过 `VisionConfig.unknownFields` 合并，不得在根级 `unknownFields` 中重复生成 `vision`。

### 9.6 ToolConfig

`ToolConfig` 必须支持三类工具。

`FunctionToolConfig`：

- `type = "function"`
- `name`
- `description`
- `parameters`

`McpToolConfig`：

- `type = "mcp"`
- `server_label`
- `server_url`
- `server_description`
- `allowed_tools`
- `require_approval`
- `authorization`
- `unknownFields`

`TicosMcpToolConfig`：

- `type = "ticos_mcp"`
- `name`
- `description`
- `parameters`
- `server_url`
- `mcp_server_id`
- `mcp_api_key`
- `server_label`
- `source_type`
- `operation_mode`
- `execution_type`
- `result_handling`
- `code`
- `language`
- `authorization`
- `unknownFields`

需求：

- SRS-TOOL-001：SDK 不得只支持 `function`；`mcp` 和 `ticos_mcp` 是首版必需能力。
- SRS-TOOL-002：ToolConfig 序列化不得丢失鉴权、白名单、执行模式和未知字段。
- SRS-TOOL-003：SDK 文档必须说明 `mcp` / `ticos_mcp` 由 Stardust 服务端执行。

### 9.7 ResponseConfig

必须支持字段：

- `modalities`
- `instructions`
- `voice`
- `tools`
- `temperature`
- `maxOutputTokens`
- `conversation`
- `input`
- `unknownFields`

需求：

- SRS-MODEL-201：`ResponseConfig.audio()` 默认 `modalities = ["audio"]`。
- SRS-MODEL-202：`ResponseConfig.text()` 默认 `modalities = ["text"]`。
- SRS-MODEL-203：同时包含 `text` 和 `audio` 时，按服务端语义处理。

### 9.8 StardustEvent

需求：

- SRS-EVT-001：所有事件必须包含 `eventId: String?`。
- SRS-EVT-002：所有事件必须包含 `rawJson: String`。
- SRS-EVT-003：音频 delta 事件必须提供 base64 字符串和可选 decoded bytes。
- SRS-EVT-004：视频 done 事件必须支持 `face_info`、`object_info`、`image_info`、`hand_info` 中任意一种或多种字段。
- SRS-EVT-005：`response.done` 必须解析 status、output、usage token 统计。
- SRS-EVT-006：function/tool 相关事件必须尽量提取 `call_id`、`name`、`arguments`、`response_id`、`item_id`。

## 10. 音频规格

### 10.1 输入音频

- SRS-AUDIO-IN-001：SDK 对服务端发送的音频必须是 PCM16、24kHz、单声道。
- SRS-AUDIO-IN-002：推荐音频分片 20ms 到 100ms；默认 40ms。
- SRS-AUDIO-IN-003：每个分片必须独立 base64 后发送 `input_audio_buffer.append`。
- SRS-AUDIO-IN-004：Server VAD 模式下，SDK 可持续发送音频，不主动 commit。
- SRS-AUDIO-IN-005：Client VAD 模式下，SDK 必须允许业务自行控制 append、commit、clear。

### 10.2 采集数据输出

- SRS-AUDIO-CAP-001：`capturedPcm` 发射的帧必须为 PCM16、24kHz、mono、16-bit signed little-endian。
- SRS-AUDIO-CAP-002：默认帧大小约 1920 bytes，对应 40ms。
- SRS-AUDIO-CAP-003：`capturedPcm` 的缓冲策略不得阻塞采集线程；推荐 `extraBufferCapacity = 64` 且 `DROP_OLDEST`。
- SRS-AUDIO-CAP-004：`captureLevel` 必须基于 RMS 计算，按满幅 32768 归一化到 `[0.0, 1.0]`。
- SRS-AUDIO-CAP-005：停止采集后 `captureLevel` 必须重置为 `0f`。
- SRS-AUDIO-CAP-006：`capturedPcm` 与实际上行 append 的 PCM 帧内容应一致。

### 10.3 输出音频

- SRS-AUDIO-OUT-001：SDK 必须支持播放 PCM16、24kHz、单声道音频。
- SRS-AUDIO-OUT-002：`response.audio.delta.delta == null` 时不得写入 AudioTrack。
- SRS-AUDIO-OUT-003：播放队列必须按事件到达顺序播放。
- SRS-AUDIO-OUT-004：收到 `response.audio.done` 后必须标记当前 response 音频完成，但不得错误清空后续 response 的音频。
- SRS-AUDIO-OUT-005：收到 `input_audio_buffer.speech_started` 后必须 stop、flush、clear queue。
- SRS-AUDIO-OUT-006：调用 `cancelResponse()` 后必须 stop、flush、clear queue，并发送 `response.cancel`。
- SRS-AUDIO-OUT-007：AudioTrack 被 stop 后，下一段可播放音频到达时必须安全重新 `play()`。

## 11. 视频规格

### 11.1 帧封包

`JpegFramePacketizer` 必须输出：

1. 1 字节 sync head：`0x54`。
2. 1 字节 message type：`0x20`。
3. 4 字节 `seq_id`，UInt32 Little Endian。
4. 4 字节 `msg_length`，UInt32 Little Endian。
5. N 字节 JPEG payload。
6. 1 字节 pad，建议 `0x00`。

需求：

- SRS-VIDEO-PKT-001：`seq_id` 必须单调递增，溢出时按 UInt32 回绕。
- SRS-VIDEO-PKT-002：`msg_length` 必须写入真实 JPEG payload 字节数，不包含 header 和 pad。
- SRS-VIDEO-PKT-003：packetizer 输出倒数第 2 字节必须等于输入 JPEG 最后 1 字节。
- SRS-VIDEO-PKT-004：packetizer 输出最后 1 字节必须是 SDK 追加 pad。

### 11.2 连接顺序

- SRS-VIDEO-CONN-001：`video.connect()` 默认必须等 Realtime 至少 `SessionCreated` 后执行。
- SRS-VIDEO-CONN-002：如果配置 `RequireSessionUpdated`，`video.connect()` 必须等 `SessionUpdated`。
- SRS-VIDEO-CONN-003：Realtime 关闭、失败或重连中时，SDK 必须暂停或断开 Video。
- SRS-VIDEO-CONN-004：Video 通道发送失败不得影响 Realtime 文本/音频事件解析。

### 11.3 帧率限制

- SRS-VIDEO-FPS-001：默认最大发送帧率为 2 FPS。
- SRS-VIDEO-FPS-002：`sendJpegFrame()` 高频调用时，SDK 必须丢弃或拒绝超出 FPS 限制的帧，并记录丢弃计数。
- SRS-VIDEO-FPS-003：帧率限制不得阻塞调用线程过久。

## 12. 错误处理

### 12.1 错误模型

```kotlin
data class StardustSdkError(
    val code: StardustErrorCode,
    val message: String,
    val cause: Throwable? = null,
    val rawEvent: String? = null,
    val recoverable: Boolean
)
```

错误码至少包含：

- `AUTH_FAILED`
- `NETWORK_UNAVAILABLE`
- `WEBSOCKET_CONNECT_FAILED`
- `WEBSOCKET_CLOSED`
- `SESSION_UPDATE_FAILED`
- `AUDIO_RECORD_FAILED`
- `AUDIO_RECORD_UNSUPPORTED_FORMAT`
- `AUDIO_PLAYBACK_FAILED`
- `JSON_PARSE_FAILED`
- `UNSUPPORTED_EVENT`
- `VIDEO_REALTIME_NOT_READY`
- `VIDEO_NOT_CONNECTED`
- `VIDEO_FRAME_ENCODE_FAILED`
- `VIDEO_PACKETIZE_FAILED`
- `SERVER_ERROR`

需求：

- SRS-ERR-001：HTTP 401 必须映射为 `AUTH_FAILED`，不得无限重连。
- SRS-ERR-002：服务端 `error` 必须映射为 `SERVER_ERROR` 或更具体错误，并保留 raw event。
- SRS-ERR-003：JSON 解析失败不得导致 SDK 崩溃。
- SRS-ERR-004：可恢复网络错误可按重连策略处理，不可恢复错误必须进入 `Failed`。
- SRS-ERR-005：Video 错误默认不关闭 Realtime。

## 13. 重连策略

需求：

- SRS-REC-001：Realtime 默认启用指数退避重连。
- SRS-REC-002：重连必须有最大次数或最大总耗时配置。
- SRS-REC-003：重连成功后可按配置自动重发最近一次 `session.update`。
- SRS-REC-004：重连期间不得继续采集并发送音频。
- SRS-REC-005：Realtime 重连后，Video 不得自动发帧，必须等待 Realtime 再次达到指定状态。
- SRS-REC-006：401 不参与自动重连。

## 14. 日志与诊断

### 14.1 日志

- SRS-LOG-001：日志等级必须支持 `NONE`、`ERROR`、`WARN`、`INFO`、`DEBUG`。
- SRS-LOG-002：音频 base64、图片 base64、视频 payload、token、terminal secret、API Key、MCP authorization 必须脱敏。
- SRS-LOG-003：Debug 日志可打印 event type、event_id、response_id、item_id、payload size。
- SRS-LOG-004：Sample UI 和 Snackbar 不得展示敏感字段明文。

### 14.2 诊断快照

`StardustDiagnostics` 至少包含：

- Realtime 当前状态。
- Video 当前状态。
- CaptureAudio 当前状态。
- PlaybackAudio 当前状态。
- Realtime 连接次数和重连次数。
- 最近一次连接耗时。
- 已发送音频字节数。
- 已接收音频字节数。
- 已发射采集 PCM 帧数。
- 当前 captureLevel。
- 已发送视频帧数。
- 已丢弃视频帧数。
- 最近一次错误码。

需求：

- SRS-DIAG-001：诊断快照必须可在任意线程读取。
- SRS-DIAG-002：诊断快照不得包含敏感字段明文。

## 15. 安全与隐私

- SRS-SEC-001：SDK 默认不落盘音频、图片、视频帧和 raw event。
- SRS-SEC-002：SDK 文档必须提示业务申请 `INTERNET`、`RECORD_AUDIO`、`CAMERA` 权限。
- SRS-SEC-003：业务传入 token 必须仅用于网络请求和内存态，不得写入日志。
- SRS-SEC-004：诊断上报必须脱敏。
- SRS-SEC-005：Sample Function 参数展示中包含 `token`、`secret`、`password`、`key` 等疑似敏感字段时必须脱敏。

## 16. Sample App 规格

### 16.1 AppSettings

```kotlin
enum class SessionConfigMode { AgentId, Advanced }

data class AdvancedSessionSettings(
    val modelProvider: String = "tiwater",
    val modelName: String = "stardust-2.5-max",
    val modalities: Set<String> = setOf("text", "audio"),
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
)
```

`AppSettings` 必须新增：

- `sessionConfigMode: SessionConfigMode = SessionConfigMode.AgentId`
- `advancedSession: AdvancedSessionSettings = AdvancedSessionSettings()`

需求：

- SRS-SAMPLE-SET-001：老版本 DataStore 无新增字段时必须默认 Agent ID 模式。
- SRS-SAMPLE-SET-002：切换模式不得清空另一模式配置。
- SRS-SAMPLE-SET-003：复杂配置保存只写本地，不立即连接。
- SRS-SAMPLE-SET-004：终端密钥默认隐藏，不得出现在摘要中。

### 16.2 复杂配置校验

- SRS-SAMPLE-ADV-001：Agent ID 模式下 `agentId` 必填，连接时只发送 `agent_id`。
- SRS-SAMPLE-ADV-002：复杂配置模式下不要求 `agentId`，连接时不得发送 `agent_id`。
- SRS-SAMPLE-ADV-003：`model.provider`、`model.name`、`model.instructions`、`speech.voice` 必填。
- SRS-SAMPLE-ADV-004：`temperature` 范围 `[0.01, 1.0]`。
- SRS-SAMPLE-ADV-005：`top_p` 范围 `[0.0, 1.0]`。
- SRS-SAMPLE-ADV-006：`top_k`、`max_response_output_tokens` 必须为正整数。
- SRS-SAMPLE-ADV-007：`history_conversation_length` 范围 `0..30`。
- SRS-SAMPLE-ADV-008：`speech.speed_ratio/pitch_ratio/volume_ratio` 范围 `1..100`。
- SRS-SAMPLE-ADV-009：`speech.output_audio_format` 与 `hearing.input_audio_format` 固定 `pcm16`。

### 16.3 TTS API

需求：

- SRS-SAMPLE-TTS-001：Sample 必须通过 Stardust TTS API `GET /tts` 动态获取发音人列表。
- SRS-SAMPLE-TTS-002：必须支持 `language`、`gender`、`provider`、`tags`、`name`、`skip`、`top`、`all` 参数。
- SRS-SAMPLE-TTS-003：默认加载中文推荐发音人，如 `language=chinese&skip=0&top=20`。
- SRS-SAMPLE-TTS-004：列表加载失败时允许手动输入 voice ID。
- SRS-SAMPLE-TTS-005：不得展示或保存训练音频。

### 16.4 VoiceUiState

Sample 的 `VoiceUiState` 必须支持：

- `phase: VoicePhase`
- `agentName: String`
- `statusResId: Int`
- `transcripts: List<TranscriptItem>`
- `audioLevel: Float`
- `errorMessage: String?`
- `isConfigured: Boolean`
- `language: String`
- `pttModeEnabled: Boolean`
- `pttSessionActive: Boolean`
- `pttPressed: Boolean`
- `isConnected: Boolean`
- `visionModeEnabled: Boolean`
- `visionSessionActive: Boolean`
- `visionResults: List<VisionResultItem>`
- `visionFps: Int`
- `visionStreaming: Boolean`
- `conversationInfo: ConversationInfoUiState` 或等价独立 StateFlow

### 16.5 PTT 规格

- SRS-SAMPLE-PTT-001：`FALSE_TOUCH_MS = 250L`。
- SRS-SAMPLE-PTT-002：未连接显示 PTT Checkbox，已连接隐藏或禁用。
- SRS-SAMPLE-PTT-003：连接时快照 `_pttModeEnabled` 为 `_pttSessionActive`，会话中不可变。
- SRS-SAMPLE-PTT-004：PTT 模式下 `SessionConfig.hearing.turnDetection` 必须为 JSON null。
- SRS-SAMPLE-PTT-005：PTT 模式连接完成后不得自动 `audio.startCapture()`。
- SRS-SAMPLE-PTT-006：PTT 按下调用 `audio.startCapture()`。
- SRS-SAMPLE-PTT-007：按住达到或超过 250ms 时，本轮必须至多一次调用 `cancelResponse()`。
- SRS-SAMPLE-PTT-008：松开时若 `elapsed >= 250ms`，调用 `audio.stopCapture(commit = true)`。
- SRS-SAMPLE-PTT-009：松开时若 `elapsed < 250ms`，调用 `audio.stopCapture(commit = false)` 并调用 `audio.clear()`。
- SRS-SAMPLE-PTT-010：PTT commit 后必须调用 `createResponse()`，显式发送 `response.create` 拉起模型回复。
- SRS-SAMPLE-PTT-011：断开或 ViewModel 清理时取消阈值 Job，`pttPressed=false`，`stopCapture(commit=false)`。
- SRS-SAMPLE-PTT-012：重复按下不得创建并发 `startCapture()`。

### 16.6 Vision + Voice 规格

- SRS-SAMPLE-VV-001：未连接显示 Vision Checkbox，已连接隐藏或禁用。
- SRS-SAMPLE-VV-002：连接时快照 `_visionModeEnabled` 为 `_visionSessionActive`，会话中不可变。
- SRS-SAMPLE-VV-003：FPS 选项为 1、2、5，默认 2。
- SRS-SAMPLE-VV-004：启用视觉时必须检查 CAMERA 权限；PTT + Vision 同时启用时还需 RECORD_AUDIO。
- SRS-SAMPLE-VV-005：无摄像头、权限拒绝或 Video 失败时必须提示并降级纯语音。
- SRS-SAMPLE-VV-006：连接顺序必须为 Realtime connect、updateSession、video.connect、CameraFrameCapture.start。
- SRS-SAMPLE-VV-007：视觉主路径不强制发送 `session.vision.enable_*` 字段。
- SRS-SAMPLE-VV-008：JPEG 编码和发送必须在后台线程或协程执行。
- SRS-SAMPLE-VV-009：App 进入后台时停止摄像头采集和帧发送。
- SRS-SAMPLE-VV-010：`response.video.done` 必须解析为 `VisionResultItem` 并最多保留最近 10 条。
- SRS-SAMPLE-VV-011：Video 失败不得关闭 Realtime。

### 16.7 VisionResultItem

```kotlin
data class VisionResultItem(
    val id: String,
    val timestamp: Long,
    val faceInfo: FaceInfo? = null,
    val objectInfo: ObjectInfo? = null,
    val handInfo: HandInfo? = null,
    val imageInfo: ImageInfo? = null,
    val rawJson: String? = null,
)
```

需求：

- SRS-SAMPLE-VR-001：`face_info.faces[]` 必须可解析为人脸数量、置信度和可选 bbox。
- SRS-SAMPLE-VR-002：`object_info.objects[]` 必须可解析 label、confidence 和可选 bbox。
- SRS-SAMPLE-VR-003：`hand_info.hands[]` 必须可解析 gesture 与 confidence。
- SRS-SAMPLE-VR-004：`image_info.description` 与 `image_info.labels[]` 必须可解析。
- SRS-SAMPLE-VR-005：部分字段缺失不得导致解析失败。

### 16.8 对话信息页规格

记录类型：

```kotlin
sealed interface ConversationRecord {
    val id: String
    val timestamp: Long
    val rawSummary: String?
}
```

必须支持：

- `UserVoice`
- `UserText`
- `AssistantVoice`
- `FunctionCall`

需求：

- SRS-SAMPLE-CIP-001：主页面必须提供对话信息入口。
- SRS-SAMPLE-CIP-002：返回主页面不得中断当前连接或音频流程。
- SRS-SAMPLE-CIP-003：每次新对话开始时必须清空上一轮信息。
- SRS-SAMPLE-CIP-004：对话终止、断开、失败、`onCleared()` 时不得立即清空信息。
- SRS-SAMPLE-CIP-005：记录必须包含稳定 id、类型、timestamp、展示文本、原始摘要。
- SRS-SAMPLE-CIP-006：时间展示格式为 `HH:mm:ss`。
- SRS-SAMPLE-CIP-007：用户语音记录应由 ASR/用户 conversation item 生成或更新。
- SRS-SAMPLE-CIP-008：助手语音记录应聚合助手文本和音频 delta/done 信息。
- SRS-SAMPLE-CIP-009：function/tool 调用必须展示名称、参数、时间和可选 call id。
- SRS-SAMPLE-CIP-010：function 参数非法 JSON 时展示原始字符串，不崩溃。
- SRS-SAMPLE-CIP-011：敏感参数字段必须脱敏。
- SRS-SAMPLE-CIP-012：列表默认最多保留最近 100 条。
- SRS-SAMPLE-CIP-013：快速断开/重连时，旧 client 延迟事件不得写入新一轮列表。
- SRS-SAMPLE-CIP-014：事件解析与 JSON 格式化不得阻塞主线程。

### 16.9 UI 与资源规格

- SRS-SAMPLE-UI-001：设置页和复杂配置页使用统一卡片、输入框、TopAppBar 和间距 Token。
- SRS-SAMPLE-UI-002：PTT、Vision、Session 配置模式选择控件风格一致。
- SRS-SAMPLE-UI-003：主 Orb 只负责连接/断开。
- SRS-SAMPLE-UI-004：用户可见字符串必须进入 `strings.xml` 和中文资源。
- SRS-SAMPLE-UI-005：主 Orb、PTT 按钮、Vision 开关、设置入口、对话信息入口必须有 contentDescription。
- SRS-SAMPLE-UI-006：支持深色模式语义色，不得硬编码仅适配浅色主题。

## 17. 测试规格

### 17.1 SDK 单元测试

必须覆盖：

- SRS-UT-001：`SessionConfig(agentId)` 序列化。
- SRS-UT-002：高级 Session 字段、unknownFields、显式 null 序列化。
- SRS-UT-003：`FunctionToolConfig`、`McpToolConfig`、`TicosMcpToolConfig` 序列化。
- SRS-UT-004：所有已知 Realtime server event 解析。
- SRS-UT-005：未知事件解析为 `Unknown`。
- SRS-UT-006：PCM bytes 与 base64 双向转换。
- SRS-UT-007：`response.audio.delta.delta == null` 不入队。
- SRS-UT-008：`input_audio_buffer.speech_started` 清空播放队列。
- SRS-UT-009：AudioTrack stop 后下一段音频可重新播放。
- SRS-UT-010：`computeRmsLevel` 静音、最大振幅、已知波形。
- SRS-UT-011：`capturedPcm` 采集中发帧，停止后不继续发帧。
- SRS-UT-012：`captureLevel` 停止后归零。
- SRS-UT-013：JPEG packetizer header、Little Endian、`msg_length`、pad。
- SRS-UT-014：Video 未就绪时返回明确错误。
- SRS-UT-015：服务端 `error` 同时派发 event 和 error。

### 17.2 SDK 集成测试

必须覆盖：

- SRS-IT-001：Mock WebSocket Server 验证 Realtime 握手包含 Authorization 和子协议。
- SRS-IT-002：发送 `session.update` 后处理 `session.updated`。
- SRS-IT-003：连续发送音频 append。
- SRS-IT-004：模拟音频响应流并验证播放或派发 bytes。
- SRS-IT-005：模拟 `input_audio_buffer.speech_started` 停止播放。
- SRS-IT-006：Video 早于 Realtime 就绪时不发送帧。
- SRS-IT-007：Video packet 可按服务端兼容逻辑还原 JPEG。
- SRS-IT-008：模拟 401，进入 `Failed` 并派发 `AUTH_FAILED`。
- SRS-IT-009：模拟断线并验证重连策略。
- SRS-IT-010：服务端 `error` 同时出现在 `events` 与 `errors`。

### 17.3 Sample 单元与 ViewModel 测试

必须覆盖：

- SRS-SUT-001：AppSettings 默认值与 DataStore 迁移。
- SRS-SUT-002：Agent ID 模式只发送 `agent_id`。
- SRS-SUT-003：复杂配置模式不发送 `agent_id`，只发送 `model/speech/hearing`。
- SRS-SUT-004：复杂配置字段校验。
- SRS-SUT-005：TTS API 参数拼接与响应解析。
- SRS-SUT-006：敏感字段脱敏。
- SRS-SUT-007：PTT 长按、短按、重复按下、断开中断。
- SRS-SUT-008：PTT 模式连接不自动采集，Server VAD 自动采集。
- SRS-SUT-009：Vision 启用调用 `video.connect()` 和 CameraFrameCapture。
- SRS-SUT-010：Vision 失败降级但语音继续。
- SRS-SUT-011：VisionResultParser 正常、缺字段、非法 JSON。
- SRS-SUT-012：对话信息记录生成、聚合、去重、清理时机、脱敏。

### 17.4 UI 测试

必须覆盖：

- SRS-UI-001：设置页可切换 Agent ID / 复杂配置模式。
- SRS-UI-002：复杂配置页可保存、返回、恢复默认值。
- SRS-UI-003：发音人选择器可加载、筛选、失败后手动输入。
- SRS-UI-004：PTT Checkbox 显隐与独立 PTT 按钮。
- SRS-UI-005：Vision Checkbox、FPS Selector、VisionResultPanel 显隐。
- SRS-UI-006：对话信息页面入口、空态、记录列表、折叠展开。
- SRS-UI-007：深色模式和 contentDescription。

### 17.5 端到端测试

必须覆盖：

- SRS-E2E-001：Agent ID 语音问答。
- SRS-E2E-002：复杂配置默认值语音问答。
- SRS-E2E-003：修改发音人后确认 TTS 音色变化。
- SRS-E2E-004：文本问答。
- SRS-E2E-005：图片 URL 问答。
- SRS-E2E-006：Server VAD 打断 TTS 后播放停止。
- SRS-E2E-007：PTT 正常语音、误触、打断。
- SRS-E2E-008：Vision + Voice 上传 JPEG 并收到 `response.video.done`。
- SRS-E2E-009：Video 失败时语音继续。
- SRS-E2E-010：对话信息页断开后可回看，下一轮开始清空。

## 18. 需求追踪

- PRD 7.1 初始化与配置 -> SRS-CFG。
- PRD 7.2 Realtime 生命周期 -> SRS-API、SRS-STATE、SRS-REC。
- PRD 7.3 Session 配置 -> SRS-MODEL。
- PRD 7.4 文本与图片 -> SRS-PROTO、SRS-MODEL ResponseConfig。
- PRD 7.5 音频输入 -> SRS-AUDIO-IN、SRS-AUDIO-CAP。
- PRD 7.6 音频播放 -> SRS-AUDIO-OUT。
- PRD 7.7 音频状态 -> SRS-AUD、SRS-STATE Capture/Playback。
- PRD 7.8 响应事件解析 -> SRS-EVT、SRS-PROTO。
- PRD 7.9 函数调用 -> SRS-TOOL、SRS-EVT。
- PRD 7.10 视频上传 -> SRS-VID、SRS-VIDEO。
- PRD 7.11 错误、重连、诊断 -> SRS-ERR、SRS-REC、SRS-DIAG。
- PRD 8.2-8.4 配置中心 -> SRS-SAMPLE-SET、SRS-SAMPLE-ADV、SRS-SAMPLE-TTS。
- PRD 8.5 PTT -> SRS-SAMPLE-PTT。
- PRD 8.6 Vision + Voice -> SRS-SAMPLE-VV、SRS-SAMPLE-VR。
- PRD 8.7 对话信息页 -> SRS-SAMPLE-CIP。
- PRD 8.8 UI -> SRS-SAMPLE-UI。

## 19. 实现优先级

P0 必须完成：

- Realtime 连接、鉴权、状态机。
- Session 配置序列化，含 Agent ID、高级字段、unknownFields、显式 null。
- 事件 parser，含 Unknown 透传。
- 文本输入、response.create。
- PCM append、commit、clear。
- ToolConfig 支持 `function`、`mcp`、`ticos_mcp`。
- 错误码、日志脱敏、资源释放。
- Sample 设置页 Agent ID 模式。

P1 必须完成：

- AudioRecord 采集、AudioTrack 播放。
- 采集 PCM/RMS 输出。
- 播放中断与 AudioTrack 恢复。
- 移除 `AudioState`，使用采集/播放双状态流。
- Sample PTT。
- Sample 复杂 Session 配置与 DataStore 迁移。

P2 必须完成：

- Video WebSocket、JPEG packetizer、FPS 限制。
- Vision + Voice Sample。
- TTS API 发音人选择。
- 对话信息页面。
- Sample UI 统一和深色模式。

P3 可后续完成：

- 完整重连策略。
- CameraX helper 下沉到 SDK 可选模块。
- 本地函数调用结果闭环。
- 本地音频片段缓存与回放。
- Maven 发布自动化与 API 文档站点。
