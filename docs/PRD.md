# Stardust Android SDK PRD

## 1. 文档定位

本文是 Stardust Android SDK 与 Sample App 的新基线产品需求文档，整合 `android-sdk/docs` 目录下逐步新增的能力需求。后续 SDK、公用模型、Sample App、测试与验收均以本文、`SRS.md`、`TECH_DESIGN.md` 为统一基线。

本文覆盖两类交付：

- `stardust-sdk`：Android Kotlin SDK，封装 Stardust Realtime 与 Video WebSocket 协议、音频采集播放、视频帧发送、事件解析、错误与诊断。
- `sample`：示例应用，作为 SDK 能力演示、联调与验收入口，包含语音对话、PTT、视觉语音、复杂 Session 配置、对话信息页与统一 UI 体验。

## 2. 背景

Stardust 已提供 Realtime WebSocket API 与 Video WebSocket API，用于实时文本、语音、图片输入、模型响应、函数调用和视频感知事件。Android 端若直接对接协议，需要自行处理鉴权、WebSocket 生命周期、PCM 音频采集/播放、事件序列、视频帧封包、会话配置、错误恢复与权限生命周期，接入成本较高且容易产生不一致实现。

本项目提供一套 Android SDK 与 Sample App：SDK 降低业务接入成本，Sample App 展示协议能力、交互模式与调试工具，便于开发者快速验证端到端链路。

参考来源：

- `stardust/src/websockethandlers/realtime_handler.py`
- `stardust/src/websockethandlers/video_handler.py`
- `stardust/docs/realtime_api_reference.md`
- `stardust/docs/session_config.md`
- `stardust/docs/api.md`
- `android-sdk/docs` 下各增量需求与技术设计文档

## 3. 产品目标

1. 提供 Kotlin-first 的 Android SDK，封装 Stardust Realtime 与 Video 协议，降低移动端、机器人终端、多模态设备接入复杂度。
2. 对齐 Stardust 服务端当前协议，支持 `session.update`、文本、图片、音频、视频、函数调用、错误事件和未知事件透传。
3. 提供稳定的音频能力：PCM16 采集、上行、下行播放、播放打断、采集侧 PCM/RMS 输出，以及采集/播放双状态流。
4. 提供视频能力：独立 `/video` 连接、JPEG 二进制封包、帧率限制、连接顺序约束、`response.video.done` 解析。
5. 提供 Sample App 演示能力：Agent ID 与复杂 Session 配置、Server VAD 与 PTT、Vision + Voice、对话信息页、真实音量动画与统一 UI 风格。
6. 提供清晰错误、日志脱敏、诊断统计、测试基线和阶段性交付定义。

## 4. 非目标

1. SDK 不实现 Stardust 服务端能力，不包含 ASR、LLM、TTS、视觉识别模型本身。
2. SDK 不提供完整 UI 组件库，不绑定 Activity、Fragment、Compose 或特定生命周期。
3. SDK 默认不持久化会话历史、音频、图片、视频帧或 raw event。
4. Sample App 不实现完整 Agent 配置后台、发音人训练、声纹注册、人脸管理或历史会话管理。
5. Sample App 不承担生产级后台长连接、前台服务、音视频文件导出和跨进程历史恢复。
6. 本地 `function_call_output` 完整闭环依赖服务端上行处理能力；首版以事件分发、配置透传和预留 API 为主。

## 5. 目标用户与核心场景

### 5.1 目标用户

- Android App 开发者：在移动端接入 Stardust 实时对话能力。
- 机器人终端开发者：在机器人设备上接入麦克风、扬声器、摄像头和视觉感知。
- IoT/多模态终端开发者：通过 Android 设备实现语音问答、图像理解、感知触发和工具调用。
- SDK 集成与联调人员：使用 Sample App 验证协议、配置、权限、错误和多模态链路。

### 5.2 核心场景

1. 语音对话：采集麦克风 PCM16 音频发送到 Stardust，播放服务端返回的 PCM16 音频流。
2. 文本对话：通过 `conversation.item.create` 与 `response.create` 发送文本并接收文本流。
3. 图片理解：发送 `input_image` URL，与文本一起触发多模态推理。
4. 视频感知：通过 `/video` WebSocket 上传 JPEG 帧，接收 Realtime 下发的 `response.video.done`。
5. Agent 接入：通过 `session.agent_id` 复用服务端 Agent 配置。
6. 复杂配置联调：在 Sample App 内配置模型、语音和听觉参数并生成 `session.update.session`。
7. PTT：通过 Sample App 的按住说话交互演示 Client VAD。
8. 对话信息复盘：按时间线查看用户语音、助手语音、文本预留和 function/tool 调用信息。

