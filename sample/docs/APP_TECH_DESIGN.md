# Stardust Voice Agent Sample App 技术实现文档

## 1. 概述
本文档详细说明了 Stardust Voice Agent Sample App 的技术架构、核心组件实现以及与 `stardust-sdk` 的集成方式。本应用遵循 MVVM 架构，采用 Jetpack Compose 进行声明式 UI 开发。

## 2. 架构设计 (Architecture)

### 2.1 整体分层
*   **View (Compose)**：负责 UI 渲染、动画执行及用户事件采集（点击、手势）。
*   **ViewModel**：负责维护 UI 状态（StateHolder），调用 SDK API，并将 SDK 的状态流（Flow）转换为 UI 可用的状态。
*   **Model (SDK)**：`stardust-sdk` 提供的 `StardustClient` 及其相关能力。

### 2.2 数据流 (Data Flow)
1.  **用户操作**：用户点击语音球 -> ViewModel 调用 `client.connect()`。
2.  **状态反馈**：SDK 状态改变 -> ViewModel 观测 `client.state` -> 更新 `UiState` -> Compose 重组渲染动画。
3.  **音频交互**：SDK 内部处理 `AudioRecord` 与 `AudioTrack`，ViewModel 仅监听播放/录音状态以更新 UI。

## 3. 核心组件实现

### 3.1 VoiceViewModel
作为 UI 与 SDK 的桥梁，承担以下职责：
*   **初始化 SDK**：持有 `StardustClient` 实例。
*   **状态聚合**：
    ```kotlin
    val uiState: StateFlow<VoiceUiState> = combine(
        client.state,
        client.audio.captureState,
        client.audio.playbackState,
        // 其他状态...
    ) { sdkState, captureState, playbackState ->
        mapToUiState(sdkState, captureState, playbackState)
    }.stateIn(...)
    ```
*   **生命周期管理**：在 `onCleared` 时调用 `client.close()`。

### 3.2 VoiceHub (UI 组件)
位于主界面中央，是交互的核心。
*   **The Orb (圆球)**：
    *   使用 `Canvas` 或 `Box` 配合 `graphicsLayer` 实现缩放动画。
    *   使用 `Animatable` 处理状态切换时的颜色平滑过渡（例如：灰色 -> 蓝色）。
*   **动画逻辑**：
    *   `Listening` 状态：开启 `InfiniteTransition` 实现呼吸效应。
    *   `Speaking` 状态：根据 `client.diagnostics` 中的实时音量数据（若有）或模拟波纹，实现扁平化扩散热点。

### 3.3 权限管理 (Permission Handler)
*   使用 `Accompanist Permissions` 或原生 `ActivityResultLauncher`。
*   在进入对话前静默检查 `RECORD_AUDIO` 权限，未获得权限时点击圆球弹出请求。

## 4. 状态映射逻辑
SDK 状态较为细碎，需要映射为 UI 表现层状态：

| SDK 状态 (`StardustState` / `CaptureAudioState` / `PlaybackAudioState`) | UI 表现状态 (`VoiceUiState`) | UI 特效 |
| :--- | :--- | :--- |
| `Idle` / `Closed` | `Ready` | 灰色圆圈，静止 |
| `Connecting` | `Connecting` | 蓝色圆环旋转 (Spinning) |
| `Connected` / `SessionCreated` | `Idle` (已连接) | 蓝色圆圈，静止 |
| `CaptureAudioState.Recording` / `Stopping`（且未处于播放/思考优先分支） | `Listening` | 蓝色圆球 + 呼吸动画 |
| `StardustState.SessionUpdated`（等候）等 + `awaitingResponse` | `Thinking` | 蓝色圆圈 + 内部微波纹或旋转 |
| `PlaybackAudioState.Playing` | `Speaking` | 蓝色圆球 + 外部扁平波纹扩散 |
| `Failed`（连接或 `PlaybackAudioState.Failed`） | `Error` | 红色圆圈，点击可重连 |

## 5. 配置管理 (Persistence)
*   使用 `DataStore` 存储 `agent_id`、`server_url`、`terminal_secret`、`group_id`、`robot_id`、`auto_play` 等关键配置。
*   `server_url` 在设置页保存与主界面发起连接前均做格式校验，需以 `ws://` 或 `wss://` 开头。
*   应用启动时从 DataStore 读取配置并构造 `StardustConfig`。

## 6. 与 SDK 集成要点
*   **WSS 鉴权（与 Stardust 服务端 `websocket_handler_base.py` 一致）**：
    *   **终端密钥**：将密钥写入连接 URL 的查询参数 `terminal_secret`（通过 `StardustConfig.terminalSecretProvider`）。此时 `tokenProvider` 返回空字符串，SDK **不**发送 `Authorization` 头，由服务端用 `terminal_secret` 走 `verify_robot_ext`。
    *   **无终端密钥（开发/联调）**：`tokenProvider` 返回固定字面量 `X-Tiwater-Debug`，并发送 `Authorization: Bearer X-Tiwater-Debug`，同时在 URL 上附带 `group_id`、`robot_id`（`StardustConfig.queryParams`）。
*   **音频策略**：Sample App 默认开启 `autoPlayAudio = true`，以展示 SDK 的全双工自动播放能力。
*   **打断逻辑**：利用 SDK 内置的 `server_vad`，应用层无需额外干预播放中断，通过 `captureState` 与 `playbackState` 更新 UI。

## 7. 错误处理与日志
*   **错误弹窗**：通过 `LaunchedEffect` 收集 `client.errors` SharedFlow，使用 `Snackbar` 或简单的文本提示展示。
*   **实时转写面板**：监听 `client.events`，过滤出文本相关的事件，维护一个最近 N 条记录的列表展示在屏幕底端。
