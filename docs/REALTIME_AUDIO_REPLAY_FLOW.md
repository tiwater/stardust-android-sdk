# Stardust Realtime 音频重播：协议流程说明（Server VAD / Client VAD）

本文用**逻辑语言**描述：在 WebSocket Realtime 会话中，客户端为「对话信息页」重播**用户语音**与**助手语音**时，应如何理解协议、收到哪些消息、用哪些字段做什么事。面向实现与排查，与 [stardust/docs/realtime_api_reference.md](../../stardust/docs/realtime_api_reference.md)、[stardust/docs/session_config.md](../../stardust/docs/session_config.md) 一致；Sample 中的对应实现见 `VoiceViewModel.kt`。

---

## 1. 共同前提

### 1.1 音频格式

- 上行 `input_audio_buffer.append` 的 `audio`：Base64 编码的 **PCM16，24 kHz，单声道**。
- 下行 `response.audio.delta` 的 `delta`：同上；解码后为连续 PCM 字节流。
- 客户端若从麦克风采集做重播缓存，帧长需与服务端一致（SDK 中为约 **1920 字节/帧 ≈ 40 ms**）。

### 1.2 对话项主键 `item_id`

- **用户**侧：与「本条用户消息」绑定的稳定 ID 出现在 `conversation.item.created` 的 `item.id`，以及 `conversation.item.input_audio_transcription.completed` 的 **`item_id`**（与对话项一致）。重播缓存应以 **`conversation 项的 item id`** 为 key，而不是 `input_audio_buffer.speech_started` 里可能不同的 `item_id`（后者在协议文档中为「语音段标识」）。
- **助手**侧：`response.audio.delta` / `response.audio.done` 等事件顶层的 **`item_id`** 对应该轮助手输出 message 项的 id。

### 1.3 助手音频（两种 VAD 相同）

1. 收到 **`response.audio.delta`**  
   - 使用 **`item_id`**：作为本条助手音频在本地累积器的 key。  
   - 使用 **`delta`**（Base64）：解码为 PCM 字节，**追加**到该 `item_id` 的缓冲区。  
   - 若 `delta` 为空或解码失败：跳过追加；中断场景下可能为 null。

2. 收到 **`response.audio.done`**  
   - 使用 **`item_id`**：将该 key 下已累积的 PCM **视为完整**，写入「可重播」存储（例如内存 Map），并标记该 `item_id` 可播放。  
   - 若此前仅有 delta 无 `conversation.item.created`：可用 `item_id` + `response_id` 先建占位 UI 记录，待文本事件补全文案。

3. 助手**文案**（用于列表展示，与重播 PCM 独立）可来自（按优先级在业务上兼容）：  
   - `response.audio_transcript.delta` 拼接 / `response.audio_transcript.done`（若带 `transcript`）；  
   - `response.content_part.done` 的 `part.transcript` 或 `part.text`；  
   - `response.output_item.done` 的 `item.content[]` 中 `transcript`/`text`；  
   - `response.done` 内嵌的 `output` 结构（若存在）。

### 1.4 事件顺序保证：Sample App 的实现原则

`realtime_api_reference.md` 只保证**同一业务系列、同一业务链路**内的事件按服务端生成顺序发送；不同业务系列之间没有全局顺序保证。Sample App 不能把日志中的时序图当作严格到达顺序，而应把每个下行事件当作一次「状态补丁」处理。

- `conversation.*`、`input_audio_buffer.*`、`response.*` 属于不同业务系列，可能交错到达；例如 `conversation.item.created`、`input_audio_buffer.committed`、`speech_started`、`speech_stopped`、`input_audio_transcription.completed` 的相对顺序不能写死。
- `input_audio_buffer.*` 内部也只应依赖明确业务因果关系；没有直接因果关系的事件（如 `speech_stopped` 与 `committed`）可能先后不固定。
- `response.*` 内同一个 `response_id + item_id` 的音频 delta / done 可按同一响应流处理，但它们仍可能与用户侧 `conversation.*`、`input_audio_buffer.*` 事件交错；Sample App 应按 **助手 `item_id`** 累积 PCM，而不是等待某个固定前置事件先到。
- UI 行、转写文本、用户 PCM、助手 PCM 应分别维护可合并的本地状态：事件先到就先落到对应 Map / pending 结构，后续事件用同一个 `item_id` 补齐；重复到达则幂等覆盖。
- 因为当前 Stardust 的 `output_index` / `content_index` 多数固定为 `0`，Sample App 不应把它们作为主关联 key；它们最多用于日志诊断。