## 6. 协议依据

### 6.1 Realtime 通道

- URL：`wss://stardust.ticos.cn/realtime`
- WebSocket 子协议：`realtime`
- 消息格式：文本 JSON
- 鉴权：优先使用 Header `Authorization: Bearer <token>`；兼容查询参数 `terminal_secret=<token>`
- 身份参数：服务端可通过 token 得到 `group_id`、`robot_id`；特殊调试场景可支持 `group_id`、`robot_id`、`terminal_id`、`component_id` 查询参数

### 6.2 Video 通道

- URL：`wss://stardust.ticos.cn/video`
- 消息格式：二进制 JPEG 帧
- 鉴权：同 Realtime 通道
- 连接顺序：Realtime 需至少达到 `SessionCreated`，建议完成 `SessionUpdated` 后再连接 `/video`
- 失败策略：Video 失败不得中断 Realtime 主语音链路，Sample App 应降级为纯语音

### 6.3 音频格式

- 输入音频：PCM16、24kHz、单声道、16-bit signed little-endian，经 base64 后通过 `input_audio_buffer.append` 发送。
- 输出音频：PCM16、24kHz、单声道、16-bit，服务端通过 `response.audio.delta` 返回 base64 数据。
- 默认采集分片：40ms，即 1920 bytes。
- `response.audio.delta.delta == null` 时 SDK 不得写入播放器。

### 6.4 视频帧格式

SDK 上传到 `/video` 的二进制消息需与服务端 `unpack_video_message` 对齐：

- Byte 0：同步头，固定 `0x54`
- Byte 1：消息类型，固定 `0x20`
- Byte 2-5：`seq_id`，UInt32 Little Endian
- Byte 6-9：`msg_length`，UInt32 Little Endian，表示真实 JPEG payload 长度
- Byte 10 至倒数第 2 字节：JPEG 数据
- 最后 1 字节：SDK 追加 pad 字节，建议 `0x00`

服务端当前通过 `message[10:-1]` 读取 JPEG 数据，因此 SDK 必须在真实 JPEG payload 末尾追加 1 个 pad 字节，避免服务端切掉 JPEG 原始 `0xD9` EOI 标记。

## 7. SDK 功能需求

### 7.1 初始化与配置

SDK 需支持：

- 配置 Realtime 与 Video base URL。
- 配置 token provider 与兼容 terminal secret provider。
- 配置可选身份查询参数。
- 配置连接超时、心跳、重连策略、日志等级。
- 配置自动播放音频、Realtime 自动重连、Video 自动重连与 Video 连接策略。

### 7.2 Realtime 会话生命周期

SDK 需暴露明确状态：

- `Idle`
- `Connecting`
- `Connected`
- `SessionCreated`
- `SessionUpdated`
- `Reconnecting`
- `Closing`
- `Closed`
- `Failed`

必需能力：

- `connect()` 建立 Realtime WebSocket，携带鉴权与子协议。
- `updateSession(session)` 发送 `session.update`。
- `close()` 主动关闭会话并释放 Realtime、Video、AudioRecord、AudioTrack 和协程资源。
- `sendRawEvent(json)` 作为调试和协议扩展逃生口。
- 连接关闭或失败时停止采集、播放和视频发送。

### 7.3 Session 配置模型

SDK 需提供类型安全的 `SessionConfig`，同时允许未知字段扩展。首版必须支持：

- `agent_id`
- `model.provider`
- `model.name`
- `model.modalities`
- `model.instructions`
- `model.ext_config`
- `model.include_initial_prompt`
- `model.initial_user_prompt`
- `model.initial_assistant_prompt`
- `model.history_conversation_length`
- `model.temperature`
- `model.top_p`
- `model.top_k`
- `model.max_response_output_tokens`
- `model.use_inner_tools`
- `model.use_inner_view_tools`
- `model.emotion_classifier`
- `model.tool_choice`
- `model.tools`
- `model.messages.nobody`
- `speech.voice`
- `speech.emotion`
- `speech.output_audio_format`
- `speech.speed_ratio`
- `speech.pitch_ratio`
- `speech.volume_ratio`
- `hearing.input_audio_format`
- `hearing.turn_detection`
- `hearing.turn_voiceprint`
- `vision.enable_face_detection`
- `vision.enable_face_identification`
- `vision.enable_gesture_detection`
- `vision.enable_object_detection`
- `vision.object_detection_target_classes`
- `vision.face_album_id`
- `knowledge.memories.enable`
- `knowledge.scripts`
- `knowledge.retrieval`
- `webhook.url`
- `webhook.events`
- `webhook.headers`
- `webhook.custom_fields`
- `webhook.enabled`
- `triggers`
- `extra`

