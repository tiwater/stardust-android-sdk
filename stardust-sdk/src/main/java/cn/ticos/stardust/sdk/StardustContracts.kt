package cn.ticos.stardust.sdk

import cn.ticos.stardust.sdk.model.ResponseConfig
import cn.ticos.stardust.sdk.model.SessionConfig
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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
    suspend fun sendImage(
        imageUrl: String,
        prompt: String? = null,
        userId: String = "nobody",
        previousItemId: String? = null,
    )

    suspend fun sendMultimodalMessage(
        text: String?,
        imageUrls: List<String>,
        userId: String = "nobody",
        previousItemId: String? = null,
    )

    suspend fun createResponse(response: ResponseConfig = ResponseConfig.audio())
    suspend fun cancelResponse(userId: String = "nobody")
    suspend fun sendRawEvent(json: String)
    suspend fun close()
}

interface StardustAudio {
    /** 麦克风采集：与播放解耦，server_vad 下应长期为 [CaptureAudioState.Recording]。 */
    val captureState: StateFlow<CaptureAudioState>

    /** 本地扬声器播放（含队列中尚未写出的 PCM）。 */
    val playbackState: StateFlow<PlaybackAudioState>

    suspend fun startCapture()
    suspend fun stopCapture(commit: Boolean = true)
    suspend fun appendPcm(pcm16: ByteArray)
    suspend fun commit()
    suspend fun clear()
    suspend fun stopPlayback(clearQueue: Boolean = true)
    fun setPlaybackEnabled(enabled: Boolean)

    /** 服务端声明当前响应的音频流已结束（`response.audio.done`）。 */
    fun onServerResponseAudioDone() {}

    /** 整个响应结束（`response.done`），用于无音频或兜底清理播放侧语义。 */
    fun onServerResponseDone() {}

    /**
     * 麦克风采集的原始 PCM16 数据流。
     *
     * 音频格式：24 kHz, mono, 16-bit signed LE。
     * 每帧大小：1920 bytes（40 ms）。
     * 仅在 [CaptureAudioState.Recording] 期间发射。
     *
     * 缓冲策略：extraBufferCapacity = 64（约 2.5 秒），
     * 溢出时丢弃最旧帧（DROP_OLDEST），不阻塞采集线程。
     */
    val capturedPcm: SharedFlow<ByteArray>

    /**
     * 当前采集帧的 RMS 音量级别，归一化至 [0.0, 1.0]。
     *
     * 与 [capturedPcm] 同频更新（每 40 ms 一次），
     * 停止采集后重置为 0。适用于波形动画和音量指示器。
     */
    val captureLevel: StateFlow<Float>
}

interface StardustVideo {
    val state: StateFlow<VideoState>

    suspend fun connect()
    suspend fun sendJpegFrame(jpeg: ByteArray)
    suspend fun disconnect()
}

enum class StardustState {
    Idle,
    Connecting,
    Connected,
    SessionCreated,
    SessionUpdated,
    Reconnecting,
    Closing,
    Closed,
    Failed,
}

enum class CaptureAudioState {
    Idle,
    Recording,
    Stopping,
}

enum class PlaybackAudioState {
    Idle,
    Playing,
    Failed,
}

enum class VideoState {
    Idle,
    WaitingRealtime,
    Connecting,
    Connected,
    Disconnected,
    Failed,
}