---

## 2. Server VAD（服务端判停）

### 2.1 会话意图

- 在 **OpenAI 形状**的 session 中：配置 **`turn_detection.type` = `"server_vad"`**（及 threshold、prefix_padding_ms、silence_duration_ms 等）。  
- 在 **Tiwater session**（`session_config.md`）中：配置 **`hearing.turn_detection`** 为带 **`type: "server_vad"`** 的对象；**不要**设为 `null`（`null` 表示关闭服务端 VAD，改由客户端判停）。

### 2.2 客户端上行（持续推流）

- 连接并 `session.update` 生效后，客户端应 **持续** 发送 **`input_audio_buffer.append`**（麦克风 PCM 分帧 Base64），无需为每一轮说话手动 `commit`。
- 本地须同步将每一帧写入 **环形缓冲**（ring buffer），为后续 PCM 快照保留足够历史。

### 2.3 服务端下行（一轮用户说话的实际顺序）

> **重要：Tiwater server VAD 的事件下行模式**  
> Tiwater 服务端在完成整段语音 VAD 检测与转写后，会**集中 burst 发送**所有关联事件，而非随说话进度逐步推送。实测日志显示，同一轮用户发言的 F → E → A → C → G 五个事件几乎在同一毫秒内全部到达（远早于"经典 OpenAI 逐帧推送"顺序 A → B → C → D → E → F → G）。客户端实现须能处理这种乱序 + burst 场景。

以下描述「收到消息后做什么」；字母顺序表示语义依赖关系，**不代表实际到达顺序**。

| 步骤 | 事件 | 关键字段 | 客户端逻辑（重播相关） |
|------|------|----------|------------------------|
| A | `input_audio_buffer.speech_started` | `item_id`（语音段）、`audio_start_ms` | 记录 `audio_start_ms`，用于步骤 C 截取 PCM。**不要**在此处清空 ring（见下方易错点 1）。该 `item_id` 必须与后续 `speech_stopped` / `input_audio_buffer.committed` / `conversation.item.created` / `input_audio_transcription.completed` 中同一轮语音的 `item_id` 一致；若不一致，属于 Stardust 服务端 bug。 |
| B | （持续）`input_audio_buffer.append` 由客户端上行 | `audio` | 麦克风照常发送；本地持续把每帧写入 **环形缓冲**，保留足够时长的历史供步骤 C 按时间戳截取。 |
| C | `input_audio_buffer.speech_stopped` | `item_id`、`audio_end_ms` | 表示服务端判定本段语音结束。根据 A 中记录的 `audio_start_ms` 与本事件的 `audio_end_ms`，从 **环形缓冲中截取对应时间段的帧**，作为 **`pending_user_pcm`**（「这一段待绑定到某个 conversation item」）。截取后清空或标记 ring 头部，避免与下一段混淆。 |
| D | （自动）服务端提交缓冲区并处理 | 等价于发生一次 commit 的语义 | 客户端可能收到 **`input_audio_buffer.committed`**。 |
| E | `input_audio_buffer.committed` | **`item_id`**（Tiwater 实测必带） | 使用 **`item_id`**：将 **`pending_user_pcm`**（若无则回退为当前 ring 合并结果）写入以 **`item_id`** 为 key 的重播缓存；清空 `pending`。若本事件 **无 `item_id`**，则**不能**单靠此事件完成绑定（见 F）。 |
| F | `conversation.item.created` | `item.id`、`item.role`、`item.content` | 使用 **`item.id`**：刷新 UI 对话行。若这是文字输入，`item.content` 通常包含 **`input_text`**，可直接展示；若这是语音输入，不要从 F 推断最终文本，先创建占位/临时行，等待 G 的转写结果。 |
| G | `conversation.item.input_audio_transcription.completed` | **`item_id`**、`transcript`、`content_index` | 使用 **`item_id`**：与对话行对齐。语音输入对应的文字以本事件的 **`transcript`** 为准；将 F 中条目升级为「用户语音」并展示 `transcript`。将 **`pending_user_pcm`**（若 C 已快照且未被消费）**以 `item_id` 为 key** 写入重播缓存（与 E 二选一或互补；同一轮语音中 A/C/E/F/G 的 `item_id` 必须一致）。 |

