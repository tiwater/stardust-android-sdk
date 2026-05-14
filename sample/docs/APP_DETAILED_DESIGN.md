# Stardust Voice Agent Sample App 系统详细设计文档

## 1. 引言

### 1.1 文档目的

本文档为 Stardust Voice Agent Sample App 的系统详细设计文档，在产品设计文档（`APP_DESIGN.md`）和技术实现文档（`APP_TECH_DESIGN.md`）基础上，对模块划分、类设计、状态机、数据流、接口定义、UI 组件实现等进行逐一展开，为后续编码实现提供明确的技术蓝图。

### 1.2 术语与缩写

| 术语 | 说明 |
| :--- | :--- |
| SDK | Stardust Android SDK (`stardust-sdk`) |
| Orb | 语音交互中心圆球，应用核心视觉组件 |
| VAD | Voice Activity Detection，语音活动检测 |
| PCM | Pulse-Code Modulation，脉冲编码调制 |
| Flow | Kotlin Coroutines 中的异步数据流 |
| Compose | Jetpack Compose，Android 声明式 UI 框架 |

### 1.3 参考文档

| 文档 | 路径 |
| :--- | :--- |
| 产品设计文档 | `android-sdk/sample/docs/APP_DESIGN.md` |
| 技术实现文档 | `android-sdk/sample/docs/APP_TECH_DESIGN.md` |
| SDK 源码 | `android-sdk/stardust-sdk/src/` |

---

## 2. 系统架构

### 2.1 分层架构总览

应用采用 **MVVM (Model-View-ViewModel)** 架构，三层之间通过单向数据流连接：

```
┌─────────────────────────────────────────────────┐
│                  View Layer                      │
│   (Jetpack Compose: Screen / Component / Theme)  │
│                                                   │
│   MainScreen ── VoiceHub ── SettingsPanel         │
│                  BottomBar    TranscriptPanel      │
└──────────────────┬──────────────────────────────┘
                   │  StateFlow<VoiceUiState>
                   │  User Events (click / gesture)
┌──────────────────▼──────────────────────────────┐
│               ViewModel Layer                    │
│                                                   │
│   VoiceViewModel ── SettingsViewModel             │
│       ↕ combine / map                             │
│   UiState aggregation                             │
└──────────────────┬──────────────────────────────┘
                   │  StardustClient API
                   │  DataStore API
┌──────────────────▼──────────────────────────────┐
│                Model Layer                       │
│                                                   │
│   StardustClient (SDK)                            │
│   SettingsRepository (DataStore)                  │
└─────────────────────────────────────────────────┘
```

### 2.2 模块依赖关系

```
app (Sample App)
 ├── :stardust-sdk           // SDK 二进制依赖或模块引用
 ├── androidx.compose.*      // Compose UI & Animation
 ├── androidx.lifecycle.*    // ViewModel & Lifecycle
 ├── androidx.datastore.*    // 配置持久化
 └── kotlinx.coroutines.*   // 协程与 Flow
```

---

## 3. 工程结构设计

### 3.1 目录结构

```
sample/
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/cn/ticos/stardust/sample/
│   │   ├── App.kt                          // Application 子类
│   │   ├── MainActivity.kt                 // 入口 Activity
│   │   ├── navigation/
│   │   │   └── AppNavigation.kt            // 导航图定义
│   │   ├── ui/
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt                // 配色常量
│   │   │   │   ├── Type.kt                 // 字体排版
│   │   │   │   └── Theme.kt                // Material 主题组装
│   │   │   ├── screen/
│   │   │   │   ├── SplashScreen.kt         // 启动动画页
│   │   │   │   ├── MainScreen.kt           // 主交互页
│   │   │   │   └── SettingsScreen.kt       // 设置页
│   │   │   └── component/
│   │   │       ├── VoiceHub.kt             // 语音圆球 + 状态动画
│   │   │       ├── OrbAnimations.kt        // Orb 动画辅助函数
│   │   │       ├── BottomBar.kt            // 底部操作栏
│   │   │       ├── TranscriptPanel.kt      // 实时转写面板
│   │   │       └── StatusText.kt           // 状态文本组件
│   │   ├── viewmodel/
│   │   │   ├── VoiceViewModel.kt           // 核心业务 ViewModel
│   │   │   └── SettingsViewModel.kt        // 设置 ViewModel
│   │   ├── model/
│   │   │   ├── VoiceUiState.kt             // UI 状态数据类
│   │   │   └── TranscriptItem.kt           // 转写记录模型
│   │   ├── data/
│   │   │   └── SettingsRepository.kt       // DataStore 封装
│   │   └── util/
│   │       └── PermissionHelper.kt         // 权限请求封装
│   └── res/
│       ├── values/
│       │   ├── strings.xml
│       │   └── themes.xml
│       ├── drawable/
│       │   └── ic_launcher_foreground.xml
│       └── mipmap-*/                        // 启动图标
└── docs/
    ├── APP_DESIGN.md
    ├── APP_TECH_DESIGN.md
    └── APP_DETAILED_DESIGN.md               // 本文档
```