要求：

- 支持 Agent ID 最小配置。
- 支持复杂 Tiwater 协议配置。
- 支持 OpenAI 兼容配置与未知字段透传。
- 支持显式 `null`，例如 PTT 模式下 `hearing.turn_detection = null`。
- `unknownFields` 不得覆盖强类型字段。

### 7.4 文本与图片输入

SDK 需封装：

- `sendText(text, userId, previousItemId)`
- `sendImage(imageUrl, prompt, userId, previousItemId)`
- `sendMultimodalMessage(text, imageUrls, userId, previousItemId)`
- `createResponse(response)`
- `cancelResponse(userId)`

要求：

- 支持特殊 `previous_item_id`：`initial_user_prompt`、`initial_assistant_prompt`。
- 仅图片无文本时，服务端会自动填充默认看图提示，SDK 文档需说明该行为。
- 默认不自动调用 `response.create`，由业务显式触发；Sample 的 PTT 在 commit 后必须调用 `createResponse()` 拉起模型回复。

### 7.5 音频输入

SDK 需提供音频采集和手动推送两种使用方式：

- 使用 Android `AudioRecord` 采集 PCM16。
- 支持 24kHz 单声道采集；设备不支持时需重采样或返回明确错误。
- 将音频分片 base64 后发送 `input_audio_buffer.append`。
- 支持 `commit()` 与 `clear()`。
- 支持 Server VAD 持续发送音频。
- 支持 Client VAD 由业务控制开始、结束和 commit。
- 对外输出本地采集 PCM：`capturedPcm: SharedFlow<ByteArray>`。
- 对外输出真实采集音量：`captureLevel: StateFlow<Float>`，RMS 归一化到 `[0.0, 1.0]`，停止采集后归零。

采集侧输出要求：

- 格式为 PCM16、24kHz、mono、16-bit signed little-endian。
- 默认每 40ms 发射一帧，约 1920 bytes。
- 不进入 `events` 事件流，避免污染服务端事件语义。
- 慢消费者不得阻塞采集线程，可丢弃最旧帧。

### 7.6 音频播放

SDK 需提供 `response.audio.delta` 播放辅助：

- 解码 base64 PCM16。
- 使用 `AudioTrack` 流式播放 24kHz 单声道 PCM16。
- 支持播放队列、停止、清空、启停自动播放。
- 收到 `response.audio.done` 标记当前音频响应完成。
- 收到 `input_audio_buffer.speech_started` 或主动 `cancelResponse()` 时，必须立即 stop/flush/clear 当前播放队列。
- `AudioTrack` stop 后，后续新音频到达时必须能安全恢复播放。

### 7.7 音频状态模型

SDK 基线不再暴露聚合 `AudioState`。

要求：

- `StardustAudio` 只暴露采集侧 `captureState: StateFlow<CaptureAudioState>` 与播放侧 `playbackState: StateFlow<PlaybackAudioState>`。
- 双状态流用于表达全双工场景，例如 `Recording + Playing`。
- 业务展示层自行决定播放优先、录音优先或复合态；SDK 不用信息有损的聚合状态替业务裁剪。

### 7.8 响应事件解析

SDK 需强类型解析并保留 raw JSON。首版必须支持：

- `error`
- `session.created`
- `session.updated`
- `conversation.created`
- `conversation.item.created`
- `conversation.item.input_audio_transcription.completed`
- `conversation.item.input_audio_transcription.failed`
- `input_audio_buffer.committed`
- `input_audio_buffer.cleared`
- `input_audio_buffer.speech_started`
- `input_audio_buffer.speech_stopped`
- `response.created`
- `response.done`
- `response.output_item.added`
- `response.output_item.done`
- `response.content_part.added`
- `response.content_part.done`
- `response.text.delta`
- `response.text.done`
- `response.audio_transcript.delta`
- `response.audio_transcript.done`
- `response.audio.delta`
- `response.audio.done`
- `response.function_call_arguments.done`
- `response.video.done`