Sample App 实现时，不应把 A → C → E → F → G 写成线性回调链。推荐做法是：`speech_started` 只记录时间戳，`speech_stopped` 只负责从 ring 快照到 `pending_user_pcm`，`committed` / `conversation.item.created` / `input_audio_transcription.completed` 都按 `item_id` 尝试合并 UI 行与重播缓存；缺少前置状态时先缓存，后续事件到达再补齐。

- **Sample（`VoiceViewModel.kt`）**：用户侧用 **`item_id` → `PendingUserSpeechSegment`** 分别写入 `audio_start_ms` / `audio_end_ms`；`speech_started` 与 `speech_stopped` 只更新**当前事件自带 `item_id`** 对应的分段，**不要**用单一全局变量在 started 里记 start、再在 stopped 里读（burst/乱序下会串句）。finalize 成功后从 pending map 移除，重播仅依赖 `_audioPcm[item_id]`；ring 按「未完成分段的最早已知 `start_ms`」或「已全部 finalize 时的最大 `end`」安全裁剪，避免提前裁掉未 finalize 分段仍需要的帧。若 `speech_stopped` 早于尾帧到达，除每帧 `tryFinalize` 外可挂**单次** catch-up 协程：按约一帧间隔轮询直至时间轴覆盖 `audio_end_ms`，总等待上限约 600ms 后再 `force` 一次兜底，避免「先盲 sleep 600ms 再试」的粗粒度延迟。

### 2.4 服务端下行：构建助手语音

用户语音被服务端判停、提交并完成转写后，Server VAD 会自动拉起模型回复；客户端**不需要**额外发送 `response.create`。服务端构建助手语音时，通常按以下语义下发事件（实际 `response.audio_transcript.delta` 与 `response.audio.delta` 会交错多次）：

| 步骤 | 事件 | 服务端构建内容 | 客户端逻辑（助手语音重播相关） |
|------|------|----------------|--------------------------------|
| H | `response.created` | 创建本轮响应，给出 `response.id`。 | 记录响应开始；此时还没有可重播 PCM。 |
| I | `response.output_item.added` / `conversation.item.created`（助手项） | 为助手输出创建 message item；后续音频事件顶层 **`item_id`** 指向同一个助手 item。 | 用 `item_id` / `item.id` 建立或更新助手 UI 占位行；不要把用户 `item_id` 与助手 `item_id` 混用。 |
| J | `response.content_part.added` | 创建音频内容分片；当前 Stardust 下发的 `content_index` 多数固定为 0。 | 为该助手 item 准备文本与音频累积状态；不要依赖 `content_index` 作为重播绑定 key。 |
| K | `response.audio_transcript.delta` | 服务端把 TTS 文本片段写入 `delta`，并带上 `response_id`、助手 **`item_id`**、`output_index`、`content_index`。 | 按助手 `item_id` 拼接展示文案；该文案仅用于 UI，不是 PCM。 |
| L | `response.audio.delta` | 服务端把 TTS 产生的 PCM16 24 kHz 单声道字节 Base64 到 **`delta`**；若中断或空 utterance，`delta` 可能为 `null`。 | 以助手 **`item_id`** 为 key，Base64 解码后追加到助手 PCM 累积器；实时播放也消费同一批 delta。 |
| M | `response.audio_transcript.done` | 标记助手语音文案流结束。 | 可停止等待更多 transcript delta；若事件不带最终 `transcript`，使用已拼接文本。 |
| N | `response.audio.done` | 标记该助手 item 的音频流结束。 | 将该 **`item_id`** 下累积的 PCM 写入可重播缓存，并标记该助手消息可播放。 |
| O | `response.content_part.done` / `response.output_item.done` / `response.done` | 收束内容分片、输出 item 与整轮响应；`response.done` 可能携带完整 `output`。 | 补全文案或状态；不要用它覆盖已按 `response.audio.done` 或截断逻辑落盘的 PCM。 |