---

## 4. 数据模型设计

### 4.1 VoiceUiState

UI 层消费的单一状态源，由 ViewModel 聚合 SDK 多路 Flow 后产出。

```kotlin
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Ready,
    val agentName: String = "",
    val statusText: String = "点击开始对话",
    val transcripts: List<TranscriptItem> = emptyList(),
    val audioLevel: Float = 0f,        // 0.0 ~ 1.0，驱动 Speaking 波纹幅度
    val errorMessage: String? = null,
)
```

### 4.2 VoicePhase（UI 表现状态枚举）

```kotlin
enum class VoicePhase {
    Ready,        // 灰色圆圈，等待连接
    Connecting,   // 蓝色圆环旋转
    Idle,         // 已连接、蓝色圆圈静止
    Listening,    // 蓝色圆球 + 呼吸动画
    Thinking,     // 蓝色圆圈 + 内部旋转微波纹
    Speaking,     // 蓝色圆球 + 外部波纹扩散
    Error,        // 红色圆圈
}
```

### 4.3 TranscriptItem

```kotlin
data class TranscriptItem(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,          // User | Agent
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class Role { User, Agent }
```

### 4.4 AppSettings（持久化配置）

```kotlin
data class AppSettings(
    val agentId: String = "",
    val serverUrl: String = DEFAULT_REALTIME_URL,
    val terminalSecret: String = "",
    val groupId: String = "",
    val robotId: String = "",
    val autoPlayAudio: Boolean = true,
)
```

与 Stardust 服务端约定（`stardust/src/websockethandlers/websocket_handler_base.py`）：**二选一**——填写 `terminalSecret` 时仅依赖查询参数 `terminal_secret`；否则必须同时填写 `groupId` 与 `robotId`，客户端使用 `Authorization: Bearer X-Tiwater-Debug` 及对应 query 连接。

---

## 5. 状态机设计

### 5.1 SDK → UI 状态映射表

优先级从高到低，第一个匹配的条件生效。

| 优先级 | SDK `StardustState` | `CaptureAudioState` | `PlaybackAudioState` | `awaitingResponse` | 映射 `VoicePhase` | 说明 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 1 | `Failed` | — | — | — | `Error` | SDK 连接本身失败 |
| 2 | `Connecting` / `Reconnecting` / `Closing` | — | — | — | `Connecting` | 连接过渡态（重连中也显示"连接中…"） |
| 3 | `Idle` / `Closed` | — | — | — | `Ready` | 初始状态或断开后 |
| 4 | 已连接 | — | `Failed` | — | `Error` | 本地播放失败（仅在 SDK 已连接时） |
| 5 | 已连接 | — | `Playing` | — | `Speaking` | Agent 正在播放音频 |
| 6 | 已连接 | — | 非 `Playing` | `true` | `Thinking` | 等待模型响应（优先于麦克风状态） |
| 7 | 已连接 | `Recording` / `Stopping` | 非 `Playing` | `false` | `Listening` | 麦克风采集中且无待响应 |
| 8 | `Connected` / `SessionCreated` / `SessionUpdated` | `Idle` | `Idle` | `false` | `Idle` | 连接就绪、空闲等待 |

### 5.2 状态转换图

```
                    connect()
   ┌─────────┐ ──────────────> ┌────────────┐
   │  Ready  │                 │ Connecting │
   └────┬────┘ <────────────── └─────┬──────┘
        │         close/fail         │ success
        │                            ▼
        │                      ┌──────────┐
        │       close()        │   Idle   │ ◄───── 播放完成 / 打断
        │  ◄────────────────── │ (已连接) │
        │                      └──┬───┬───┘
        │                         │   │
        │             开始录音     │   │  等待响应
        │                         ▼   ▼
        │                   ┌──────┐  ┌──────────┐
        │                   │Listen│→ │ Thinking │
        │                   └──────┘  └────┬─────┘
        │                                  │ 收到音频
        │                                  ▼
        │                            ┌──────────┐
        │                            │ Speaking │
        │                            └──────────┘
        │
        │         任意阶段 fail
        │ ◄──────────────────── ┌───────┐
        └───────────────────── │ Error │ ──── 点击重连 ──→ Connecting
                               └───────┘
```