未知或暂未完整实现事件必须透传为 `Unknown`，不得导致崩溃。

### 7.9 函数调用与工具配置

SDK 首版需能接收函数调用事件，支持本地函数调用事件分发，并完整支持服务端执行型工具配置。

要求：

- `ToolConfig` 必须支持 `function`、`mcp`、`ticos_mcp`。
- `mcp` 支持 `server_label`、`server_url`、`server_description`、`allowed_tools`、`require_approval`、`authorization` 等字段。
- `ticos_mcp` 支持 `name`、`description`、`parameters`、`server_url`、`mcp_server_id`、`mcp_api_key`、`operation_mode`、`execution_type`、`result_handling` 等字段。
- Android 客户端通常不执行 `mcp` / `ticos_mcp` 工具逻辑，工具发现、执行和结果回传由 Stardust 服务端完成。
- 本地 `function` 调用结果回传 API 可预留。

### 7.10 视频上传

SDK 需封装 `/video` 通道：

- 独立管理 Video WebSocket。
- 提供 `connect()`、`sendJpegFrame()`、`disconnect()`。
- `connect()` 必须等待或校验 Realtime 状态达到策略要求。
- 未就绪时不得静默连接或静默丢帧，应挂起、排队或返回明确错误。
- 内部完成二进制封包和 pad 字节追加。
- 支持帧率限制，默认建议 2 FPS，可配置 1-30 FPS。
- 支持业务传入已编码 JPEG；CameraX helper 可在 Sample 或 SDK helper 层提供。

### 7.11 错误、重连与诊断

SDK 需区分协议错误、鉴权错误、网络错误、音频设备错误和视频错误。

要求：

- 401 鉴权失败不自动无限重连，回调业务刷新 token 或提示登录。
- 网络断开可按指数退避自动重连。
- 重连成功后可按配置自动重发最近一次 `session.update`。
- 重连期间不得继续采集并发送音频；Video 必须等待 Realtime 再次就绪。
- JSON 解析失败保留原始消息并回调。
- 服务端 `error` 同时作为协议事件和 SDK 错误派发。
- 提供日志等级、敏感字段脱敏、指标统计和 `StardustDiagnostics` 快照。

### 7.12 安全与隐私

要求：

- token 仅通过 Header 或必要查询参数发送，不写入普通日志。
- 默认不落盘音频、图片、视频帧和 raw event。
- 日志与 UI 脱敏 `Authorization`、`terminal_secret`、`token`、`secret`、`password`、API Key、MCP authorization 等字段。
- SDK 文档提示业务申请 `INTERNET`、`RECORD_AUDIO`、`CAMERA` 权限。

## 8. Sample App 功能需求

### 8.1 基础定位

Sample App 是 SDK 能力的演示、调试和验收入口，需保持低门槛、可配置、可观察、可复现。Sample 新增能力不应改变 `stardust-sdk` 公共 API 语义，除非对应能力已明确纳入 SDK 基线。

### 8.2 配置中心

配置中心需包含：

- Server URL
- Terminal Secret
- Group ID
- Robot ID
- Auto Play Audio
- Session 配置模式
- Agent ID 配置或复杂 Session 配置摘要

通用校验：

- `serverUrl` 必须以 `ws://` 或 `wss://` 开头。
- `terminalSecret` 与 `groupId + robotId` 二选一。
- Terminal Secret 输入框默认隐藏并支持显示/隐藏切换。
- 终端密钥不得出现在日志、Snackbar、摘要卡片或错误提示中。

### 8.3 Session 配置模式

Sample App 支持两种互斥模式：

- Agent ID 模式：默认模式，连接时只发送 `session.agent_id`。
- 复杂配置模式：本地表单生成 `model`、`speech`、`hearing` 配置，连接时不发送 `agent_id`。

要求：

- 切换模式不清空另一模式已保存配置。
- 新增配置持久化到 DataStore，App 重启后恢复。
- 老数据缺失新增字段时使用推荐默认值。
- 复杂配置保存只写本地，不立即连接。

### 8.4 复杂 Session 配置

复杂配置首版覆盖 Voice Agent 所需模型、语音、听觉配置。

