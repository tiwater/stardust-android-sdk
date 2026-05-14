package cn.ticos.stardust.sample.model

import androidx.compose.runtime.Immutable

import cn.ticos.stardust.sample.R

@Immutable
data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Ready,
    val agentName: String = "",
    val sessionConfigMode: SessionConfigMode = SessionConfigMode.AgentId,
    val statusResId: Int = R.string.status_ready,
    val transcripts: List<TranscriptItem> = emptyList(),
    val audioLevel: Float = 0f,
    val errorMessage: String? = null,
    val isConfigured: Boolean = false,
    val language: String = "en",
    val pttModeEnabled: Boolean = false,
    val pttSessionActive: Boolean = false,
    val pttPressed: Boolean = false,
    val isConnected: Boolean = false,
    val visionModeEnabled: Boolean = false,
    val visionSessionActive: Boolean = false,
    val visionResults: List<VisionResultItem> = emptyList(),
    val visionFps: Int = 2,
    /** 当前用于视觉推流的是否为后置摄像头（否则为前置）。 */
    val visionUseBackCamera: Boolean = true,
    /** 设备同时具备前置与后置时允许切换镜头。 */
    val canSwitchVisionCamera: Boolean = false,
    val visionStreaming: Boolean = false,
    val conversationRecords: List<ConversationRecord> = emptyList(),
    val conversationRecordCount: Int = 0,
    val userVoiceCount: Int = 0,
    val userTextCount: Int = 0,
    val assistantVoiceCount: Int = 0,
    val functionCallCount: Int = 0,
    val playableAudioItemIds: Set<String> = emptySet(),
    val playingItemId: String? = null,
    val audioCacheRevision: Long = 0L,
)