### 5.3 映射实现逻辑

实际入口为 `mapToVoicePhase`，先处理本地「接线」过渡期，再委托 `mapSdkToVoicePhase`：

```kotlin
// VoiceViewModel.kt（顶层私有函数）
private fun mapToVoicePhase(
    sdkState: StardustState,
    captureState: CaptureAudioState,
    playbackState: PlaybackAudioState,
    awaitingResponse: Boolean,
    connectionWiring: Boolean,  // 本地 connect() 调用前的过渡窗口
): VoicePhase = when {
    connectionWiring && sdkState == StardustState.Idle -> VoicePhase.Connecting
    else -> mapSdkToVoicePhase(sdkState, captureState, playbackState, awaitingResponse)
}

// VoicePhaseMapping.kt
fun mapSdkToVoicePhase(
    sdkState: StardustState,
    captureState: CaptureAudioState,
    playbackState: PlaybackAudioState,
    awaitingResponse: Boolean,
): VoicePhase = when {
    sdkState == StardustState.Failed                                        -> VoicePhase.Error
    sdkState == StardustState.Connecting ||
        sdkState == StardustState.Reconnecting ||
        sdkState == StardustState.Closing                                   -> VoicePhase.Connecting
    sdkState.isDisconnected                                                 -> VoicePhase.Ready
    playbackState == PlaybackAudioState.Failed                              -> VoicePhase.Error   // SDK 已连接，但播放失败
    playbackState == PlaybackAudioState.Playing                             -> VoicePhase.Speaking
    awaitingResponse                                                        -> VoicePhase.Thinking // 优先于 Recording
    captureState == CaptureAudioState.Recording ||
        captureState == CaptureAudioState.Stopping                          -> VoicePhase.Listening
    sdkState == StardustState.Connected ||
        sdkState == StardustState.SessionCreated ||
        sdkState == StardustState.SessionUpdated                            -> VoicePhase.Idle
    else                                                                    -> VoicePhase.Ready
}
```

> `awaitingResponse` 标志位在收到 `ResponseCreated` 事件时置 `true`，在 `ResponseDone` 或 `ResponseAudioDelta` 到达时置 `false`。  
> `awaitingResponse` 排在 `Recording` 之前，确保全双工下麦克风长期开启时，等待响应阶段能正确显示"思考中…"而非"正在聆听…"。

---

## 6. 核心组件详细设计

### 6.1 VoiceViewModel

#### 6.1.1 职责

| 职责 | 说明 |
| :--- | :--- |
| SDK 生命周期管理 | 创建/销毁 `StardustClient` |
| 状态聚合 | 将 `client.state`、`client.audio.captureState`、`client.audio.playbackState`、事件流聚合为 `VoiceUiState` |
| 用户意图转发 | 将 UI 操作（连接、断开、重连）翻译为 SDK 调用 |
| 事件收集 | 从 `client.events` 提取转写文本维护列表 |
| 错误收集 | 从 `client.errors` 收集并暴露给 UI |

#### 6.1.2 关键属性与方法

```kotlin
class VoiceViewModel(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private var client: StardustClient? = null

    // ---- 对外暴露的 UI 状态 ----
    val uiState: StateFlow<VoiceUiState>

    // ---- 用户操作 ----
    fun onOrbClicked()          // 根据当前 phase 决定 connect / disconnect
    fun onReconnectClicked()    // 强制重连
    fun onDisconnect()          // 断开连接

    // ---- 内部 ----
    private fun createClient(settings: AppSettings): StardustClient
    private fun observeSdkStreams(client: StardustClient)
    override fun onCleared()    // client.close()
}
```

#### 6.1.3 状态聚合流水线

