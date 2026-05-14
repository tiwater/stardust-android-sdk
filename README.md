# Stardust Android SDK

Stardust Android SDK 封装了 Stardust Realtime 与 Video WebSocket 协议，为 Android 应用、机器人终端和多模态设备提供稳定、低延迟、可扩展的客户端能力。

## 项目文档

项目相关的需求和设计文档已规整存放在 [docs/](docs/) 目录中：

- [PRD (产品需求文档)](docs/PRD.md) - 定义了 SDK 的功能范围、协议依据和验收标准。
- [SRS (软件需求规格说明书)](docs/SRS.md) - 将产品需求转化为具体的工程规格、API 定义和状态机逻辑。
- [TECH_DESIGN (技术设计文档)](docs/TECH_DESIGN.md) - 详细说明了 SDK 的模块划分、技术选型和分阶段实现计划。

## 目录结构

```text
android-sdk/
├── build.gradle.kts      # 根项目构建脚本
├── settings.gradle.kts   # 项目设置
├── gradle.properties     # Gradle 属性
├── README.md             # 本文档
├── docs/                 # 项目文档目录
│   ├── PRD.md
│   ├── SRS.md
│   └── TECH_DESIGN.md
└── stardust-sdk/         # SDK 核心库模块
    ├── build.gradle.kts
    └── src/              # 源代码与测试
```

## 核心能力

- **Realtime 会话**：支持连接、鉴权、Session 配置和全双工事件收发。
- **音频交互**：封装 PCM16 24kHz 音频采集与流式播放，支持打断逻辑。
- **多模态输入**：便捷发送文本、图片 URL 以及多模态混合消息。
- **视频感知**：支持上传二进制 JPEG 帧流，接收视觉分析结果。
- **工具调用**：完整支持 Function、MCP 和 Ticos MCP 工具配置。

## 技术栈

- **Language**: Kotlin (Kotlin-first, Java compatible)
- **Networking**: OkHttp WebSocket
- **Asynchronous**: Kotlin Coroutines & Flow
- **Serialization**: kotlinx.serialization
- **Android**: API Level 26+