关键字段来自服务端事件构建：`response_id` 对应本轮响应，`response_item_id` 会被下发为顶层 **`item_id`**，`utterance.data` 会被编码为 `response.audio.delta.delta`。因此，助手语音重播的主键始终是 **`response.audio.*.item_id`**，不是用户语音的 `input_audio_buffer.*.item_id`。

**注意：`output_index` / `content_index` 当前不能作为可靠区分依据。** Stardust 服务端实测与代码实现中，多数 `response.*` 事件会把 `output_index`、`content_index` 固定下发为 `0`。在当前单助手输出、单音频 content part 场景下可以工作，但这属于服务端索引字段未完整实现；客户端应优先用 **`response_id + item_id`** 关联助手响应流，并用 **助手 `item_id`** 作为重播缓存 key。若未来支持同一响应内多 output 或多 content part，服务端需要修正这两个索引的真实下发。

Sample App 实现时，`response.audio.delta` 可以早于或晚于部分 UI / transcript 事件到达。收到 delta 就应按助手 `item_id` 追加 PCM；若 UI 行尚未创建，可先建立助手占位记录。`response.audio.done` 只表示该助手音频缓存可以落盘，不应假设所有 `conversation.*` 或 `response.done` 已经先到。

### 2.5 Server VAD 下易错点（用户语音侧）

- **易错点 1（会导致用户 PCM 全部丢失）：在 `speech_started` 时清空 ring**  
  Tiwater server VAD 中，`speech_started` 与 `speech_stopped` 几乎同时到达（burst 模式，相差 < 2 ms）。若按旧思路在 `speech_started` 时清空 ring，则 `speech_stopped` 时快照到的 ring 为空，PCM 全部丢失。  
  **正确做法**：ring 持续写入，收到 `speech_started` 只记录 `audio_start_ms`；收到 `speech_stopped` 时按 `[audio_start_ms, audio_end_ms]` 从 ring 中截取对应帧。

- **同一轮用户语音的 `item_id` 不一致**：`speech_started` / `speech_stopped` / `input_audio_buffer.committed` / `conversation.item.created` / `input_audio_transcription.completed` 中的 **用户 `item_id` 必须一致**。这些事件只用于绑定本轮用户输入的 PCM 与转写；它们不应与后续助手回复的 `response.audio.*.item_id` 混用。若同一轮用户语音内部出现不一致，应视为 Stardust 服务端 bug，而不是客户端正常兼容分支。  

- **G 与 E 的先后顺序（用户语音绑定）**：若 committed 先到但客户端尚未创建/确认用户 UI 对话行，可保留 `pending_user_pcm`，待 F/G 到达后用同一个用户 `item_id` 完成 UI 与重播缓存绑定；若 E 缺少 `item_id` 或与 F/G 不一致，应按服务端异常处理。助手语音的缓存绑定另见 §2.4，使用 `response.audio.delta` / `response.audio.done` 顶层的助手 `item_id`。

---

## 3. Client VAD / Push-to-Talk（客户端判停）

### 3.1 会话意图

- **OpenAI 文档**：`turn_detection.type` = **`"disabled"`** 或不依赖服务端判停。  
- **Tiwater**：**`hearing.turn_detection` = `null`**，表示关闭服务端 VAD，由客户端决定何时 **`input_audio_buffer.commit`**。  
- Sample 在 PTT 模式下对 `hearing.turn_detection` 发送 **`JsonNull`**，语义与上一致。

### 3.2 客户端上行（按需采集）

1. **按下（开始说话）**  
   - 开始采集并发送 **`input_audio_buffer.append`**。  
   - 本地：清空 ring，自本段起写入帧，供松手时快照。

2. **松开（结束说话）**  
   - 发送 **`input_audio_buffer.commit`**（SDK 可为 `stopCapture(commit = true)`）。  
   - 本地：将当前 ring **快照为 `pending_user_pcm`** 并清空 ring。  
   - 若按压过短：可 `stopCapture(commit = false)` + **`input_audio_buffer.clear`**，不提交本轮。