```kotlin
private fun buildUiStateFlow(client: StardustClient): StateFlow<VoiceUiState> {
    val phaseFlow = combine(
        client.state,
        client.audio.captureState,
        client.audio.playbackState,
        _awaitingResponse,
    ) { sdk, cap, play, awaiting -> mapToPhase(sdk, cap, play, awaiting) }

    val transcriptFlow = client.events
        .filterIsInstance<StardustEvent.ConversationItemCreated>()
        .scan(emptyList<TranscriptItem>()) { acc, event ->
            (acc + event.toTranscriptItem()).takeLast(MAX_TRANSCRIPTS)
        }

    return combine(
        phaseFlow,
        transcriptFlow,
        _audioLevel,
        _errorMessage,
    ) { phase, transcripts, level, error ->
        VoiceUiState(
            phase = phase,
            agentName = currentAgentName,
            statusText = phase.toStatusText(),
            transcripts = transcripts,
            audioLevel = level,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VoiceUiState())
}
```

#### 6.1.4 生命周期时序

```
Activity.onCreate
  └─ setContent { MainScreen(viewModel) }
       └─ VoiceViewModel.init
            ├─ settingsRepo.settings.collect → 缓存最新配置
            └─ 等待用户点击 Orb

用户点击 Orb
  └─ onOrbClicked()
       ├─ createClient(settings) → StardustSdk.create(config)
       ├─ client.connect()
       └─ observeSdkStreams(client)

SDK 连接成功
  └─ client.state → Connected
       └─ client.updateSession(sessionConfig)

对话循环
  └─ SDK 内部 AudioRecord → 服务端 VAD → 事件流 → UI 更新

Activity.onDestroy (配置变更除外)
  └─ ViewModel.onCleared()
       └─ client.close()
```

### 6.2 SettingsViewModel

```kotlin
class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    suspend fun persistAll(
        agentId: String,
        serverUrl: String,
        terminalSecret: String,
        groupId: String,
        robotId: String,
        autoPlayAudio: Boolean,
    )
}
```

### 6.3 SettingsRepository

基于 `Preferences DataStore` 封装，提供类型安全的读写接口。

```kotlin
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            agentId = prefs[KEY_AGENT_ID] ?: "",
            serverUrl = prefs[KEY_SERVER_URL] ?: DEFAULT_REALTIME_URL,
            terminalSecret = prefs[KEY_TERMINAL_SECRET] ?: "",
            groupId = prefs[KEY_GROUP_ID] ?: "",
            robotId = prefs[KEY_ROBOT_ID] ?: "",
            autoPlayAudio = prefs[KEY_AUTO_PLAY] ?: true,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) { ... }
}
```

---

## 7. UI 组件详细设计

### 7.1 配色与主题常量

```kotlin
object AppColors {
    val Background  = Color(0xFFF8F9FA)
    val Primary     = Color(0xFF007AFF)
    val Neutral     = Color(0xFF343A40)
    val Error       = Color(0xFFFF3B30)
    val Surface     = Color(0xFFFFFFFF)
}
```

字体方案使用 `Roboto`（系统默认），定义两档：

| 角色 | 字重 | 字号 |
| :--- | :--- | :--- |
| Agent 名称标题 | Medium | 18sp |
| 状态文本 | Regular | 14sp |

### 7.2 MainScreen

主界面采用 `Scaffold` + `Column` 布局：

```
┌──────────────────────────────┐
│  TopBar: Agent Name (居中)    │
├──────────────────────────────┤
│                              │
│                              │
│        VoiceHub (Orb)        │  weight(1f)，垂直居中
│        StatusText            │
│                              │
│                              │
├──────────────────────────────┤
│  TranscriptPanel (可选展开)   │  最大高度 160dp
├──────────────────────────────┤
│  BottomBar: [设置][重连][日志] │  56dp
└──────────────────────────────┘
```

### 7.3 VoiceHub（语音圆球）

#### 7.3.1 组件签名