模型配置：

- `model.provider`：必填，默认 `tiwater`。
- `model.name`：必填，默认 `stardust-2.5-max` 或当前推荐实时模型。
- `model.modalities`：固定 `text` + `audio`。
- `model.instructions`：必填，默认中文友好语音助手提示词，建议最大 8000 字符。
- `model.temperature`：默认 `0.7`，范围 `[0.01, 1.0]`。
- `model.top_p`：默认 `1.0`，范围 `[0.0, 1.0]`。
- `model.top_k`：默认 `40`，正整数。
- `model.max_response_output_tokens`：默认 `1024`，正整数。
- `model.history_conversation_length`：默认 `30`，范围 `0..30`。

语音配置：

- `speech.voice`：必填，默认 `zh_female_wanwanxiaohe_moon_bigtts`。
- `speech.output_audio_format`：固定 `pcm16`。
- `speech.emotion`：默认 `neutral`，可选 `neutral/happy/sad/angry/surprised/fearful/disgusted`。
- `speech.speed_ratio`、`speech.pitch_ratio`、`speech.volume_ratio`：默认 `50`，范围 `1..100`。

听觉配置：

- `hearing.input_audio_format`：固定 `pcm16`。

发音人选择：

- 通过 Stardust TTS API `GET /tts` 动态获取发音人列表。
- 支持 `language`、`gender`、`provider`、`tags`、`name`、`skip`、`top`、`all` 查询。
- 默认请求中文推荐发音人，如 `GET /tts?language=chinese&skip=0&top=20`。
- 接口失败时允许手动输入 voice ID。
- 不上传训练音频，不展示或保存训练音频。

### 8.5 Push to Talk

Sample App 支持 PTT 作为 Client VAD 的交互演示。

要求：

- 未连接时在主 Orb 下方显示 `Push to Talk（按住说话）` Checkbox，默认不勾选。
- 已连接期间隐藏或禁用 Checkbox，会话内不得切换模式。
- 断开后再次显示并保留同一进程内选择。
- PTT 模式连接时发送 `hearing.turn_detection = null`。
- PTT 模式连接成功后不自动 `startCapture()`，仅在用户按住专用 PTT 区域时采音。
- 主 Orb 仅负责连接/断开，不承担 PTT 采音。
- PTT 专用区域仅在 PTT 且已连接时显示，按下 `startCapture()`，松开按规则提交或清空。
- 固定误触阈值 `FALSE_TOUCH_MS = 250`。
- 按住时长 `< 250ms`：`stopCapture(commit = false)` + `clear()`，不调用 `cancelResponse()`。
- 按住时长 `>= 250ms`：本轮按住内至多一次 `cancelResponse()` 打断助手，松开时 `stopCapture(commit = true)`，随后调用 `createResponse()`。
- PTT commit 后必须调用 `createResponse()`，因为 `input_audio_buffer.commit` 只提交音频并触发转写，不会自动生成模型回复。
- PTT 间隙释放麦克风。

### 8.6 Vision + Voice

Sample App 支持视觉语音多模态演示。

要求：

- 未连接时显示 `启用视觉（摄像头）` Checkbox，默认关闭，进程内保留选择。
- 视觉已启用且未连接时可选择 FPS：1 / 2 / 5，默认 2 FPS。
- 已连接期间隐藏或禁用视觉开关和 FPS 选择，会话内不得切换视觉模式。
- 启用视觉时需检查 `CAMERA` 权限；PTT + Vision 同时启用时需分别检查 `RECORD_AUDIO` 与 `CAMERA`。
- 设备无摄像头、权限拒绝、Video 连接失败或发送失败时，提示并降级为纯语音。
- 连接顺序为 `client.connect()` -> `client.updateSession()` -> `client.video.connect()` -> 启动 CameraX 并发送 JPEG。
- 视觉主路径不强制在 `session.update` 中写入 `vision.enable_*` 字段；如需细粒度控制，可通过 `VisionConfig.unknownFields` 透传。
- CameraX JPEG 编码与发送不得阻塞主线程。
- App 进入后台时停止摄像头采集与帧发送。
- 主界面展示最近 N 条 `response.video.done` 视觉结果摘要，建议 N=10。

视觉结果展示字段：

- 人脸：`face_info.faces` 数量、置信度、bbox。
- 物体：`object_info.objects[].label/confidence/bbox`。
- 手势：`hand_info.hands[].gesture/confidence`。
- 图像：`image_info.description`、`image_info.labels`。