3. **必须发送 `response.create`**  
   - Client VAD / PTT 关闭服务端判停后，**`input_audio_buffer.commit` 只提交音频并触发转写，不会自动生成模型回复**。客户端必须在 commit 后发送 **`response.create`**，由该事件显式拉起助手响应。Sample PTT 也应遵循此链路，并在按下/松开时用 `cancelResponse` 打断上一轮。

### 3.3 服务端下行（与 Server VAD 的差异）

- **通常不会**收到 `input_audio_buffer.speech_started` / `speech_stopped`（无服务端 VAD）。  
- **会**收到 **`input_audio_buffer.committed`**（在客户端 commit 之后）。**Tiwater 实测**：`committed` 事件可能被连续发送两次（`item_id` 相同），客户端需做**幂等处理**，以相同 key 写入时覆盖即可。  
- 发送 **`response.create`** 后，同样会有 **`conversation.item.created`**（content 为 `input_audio`）、**`conversation.item.input_audio_transcription.completed`**、`response.created` 等，字段用法与 §2.3 的 F、G 相同。  
- **用户 PCM 绑定**：以 **`input_audio_buffer.committed` 中的 `item_id`（若存在）** 或 **G 的 `item_id`** 写入重播缓存；推荐仍以 **G 的 `item_id`** 为最终权威，与 UI 行一致。

### 3.4 服务端下行：构建助手语音

Client VAD / PTT 与 Server VAD 的助手语音事件形态相同，差异在于：客户端必须在 `input_audio_buffer.commit` 之后显式发送 **`response.create`**，服务端才会开始构建并下发助手语音。

| 步骤 | 事件 | 服务端构建内容 | 客户端逻辑（助手语音重播相关） |
|------|------|----------------|--------------------------------|
| H | `response.created` | 服务端接受 `response.create` 后创建响应，生成 `response.id`。 | 建立本轮助手响应状态；尚无可播放重播缓存。 |
| I | `response.output_item.added` / `conversation.item.created`（助手项） | 创建助手 message item；后续音频、文案事件的顶层 **`item_id`** 都应指向该助手 item。 | 创建助手占位行，并用助手 `item_id` 初始化 PCM 与 transcript 累积器。 |
| J | `response.content_part.added` | 创建音频内容分片；当前 Stardust 下发的 `output_index`、`content_index` 多数固定为 0。 | 可保留索引用于日志诊断；重播绑定不要依赖它们，仍以助手 `item_id` 为准。 |
| K | `response.audio_transcript.delta` | 将 TTS 对应的文本片段放入 **`delta`** 下发。 | 按助手 `item_id` 拼接助手文案，供列表展示和截断后文案保留。 |
| L | `response.audio.delta` | 将 TTS 音频 PCM16 24 kHz 单声道字节 Base64 到 **`delta`**；中断时可能下发 `delta=null` 或停止继续下发。 | `delta` 非空时解码并追加到该助手 `item_id` 的 PCM 累积器；`delta=null` 时跳过，不能清空既有缓存。 |
| M | `response.audio_transcript.done` | 标记助手文案流结束。 | 将已拼接 transcript 视为最终展示文案。 |
| N | `response.audio.done` | 标记助手音频流结束。 | 将该助手 **`item_id`** 的累计 PCM 写入可重播缓存；此时才标记「助手语音可重播」。 |
| O | `response.content_part.done` / `response.output_item.done` / `response.done` | 收束内容分片、输出 item 与整轮响应。 | 只补状态或文案；不要重新生成 PCM，也不要覆盖被截断后的本地缓存。 |

因此，PTT 模式下用户语音的重播 key 来自 `input_audio_buffer.committed` / `input_audio_transcription.completed.item_id`，而助手语音的重播 key 来自 **`response.audio.delta.item_id` / `response.audio.done.item_id`**。两者属于不同 conversation item，不能合并到同一个缓存条目。