```kotlin
@Composable
fun VoiceHub(
    phase: VoicePhase,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

#### 7.3.2 Orb 动画状态机

| VoicePhase | 颜色 | 动画 | 实现方式 |
| :--- | :--- | :--- | :--- |
| `Ready` | `Neutral.copy(alpha=0.3)` | 静止 | 固定圆形 `Box` |
| `Connecting` | `Primary` | 圆环旋转 | `InfiniteTransition` + `rotate`，绘制 `drawArc` |
| `Idle` | `Primary` | 静止 | 实心圆 |
| `Listening` | `Primary` | 呼吸缩放 1.0↔1.05 | `InfiniteTransition` + `animateFloat` → `graphicsLayer { scaleX; scaleY }` |
| `Thinking` | `Primary` | 内部微波纹旋转 | `InfiniteTransition` + `Canvas` 绘制旋转弧线 |
| `Speaking` | `Primary` | 外扩波纹 | 多层 `Canvas` 圆环，半径由 `audioLevel` 驱动 |
| `Error` | `Error` | 静止 | 红色实心圆 |

#### 7.3.3 颜色过渡

使用 `animateColorAsState` 在状态切换时实现平滑颜色过渡：

```kotlin
val orbColor by animateColorAsState(
    targetValue = when (phase) {
        VoicePhase.Ready -> AppColors.Neutral.copy(alpha = 0.3f)
        VoicePhase.Error -> AppColors.Error
        else -> AppColors.Primary
    },
    animationSpec = tween(durationMillis = 300),
)
```

#### 7.3.4 Speaking 波纹扩散算法

```kotlin
val rippleCount = 3
val baseRadius = orbRadius * 1.2f
for (i in 0 until rippleCount) {
    val phaseOffset = i * (1f / rippleCount)
    val progress = ((animProgress + phaseOffset) % 1f)
    val radius = baseRadius + (maxRippleRadius - baseRadius) * progress
    val alpha = (1f - progress) * audioLevel * 0.4f
    drawCircle(
        color = AppColors.Primary.copy(alpha = alpha),
        radius = radius,
        style = Stroke(width = 2.dp.toPx()),
    )
}
```

### 7.4 BottomBar

```kotlin
@Composable
fun BottomBar(
    onSettingsClick: () -> Unit,
    onReconnectClick: () -> Unit,
    onTranscriptToggle: () -> Unit,
    modifier: Modifier = Modifier,
)
```

三个 `IconButton` 水平等分排列，图标使用 Material Icons：

| 位置 | 图标 | 动作 |
| :--- | :--- | :--- |
| 左 | `Icons.Outlined.Settings` | 导航至设置页 |
| 中 | `Icons.Outlined.Refresh` | 触发重连 |
| 右 | `Icons.Outlined.Chat` | 展开/折叠转写面板 |

### 7.5 TranscriptPanel

以 `LazyColumn` 实现，倒序展示最近 `N` 条（默认 20）对话记录。

```kotlin
@Composable
fun TranscriptPanel(
    transcripts: List<TranscriptItem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.heightIn(max = 160.dp),
        reverseLayout = true,
    ) {
        items(transcripts, key = { it.id }) { item ->
            TranscriptRow(item)
        }
    }
}
```

- 用户消息右对齐，Agent 消息左对齐。
- 新消息到达时自动滚动到底部。

### 7.6 SettingsScreen

以 `Column` + `OutlinedTextField` 实现扁平化表单：

| 字段 | 类型 | 校验 |
| :--- | :--- | :--- |
| Agent ID | 文本输入 | 非空 |
| Server URL | 文本输入 | 格式校验（`ws://` 或 `wss://` 前缀） |
| 终端密钥 | 文本输入 | 与 `terminal_secret` 二选一逻辑见下 |
| Group ID | 文本输入 | 未填终端密钥时必填（且需同时填 Robot ID） |
| Robot ID | 文本输入 | 未填终端密钥时必填（且需同时填 Group ID） |
| Auto Play Audio | Switch | — |

**鉴权字段二选一**：已填写终端密钥则不再要求 Group/Robot；未填终端密钥则必须 **同时** 填写 Group ID 与 Robot ID（用于 `Bearer X-Tiwater-Debug` 联调路径）。

保存按钮触发 `SettingsViewModel.persistAll(...)`，写入 DataStore 后自动 pop 返回主页。

### 7.7 SplashScreen

- 显示应用 Logo（纯文本 + 动画）。
- 使用 `LaunchedEffect` 延迟 1200ms 后通过 Navigation 跳转到 `MainScreen`。
- 进入动画：Logo 从 `alpha 0 / scale 0.8` 渐入到 `alpha 1 / scale 1`。

---

## 8. 导航设计

使用 `Navigation Compose` 管理页面跳转：

```kotlin
sealed class AppRoute(val route: String) {
    object Splash : AppRoute("splash")
    object Main : AppRoute("main")
    object Settings : AppRoute("settings")
}
```

```
Splash ──(自动跳转)──> Main ──(设置按钮)──> Settings
                             <──(返回)────
```

---

## 9. SDK 集成详细设计

### 9.1 StardustConfig 构建