### 8.7 对话信息页面

Sample App 新增对话信息页面，用于开发、联调和演示。

要求：

- 主页面提供进入「对话信息」页面的入口，返回不影响当前会话。
- 未开始对话时展示空态。
- 每次新对话开始时清空上一轮信息。
- 对话终止、主动断开、连接失败或 ViewModel 清理时不得立即清空，便于回看。
- 不要求跨进程持久化。
- 由 `VoiceViewModel` 或同生命周期状态持有者统一维护，不能为页面单独创建第二套 SDK client。

记录类型：

- `UserVoice`：用户语音、ASR/转写文字、时间、音频占位或片段摘要。
- `UserText`：用户文本消息预留类型。
- `AssistantVoice`：助手语音、文本、时间、音频片段摘要。
- `FunctionCall`：function/tool 名称、参数、时间、调用 id 或事件摘要。

展示要求：

- 时间格式建议 `HH:mm:ss`。
- Function 参数格式化 JSON；非法 JSON 展示原始字符串，不崩溃。
- 长文本和长参数支持折叠/展开。
- 参数中疑似敏感字段需脱敏。
- 默认按时间升序或最新在下方展示。
- 顶部展示统计：总数、用户语音、用户文本、助手语音、function 数量。
- 列表设置内存上限，建议最多 100 条。
- 快速断开/重连时旧 client 延迟事件不得污染新一轮列表。

### 8.8 UI 设计基线

Sample App 应统一视觉语言和交互规则：

- 统一使用 Material3 `TopAppBar` 风格。
- 输入控件统一采用 Filled 样式；数字输入使用数字键盘。
- 内容使用分组卡片表达层级，避免长表单裸露堆叠。
- 主操作区位置保持一致，连接/断开仍以 Orb 为核心。
- 复杂配置页面操作按钮位置清晰，保存、返回、恢复默认值不混淆。
- `SessionConfigModeSelector`、PTT Checkbox、Vision Checkbox 风格一致。
- 支持深色模式语义色 Token 和统一间距 Token。
- 可访问性：主 Orb、PTT 区域、Vision 开关、配置入口、对话信息入口均需有 contentDescription。
- 用户可见字符串进入资源文件，保持中英文策略一致。

## 9. 对外 API 草案

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