同 §2.4，当前 Stardust 服务端下发的 `output_index` / `content_index` 实测基本固定为 `0`，客户端不要依赖它们区分多路助手内容；助手语音重播绑定以 **`response_id + item_id`** 关联流，以 **助手 `item_id`** 落缓存。若出现多 output / 多 content part 需求，应先修正服务端索引字段。

### 3.5 打断：`response.cancel` + `conversation.item.truncate`

**发起打断时（客户端上行）**

- 客户端向服务端发送 **`response.cancel`**，并同时发送 **`conversation.item.truncate`**（含 `item_id`、`content_index`、`audio_end_ms`），告知服务端截断当前助手输出至该时间点。  
- `realtime_api_reference.md` 注明：在某种 **Client VAD（`robot_info.vad.use === true`）** 场景下 **`response.cancel` 可能被忽略**；即使如此，本地仍需立即停止播放并按下述逻辑裁剪缓存。

**截断后的本地处理（不依赖服务端回送）**

- **Tiwater 实测**：服务端**不会**回送 `conversation.item.truncated` 事件。客户端**必须在发出 `conversation.item.truncate` 的同时立即在本地执行截断**，不能等待服务端确认。  
- 客户端应使用发出的 `conversation.item.truncate` 中的 **`item_id`** 和 **`audio_end_ms`** 定位该助手条目的重播缓存，并原地裁剪：丢弃超出 `audio_end_ms` 之后的字节（`audio_end_ms × 采样率 × 通道数 × 字节深度` 对齐）。  
- **打断后仍可能收到迟到的 `response.audio.delta`**（日志实测：`response.cancel` 与 `conversation.item.truncate` 发出后约 100～200 ms 内仍有 delta 到达）。这些 delta **不应再追加到重播缓存**；可通过「发出 truncate 后对该 `item_id` 置标志位 `truncated = true`，之后的 delta 直接丢弃」来实现。  
- 重播文案（transcript）同理：以截断前已拼接好的 `audio_transcript.delta` 为准，或直接置空，不再等待 `response.audio_transcript.done`（它可能不再到达或内容不一致）。

### 3.6 PTT 与 Server VAD 的采集差异（Sample）

- **Server VAD**：连接成功后即 **`startCapture()`**，全程 append。  
- **PTT**：仅在按下时 **`startCapture()`**，松开时 **`stopCapture(commit=true/false)`** 与可选 **`clear()`**。

---

## 4. 用户侧：对话展示与「是否有音频」

### 4.1 `conversation.item.created` 的 content 形态

- 若含 **`input_audio`**：可视为语音消息（Client VAD / PTT 模式常见此形态）。  
- 若仅有 **`input_text`**（服务端「音频分离投递」）：先直接显示文本；待 **`input_audio_transcription.completed`** 再标记为语音行并 **`item_id` 对齐**。  
  **Tiwater server VAD 实测**：此模式是 **常态**，`input_text` 字段通常已包含完整转写文本（而非空占位），直接展示即可，`transcription.completed` 到达后按 `item_id` 覆写确认即可。  
- **`item.id`**：列表行与重播缓存的主键应与后续 **`input_audio_transcription.completed.item_id`** 一致。

### 4.2 转写失败

- **`conversation.item.input_audio_transcription.failed`**：含 **`item_id`**、`error`。可展示错误；一般无可靠 PCM，不应提供重播或应移除 pending。

---

## 5. 助手侧：流式播放与重播缓存分离

- **实时播放**：由 SDK 在收到 **`response.audio.delta`** 时解码并入队播放（与重播累积并行）。  
- **重播（正常完成）**：在 **`response.audio.done`** 时把该 **`item_id`** 的累积 PCM 封包写入缓存，并标记可播放。**不要**在仅收到第一个 delta 时就标记「可重播」，否则内容不完整。  
- **索引字段**：当前 Stardust 的 `output_index` / `content_index` 多数固定为 `0`，只能视为协议占位字段；重播实现不要把它们作为主要 key。  
- **重播（被打断截断）**：若在 `response.audio.done` 到来前收到 **`conversation.item.truncate`**（含 `item_id`、`audio_end_ms`）：  
  1. 对该 `item_id` 的 PCM 缓冲按 `audio_end_ms` 裁剪，**丢弃超出部分**。  
  2. 将裁剪后的 PCM **立即写入可重播缓存**（不必等 `response.audio.done`，截断即视为该条助手音频完结）。  
  3. 对该 `item_id` **置 `truncated` 标志**，后续迟到的 `response.audio.delta` 一律跳过追加。  
  4. 重播文案以截断前已累积的 `audio_transcript.delta` 拼接结果为准（可附「…」标注被打断）。  