```kotlin
fun buildStardustConfig(settings: AppSettings): StardustConfig {
    val secret = settings.terminalSecret.trim()
    val hasTerminalSecret = secret.isNotEmpty()
    return StardustConfig(
        realtimeUrl = settings.serverUrl,
        tokenProvider = {
            if (hasTerminalSecret) "" else STARDUST_DEBUG_BEARER_TOKEN
        },
        terminalSecretProvider = if (hasTerminalSecret) {
            { secret }
        } else {
            null
        },
        queryParams = if (hasTerminalSecret) {
            emptyMap()
        } else {
            mapOf(
                "group_id" to settings.groupId.trim(),
                "robot_id" to settings.robotId.trim(),
            )
        },
        autoPlayAudio = settings.autoPlayAudio,
        reconnectPolicy = ReconnectPolicy(
            enabled = true,
            maxAttempts = 3,
            initialDelayMs = 1000L,
            maxDelayMs = 10_000L,
        ),
        logLevel = StardustLogLevel.DEBUG,
    )
}
```

其中 `STARDUST_DEBUG_BEARER_TOKEN` 为字面量 `"X-Tiwater-Debug"`，须与服务端 `websocket_handler_base.py` 中分支字符串完全一致。

### 9.2 WSS 鉴权策略（与 Stardust 服务端一致）

Sample **不再**通过独立 HTTP「Token Endpoint」拉取 token，而是直接按服务端握手规则二选一：

| 模式 | `tokenProvider` | `terminalSecretProvider` | `queryParams` | SDK 行为摘要 |
| :--- | :--- | :--- | :--- | :--- |
| 终端密钥 | 返回 `""`（空串） | 返回密钥字符串 | 无额外项（SDK 会附加 `terminal_secret`） | 仅 query 鉴权：`stardust-sdk` 在 token 为空时**不**设置 `Authorization` 头 |
| Debug / 联调 | 返回 `X-Tiwater-Debug` | `null` | `group_id`、`robot_id` | `Authorization: Bearer X-Tiwater-Debug` + URL 查询参数 |

每次 `connect()` 时 SDK 会再次调用 `tokenProvider` 与 `terminalSecretProvider`（若存在），与实时连接、视频 WebSocket（若启用）共用同一套配置。

### 9.3 Session 配置

连接成功后立即发送 `session.update`：

```kotlin
client.updateSession(
    SessionConfig(
        agentId = settings.agentId.takeIf { it.isNotBlank() },
        model = ModelConfig(name = "gpt-4o-realtime"),
    )
)
```

### 9.4 事件消费

ViewModel 中通过 `SharedFlow` 收集关键事件：

| 事件类型 | 用途 |
| :--- | :--- |
| `ResponseCreated` | 设置 `awaitingResponse = true` |
| `ResponseDone` | 设置 `awaitingResponse = false` |
| `ResponseAudioDelta` | 重置 `awaitingResponse = false`（进入 Speaking） |
| `ConversationItemCreated` | 提取文本追加到转写列表 |
| `InputAudioBufferSpeechStarted` | 打断事件，UI 可追加打断标记 |

### 9.5 音频流水线

```
┌────────┐  PCM 16kHz  ┌──────────┐  WebSocket  ┌──────────┐
│  Mic   │ ──────────> │ SDK      │ ──────────> │  Server  │
│        │             │ Audio    │ <────────── │          │
└────────┘             │ Module   │  PCM 24kHz  └──────────┘
                       │          │ ──────────>
                       └──────────┘             ┌──────────┐
                         enqueue                │ Speaker  │
                                                └──────────┘
```

SDK 内部处理 `AudioRecord`（录音）和 `AudioTrack`（播放），Sample App 不直接操作音频硬件，通过 `captureState` 与 `playbackState` 感知状态变化。

---

## 10. 权限管理设计

### 10.1 所需权限

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

### 10.2 请求时机

| 时机 | 行为 |
| :--- | :--- |
| 应用启动 | 静默检查 `RECORD_AUDIO` 是否已授权 |
| 用户点击 Orb 且未授权 | 弹出系统权限请求对话框 |
| 用户拒绝 | 展示 Snackbar 提示引导至系统设置 |
| 用户永久拒绝 | Snackbar 含 "设置" 按钮，跳转应用权限页 |

### 10.3 实现方案

使用 `rememberLauncherForActivityResult(RequestPermission())` 在 Compose 中声明式管理权限流程：

```kotlin
@Composable
fun PermissionGate(
    onGranted: () -> Unit,
    content: @Composable (requestPermission: () -> Unit) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted()
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        LocalContext.current, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
        onGranted()
    } else {
        content { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    }
}
```

---