interface StardustVideo {
    val state: StateFlow<VideoState>
    suspend fun connect()
    suspend fun sendJpegFrame(jpeg: ByteArray)
    suspend fun disconnect()
}
```

## 10. 典型流程

### 10.1 Agent ID 语音对话

1. 创建 `StardustClient` 并配置 token。
2. `connect()` 建立 Realtime。
3. 收到 `session.created`。
4. `updateSession(SessionConfig(agentId = "..."))`。
5. 收到 `session.updated`。
6. Server VAD 模式调用 `audio.startCapture()`。
7. SDK 自动播放音频 delta，同时派发文本、ASR、响应和错误事件。

### 10.2 复杂配置语音对话

1. Sample 设置页选择复杂配置。
2. 编辑模型、发音人、语音参数和听觉参数。
3. 保存到 DataStore。
4. 点击 Orb 连接时构建 `SessionConfig(model, speech, hearing)`，不发送 `agent_id`。
5. 完成语音问答并验证发音人与模型参数生效。

### 10.3 PTT 对话

1. 未连接时勾选 PTT。
2. 连接时发送 `hearing.turn_detection = null`。
3. 不自动采集。
4. 用户按住 PTT 区域开始采集。
5. 达 250ms 后至多一次 `cancelResponse()`。
6. 松开后 commit；短按误触则 clear。
7. 服务端完成 ASR 与响应。

### 10.4 Vision + Voice

1. 未连接时启用视觉并选择 FPS。
2. Realtime 连接并更新 session。
3. Video 按策略连接。
4. CameraX 采集 JPEG 并按 FPS 上传。
5. Realtime 收到 `response.video.done` 后展示结果。
6. Video 异常时停止视觉并保留语音对话。

### 10.5 对话信息复盘

1. 新对话开始时清空上一轮对话信息。
2. 监听用户语音、助手语音、function/tool 事件并生成记录。
3. 用户进入对话信息页查看时间线。
4. 断开后保留记录，下一次新对话开始再清空。

## 11. 验收标准

### 11.1 SDK 功能验收

- 可使用 token 成功连接 Realtime 并收到 `session.created`。
- 可发送 Agent ID 配置并收到 `session.updated`。
- 可发送复杂 Session 配置，含模型、语音、听觉、高级字段和未知字段。
- 可序列化 `function`、`mcp`、`ticos_mcp` 工具配置且不丢字段。
- 可发送文本、图片、多模态消息并接收响应事件。
- 可采集 PCM16、发送 append、commit、clear。
- 可接收并播放 `response.audio.delta`。
- 可在 `input_audio_buffer.speech_started` 或 `cancelResponse()` 时立即停止播放。
- 可通过 `capturedPcm` 获取采集帧，通过 `captureLevel` 获取真实 RMS 音量。
- 不再暴露 `AudioState` 聚合状态，只暴露 `captureState` 与 `playbackState`。
- Realtime 未就绪时 Video 连接和发帧不静默丢弃。
- 可连接 `/video` 并上传包含 pad 字节的 JPEG packet。
- 可解析 `response.video.done`。
- 可处理 `error`、401、断线、JSON 解析失败和未知事件。

### 11.2 Sample 功能验收

- 设置页可切换 Agent ID / 复杂配置，二者互斥生效。
- 复杂配置可持久化，重启后恢复。
- 发音人列表可通过 TTS API 获取，失败时可手动输入 voice ID。
- PTT 默认关闭；连接前可选，连接后不可改；短按误触和 250ms 打断规则正确。
- PTT commit 后会调用 `createResponse()` 显式拉起模型回复。
- Vision 默认关闭；可选 1/2/5 FPS；Video 失败可降级纯语音。
- Server VAD、PTT、Vision + Voice 能组合运行。
- 对话信息页可展示用户语音、助手语音和 function/tool 调用，断开后保留，下一轮开始清空。
- 真实 `captureLevel` 替代模拟音量动画。
- UI 风格、字符串资源、深色模式、可访问性符合基线。

### 11.3 性能与兼容性验收

- 最低支持 Android 8.0（API 26）。
- Kotlin-first API，Java 可调用。
- WebSocket 收发、音频采集/播放、JPEG 编码与发送不阻塞主线程。
- 连续 10 分钟语音对话无明显内存增长。
- 默认视频帧率下主界面无明显卡顿。
- 单次 `close()` 后无 AudioRecord、AudioTrack、WebSocket、CameraX、协程泄漏。
- 日志、诊断、UI 不泄露敏感字段。

## 12. 里程碑

### M1：协议核心

- Android library 工程。
- Realtime 连接、鉴权、状态机。
- Session、Tool、Response 模型和序列化。
- 事件 parser 与 Unknown 透传。
- 文本、图片、response.create。
- PCM append/commit/clear。
- 错误模型、日志脱敏、基础测试。

### M2：语音能力

- AudioRecord 采集与 AudioTrack 播放。
- 采集 PCM/RMS 对外输出。
- 播放中断与队列恢复。
- `AudioState` 移除，使用采集/播放双状态流。
- Server VAD 与 PTT Sample。

### M3：多模态与 Sample 配置

- Video WebSocket、JPEG packetizer、帧率限制。
- `response.video.done` 解析。
- Vision + Voice Sample。
- 复杂 Session 配置、TTS 发音人选择、DataStore 迁移。
- 对话信息页面。

### M4：稳定性与发布

- 完整重连策略。
- 诊断统计补齐。
- Sample UI 统一与深色模式。
- README、API 文档、迁移指南。
- Maven artifact 发布。

## 13. 风险与待确认项

1. Video 通道依赖 Realtime 会话状态，SDK 必须强制连接顺序，不能只依赖文档约束。
2. `function_call_output` 服务端上行闭环仍需确认；`mcp` / `ticos_mcp` 服务端执行路径应优先完整支持。
3. Android 设备对 24kHz 录音支持不一致，需确认重采样是否纳入首版强制能力。
4. Video 二进制协议的尾字节行为依赖服务端当前切片逻辑；SDK 需保持 pad 兼容。
5. TTS API 鉴权、baseUrl 派生和分页字段需与服务端最终接口确认。
6. Sample App 视觉链路的 CameraX 依赖版本与设备兼容性需在真机验证。
7. 对话信息页如未来加入本地音频缓存或导出，需要重新评估隐私与脱敏策略。