- **`response.done`**：标志整轮响应结束；内含 `output` 时可用于补全助手文案或校验 `item` 列表。被截断的轮次也可能收到 `response.done`，此时不应覆盖已裁剪的缓存。

---

## 6. 事件级检查清单（实现自测）

**用户重播**

- [ ] 事件处理按 `item_id` 合并状态，不依赖 `conversation.*`、`input_audio_buffer.*`、`response.*` 的全局到达顺序。  
- [ ] Server VAD：`speech_started` 只用于记录 `audio_start_ms`，**不清空 ring**；`speech_stopped` 时按 `[audio_start_ms, audio_end_ms]` 从 ring 截取 PCM；**绑定 key 用 `conversation.item` / `transcription.completed` 的 `item_id`**。  
- [ ] Client VAD：松手 `commit` 后 **`pending_user_pcm`** 与 **`input_audio_buffer.committed` / transcription.completed** 的 **`item_id`** 对齐；`committed` 可能重复到达，按 key 幂等写入。  
- [ ] `input_audio_buffer.committed` **无 `item_id`** 时，仍能仅靠 G 完成缓存。  
- [ ] 同一条用户消息：E 与 G **重复到达**时，后到达者应覆盖或幂等写入同一 key。  
- [ ] 当 `conversation.item.created` 携带 **`input_audio`** 内容、立即生成 `UserVoice` 行时（PTT 常见 / server VAD 偶发），也以该 **`item.id`** 把 `pending_user_pcm` 写入重播缓存；**不消费 pending**，留给 G 二次以 `transcription.completed.item_id` 覆盖。这样即使 G 不到达或 `item_id` 与 F 不一致，UI 行的 `itemId` 仍能命中缓存（保证「蓝色播放按钮」可见）。

**重播播放（音轨写入）**

- [ ] 重播 PCM 必须以 **`AudioTrack.MODE_STREAM`** + **分块写入** 播放，**不要用 MODE_STATIC**。MODE_STATIC 在 PCM ≥ 1 MB（多句助手语音）时，内核可能分配小于请求的 buffer，使 `write` 截断且不报错，造成「只能播前 1~2 句」。
- [ ] `track.write` 返回值需用于推进 offset；返回 ≤0 时退出循环。
- [ ] 全部数据 push 完后，再 `delay` 一段「buffer 排空时长」(≈ `streamBufSize × 1000 / (24000 × 2) ms` + 余量)，避免提前 `stop()` 截断尾音。

**助手重播**

- [ ] 每个 `item_id` 在 **`response.audio.done`** 前持续累积 delta；done 后写入可播放缓存。  
- [ ] `response.audio.delta` 到达时即可按助手 `item_id` 累积 PCM；不等待 `conversation.item.created`、`response.content_part.added` 或 `response.done` 的固定先后顺序。  
- [ ] `output_index` / `content_index` 只用于诊断，不作为重播缓存主 key。  
- [ ] `delta` 为 null 或中断：不崩溃，缓存可为空或短于实时播放。  
- [ ] **发出** `conversation.item.truncate` 时（不等服务端回送）：按 `audio_end_ms` 裁剪该 `item_id` 的 PCM 缓冲，立即写入可重播缓存，并置 `truncated` 标志——**不再追加**后续迟到的 delta，也不等待 `response.audio.done`。  
- [ ] 被截断的助手条目：`response.done` 到达时**不覆盖**已裁剪的缓存。

---

## 7. 参考

- [stardust/docs/realtime_api_reference.md](../../stardust/docs/realtime_api_reference.md)  
- [stardust/docs/session_config.md](../../stardust/docs/session_config.md)（`hearing.turn_detection`）  
- Sample：`android-sdk/sample/.../VoiceViewModel.kt`、`ConversationRecordParser.kt`
