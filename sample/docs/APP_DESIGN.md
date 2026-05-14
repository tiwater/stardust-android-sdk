# Stardust Voice Agent Sample App 设计文档

## 1. 文档目的
本文档旨在定义 Stardust Android SDK 示例应用（Sample App）的设计规范与功能逻辑。该应用将作为开发者集成 SDK 的参考实现，重点展示语音交互（Realtime Voice）的核心能力。

## 2. 设计理念：极简扁平化 (Minimalist Flat Design)
为了突出 SDK 的核心功能并提供现代化的用户体验，本应用采用**极简扁平化**设计风格。

*   **克制 (Restraint)**：移除所有非必要的装饰（如阴影、复杂渐变、厚重的边框）。
*   **层级 (Hierarchy)**：通过纯色块、对比色和字号大小来区分视觉层级，而非依赖深度感。
*   **空间 (Negative Space)**：利用留白引导用户关注核心交互区域——语音球。
*   **直觉 (Intuition)**：所有的交互反馈均通过颜色变化和微动画（Micro-interactions）完成。

## 3. 核心功能
1.  **会话管理**：支持快速连接/断开 Stardust 服务。
2.  **语音交互**：支持全双工语音问答，具备服务端打断（VAD）和本地音频流式播放能力。
3.  **状态感知**：实时展示连接状态（Idle, Connecting, Listening, Thinking, Speaking）。
4.  **配置中心**：简易面板设置 Agent ID、Server URL、终端密钥（或 Group ID + Robot ID 联调）、自动播放等。
5.  **实时转写 (可选)**：在屏幕底端以扁平文本流形式展示最近的对话内容。

## 4. UI 视觉设计 (UI Visuals)

### 4.1 配色方案 (Color Palette)
| 角色 | 颜色 (Hex) | 用途 |
| :--- | :--- | :--- |
| **Background** | `#F8F9FA` | 全屏背景，极致清爽 |
| **Primary (Accent)** | `#007AFF` | 核心操作、活跃状态 |
| **Neutral** | `#343A40` | 文本、静默状态图标 |
| **Error** | `#FF3B30` | 错误提示、连接断开 |
| **Surface** | `#FFFFFF` | 扁平卡片背景 |

### 4.2 核心组件：语音交互中心 (Voice Hub)
*   **中心圆球 (The Orb)**：屏幕中央的一个纯色圆形，根据状态变换：
    *   **静默 (Idle)**：淡灰色圆圈。
    *   **监听 (Listening)**：蓝色实心圆，伴随轻微的呼吸律动（Scale 1.0 -> 1.05）。
    *   **思考 (Thinking)**：蓝色圆环旋转动画。
    *   **播放 (Speaking)**：蓝色圆球，根据音量大小产生扁平的波纹扩散效果。
*   **操作栏 (Bottom Bar)**：
    *   极简的图标按钮：[设置]、[日志/文本]、[重连]。

### 4.3 字体 (Typography)
*   使用 Android 系统默认的 `Inter` 或 `Roboto`。
*   **标题**：Medium 18sp，用于展示当前 Agent 名称。
*   **状态文本**：Regular 14sp，淡灰色，位于圆球正下方。

## 5. 交互流程 (Interaction Flow)
1.  **冷启动**：显示 App Logo 动画 -> 进入主界面。
2.  **建立连接**：点击中心圆球 -> 状态切换为 `Connecting` -> 成功后自动进入 `Idle` 并播放欢迎语。
3.  **对话**：用户直接说话 -> 圆球进入 `Listening` -> 停止说话后进入 `Thinking` -> 收到音频后进入 `Speaking`。
4.  **打断**：在 Agent 说话期间，用户说出关键词 -> Agent 立即停止（SDK 逻辑）-> 圆球切换回 `Listening`。

## 6. 技术架构 (Technical Stack)
*   **UI 框架**：Jetpack Compose (声明式 UI，天然适合状态驱动的扁平化设计)。
*   **状态管理**：ViewModel + Kotlin Flow (与 SDK 的 StateFlow 完美契合)。
*   **核心引擎**：`stardust-sdk`。
*   **权限管理**：快捷处理麦克风权限。

## 7. 预览图 (Mockup Concept)
```text
+---------------------------+
|      [ Agent Name ]       |
|                           |
|                           |
|             O             |  <-- 语音圆球 (状态动画)
|         [Status...]       |
|                           |
|                           |
|                           |
| [Text Feedback Preview..] |
|                           |
|  (S)        (R)        (H)  |  <-- 设置、重连、历史记录
+---------------------------+
```