## 11. 错误处理设计

### 11.1 错误分类

| 错误类型 | 来源 | UI 表现 |
| :--- | :--- | :--- |
| 网络连接失败 | `client.errors` / `StardustState.Failed` | Orb 变红 + 状态文本提示（"出错了，点击重试"） |
| 音频播放失败 | `PlaybackAudioState.Failed` | Orb 变红 + 状态文本提示（"出错了，点击重试"） |
| 连接鉴权失败（如 HTTP 401） | `client.errors` / 连接异常 | Snackbar 提示 |
| 权限拒绝 | 系统回调 | Snackbar + 引导操作 |
| SDK 内部错误 | `client.errors` SharedFlow | Snackbar 展示 `errorCode` + 描述 |
| WebSocket 异常断开 | SDK 自动重连 / `Failed` | 若超出重试次数则进入 Error 状态 |

### 11.2 错误收集流程

```kotlin
viewModelScope.launch {
    client.errors.collect { error ->
        _errorMessage.value = "[${error.code}] ${error.message}"
        delay(ERROR_DISPLAY_DURATION)
        _errorMessage.value = null
    }
}
```

### 11.3 重连策略

SDK 层自动重连由 `ReconnectPolicy` 控制（指数退避）。UI 层在自动重连耗尽后提供手动重连按钮：

- BottomBar 中间按钮：`onReconnectClicked()` → `client.close()` → `client = createClient()` → `client.connect()`。
- Error 状态下点击 Orb 同样触发重连。

---

## 12. 线程与协程设计

| 上下文 | 用途 |
| :--- | :--- |
| `viewModelScope` (Main) | 状态聚合、UI 事件处理 |
| `Dispatchers.IO` | DataStore 读写、SDK 内 WebSocket I/O |
| SDK 内部线程 | WebSocket I/O、音频录制/播放 |

ViewModel 中所有 SDK 调用均在 `viewModelScope` 中发起，SDK 内部自行切换线程。

---

## 13. 性能与优化设计

### 13.1 Compose 重组优化

| 策略 | 应用位置 |
| :--- | :--- |
| `@Stable` / `@Immutable` 注解 | `VoiceUiState`、`TranscriptItem` |
| `key` 参数 | `LazyColumn` 的 `items(key = { it.id })` |
| `derivedStateOf` | 仅在 phase 变化时触发动画切换 |
| lambda 稳定性 | ViewModel 方法引用避免每次重组创建新 lambda |

### 13.2 动画性能

- Orb 动画使用 `graphicsLayer` 修改 `scaleX/scaleY/alpha`，不触发 layout 重排。
- Speaking 波纹使用 `Canvas` 直接绘制，避免创建额外 Composable 节点。
- 动画帧率目标 60fps，通过 `withFrameMillis` 控制波纹进度。

### 13.3 内存管理

- 转写列表使用 `takeLast(MAX_TRANSCRIPTS)` 限制条目数量（默认 20 条）。
- SDK 客户端在 `onCleared` 中确保释放。
- 音频缓冲区管理完全由 SDK 内部负责。

---

## 14. 可测试性设计

### 14.1 测试分层

| 层次 | 测试类型 | 覆盖范围 |
| :--- | :--- | :--- |
| ViewModel | JUnit + Turbine | 状态映射逻辑、事件处理、错误传播 |
| Repository | JUnit | DataStore 读写正确性 |
| UI | Compose UI Test | 组件渲染、点击响应、动画触发 |
| 集成 | Instrumented Test | SDK 连接完整流程 |

### 14.2 Mock 策略

定义 `StardustClient` 接口的 Fake 实现用于 ViewModel 单元测试：

```kotlin
class FakeStardustClient : StardustClient {
    private val _state = MutableStateFlow(StardustState.Idle)
    override val state: StateFlow<StardustState> = _state

    private val _events = MutableSharedFlow<StardustEvent>()
    override val events: SharedFlow<StardustEvent> = _events

    // 可编程地模拟状态变迁
    fun emitState(state: StardustState) { _state.value = state }
    suspend fun emitEvent(event: StardustEvent) { _events.emit(event) }
    // ...
}
```

### 14.3 关键测试用例

| 测试场景 | 验证目标 |
| :--- | :--- |
| SDK Idle → Connecting → Connected | `VoicePhase` 依次变为 Ready → Connecting → Idle |
| `CaptureAudioState.Recording`，且 `awaitingResponse == false` | `VoicePhase == Listening` |
| `CaptureAudioState.Recording`，且 `awaitingResponse == true` | `VoicePhase == Thinking`（等待响应优先于麦克风状态） |
| 收到 `ResponseAudioDelta` 后开始播放 | `Thinking → Speaking` 转换 |
| `StardustState.Failed` 事件 | `VoicePhase == Error`，errorMessage 非空 |
| `PlaybackAudioState.Failed`（SDK 已连接时） | `VoicePhase == Error` |
| 重连中（`Reconnecting`）且音频失败 | `VoicePhase == Connecting`（重连优先，不误显示 Error） |
| 点击 Orb（Ready 状态） | 触发 `connect()` |
| 点击 Orb（Error 状态） | 触发重连流程 |
| DataStore 保存/读取 | 配置数据一致性 |

---

## 15. 构建与依赖配置

### 15.1 核心依赖

| 依赖 | 版本 | 用途 |
| :--- | :--- | :--- |
| `stardust-sdk` | 本地模块引用 | 核心 SDK |
| `androidx.compose.ui` | BOM latest | 声明式 UI |
| `androidx.compose.material3` | BOM latest | Material 3 组件 |
| `androidx.compose.animation` | BOM latest | 动画框架 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8+ | ViewModel 集成 |
| `androidx.navigation:navigation-compose` | 2.7+ | 页面导航 |
| `androidx.datastore:datastore-preferences` | 1.1+ | 配置持久化 |
| `kotlinx-coroutines-android` | 1.8+ | 协程支持 |

### 15.2 编译配置

| 配置项 | 值 |
| :--- | :--- |
| `compileSdk` | 34 |
| `minSdk` | 26 |
| `targetSdk` | 34 |
| Kotlin JVM Target | 17 |
| Compose Compiler | 与 Kotlin 版本匹配 |

---

## 16. 附录

### 16.1 完整数据流时序（典型对话）

```
┌──────┐     ┌──────────┐     ┌─────────┐     ┌────────┐
│ User │     │ Compose  │     │ViewModel│     │  SDK   │
└──┬───┘     └────┬─────┘     └────┬────┘     └───┬────┘
   │  点击 Orb    │                │               │
   │─────────────>│  onClick()     │               │
   │              │───────────────>│ onOrbClicked() │
   │              │                │──────────────>│ connect()
   │              │                │               │
   │              │                │  state=       │
   │              │  Connecting    │  Connecting   │
   │              │<───────────────│<──────────────│
   │              │                │               │
   │              │                │  state=       │
   │              │  Idle          │  Connected    │
   │              │<───────────────│<──────────────│
   │              │                │──────────────>│ updateSession()
   │              │                │               │
   │  说话        │                │  audioState=  │
   │─ ─ ─ ─ ─ ─ ─│─ ─ ─ ─ ─ ─ ─ ─│─ ─Recording ─│ (SDK 内部 AudioRecord)
   │              │  Listening     │               │
   │              │<───────────────│<──────────────│
   │              │                │               │
   │  停止说话    │                │  audioState=  │
   │─ ─ ─ ─ ─ ─ ─│─ ─ ─ ─ ─ ─ ─ ─│─ ─Idle ─ ─ ─ │
   │              │  Thinking      │  (await resp) │
   │              │<───────────────│               │
   │              │                │               │
   │              │                │  audioState=  │
   │              │  Speaking      │  Playing      │
   │              │<───────────────│<──────────────│
   │              │                │               │
   │  (打断说话)  │                │  SpeechStart  │
   │─ ─ ─ ─ ─ ─ ─│─ ─ ─ ─ ─ ─ ─ ─│<──────────── │ (SDK 自动停播)
   │              │  Listening     │               │
   │              │<───────────────│               │
```

### 16.2 设计约束与未来扩展

| 约束/扩展点 | 说明 |
| :--- | :--- |
| 最低 API 级别 | Android 8.0 (API 26)，覆盖 95%+ 设备 |
| 音频格式 | 录音 PCM 16kHz，播放 PCM 24kHz，由 SDK 内部管理 |
| 视频能力 | SDK 已提供 `StardustVideo` 接口，Sample App 当前不集成，后续可扩展 |
| 多轮对话历史 | 当前仅展示最近 N 条，后续可增加本地持久化 |
| 国际化 | 字符串资源集中在 `strings.xml`，便于后续多语言支持 |
| 深色主题 | 当前仅设计浅色主题，可通过 `isSystemInDarkTheme()` 扩展 |
