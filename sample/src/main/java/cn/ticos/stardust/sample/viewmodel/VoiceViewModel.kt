package cn.ticos.stardust.sample.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.camera.CameraFrameCapture
import cn.ticos.stardust.sample.data.SettingsRepository
import cn.ticos.stardust.sample.model.AppSettings
import cn.ticos.stardust.sample.model.SessionConfigMode
import cn.ticos.stardust.sample.model.STARDUST_DEBUG_BEARER_TOKEN
import cn.ticos.stardust.sample.model.toSessionConfig
import cn.ticos.stardust.sample.model.ConversationRecord
import cn.ticos.stardust.sample.model.TranscriptItem
import cn.ticos.stardust.sample.model.VisionResultItem
import cn.ticos.stardust.sample.model.VoicePhase
import cn.ticos.stardust.sample.model.VoiceUiState
import cn.ticos.stardust.sdk.CaptureAudioState
import cn.ticos.stardust.sdk.PlaybackAudioState
import cn.ticos.stardust.sdk.ReconnectPolicy
import cn.ticos.stardust.sdk.StardustClient
import cn.ticos.stardust.sdk.StardustConfig
import cn.ticos.stardust.sdk.StardustEvent
import cn.ticos.stardust.sdk.StardustLogLevel
import cn.ticos.stardust.sdk.StardustSdk
import cn.ticos.stardust.sdk.StardustState
import cn.ticos.stardust.sdk.VideoConnectPolicy
import cn.ticos.stardust.sdk.VideoState
import cn.ticos.stardust.sdk.model.HearingConfig
import cn.ticos.stardust.sdk.model.SessionConfig
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

private const val MAX_TRANSCRIPTS = 20
private const val ERROR_DISPLAY_MS = 4_000L
private const val REALTIME_READY_TIMEOUT_MS = 20_000L
private const val REPLAY_SAMPLE_RATE = 24_000
private const val REPLAY_BYTES_PER_SAMPLE = 2
private const val MAX_AUDIO_CACHE_BYTES = 15_728_640L // 15 MB
private const val ASSISTANT_AUDIO_FINALIZE_DELAY_MS = 600L
private const val USER_PCM_DRAIN_DELAY_MS = 80L
/** Server VAD：ring 时间轴追上 `audio_end_ms` 的轮询间隔（与一帧采集周期一致）。 */
private const val SERVER_VAD_RING_CATCHUP_POLL_MS = 40L
/** 仍追不上时最多等待多久再 `force` finalize，避免无限挂起（与原先单次 delay 上限同量级）。 */
private const val SERVER_VAD_RING_CATCHUP_MAX_WAIT_MS = 600L
// 用户语音环形缓冲：每帧 1920 字节 ≈ 40ms（24kHz 16-bit mono），保留最近 15 秒
private const val USER_PCM_RING_MAX_FRAMES = 375 // 375 × 40ms = 15s

class VoiceViewModel(
    private val application: Application,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    companion object {
        const val FALSE_TOUCH_MS = 250L
        const val DEFAULT_VISION_FPS = 2
        const val MAX_VISION_RESULTS = 10
        const val MAX_CONVERSATION_RECORDS = 100
        val ALLOWED_FPS_OPTIONS = listOf(1, 2, 5)
    }

    private val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    private val _client = MutableStateFlow<StardustClient?>(null)
    private val _awaitingResponse = MutableStateFlow(false)
    private val _transcripts = MutableStateFlow<List<TranscriptItem>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _audioLevel = MutableStateFlow(0f)
    private val _connectionWiring = MutableStateFlow(false)

    private val _pttModeEnabled = MutableStateFlow(false)
    private var _pttSessionActive = false
    private val _pttPressed = MutableStateFlow(false)
    private var _pttDownTimestamp: Long = 0L
    private var _pttCancelFired: Boolean = false
    private var _pttThresholdJob: Job? = null

    private val _visionModeEnabled = MutableStateFlow(false)
    private var _visionSessionActive = false
    private val _visionResults = MutableStateFlow<List<VisionResultItem>>(emptyList())
    private val _visionFps = MutableStateFlow(DEFAULT_VISION_FPS)
    private val _visionUseBackCamera = MutableStateFlow(true)
    private val _visionStreaming = MutableStateFlow(false)
    private var _visionCaptureJob: Job? = null
    private var _cameraFrameCapture: CameraFrameCapture? = null
    @Volatile
    private var acceptVisionResults: Boolean = false
    @Volatile
    private var cameraLifecycleOwner: LifecycleOwner? = null
    @Volatile
    private var cameraSurfaceProvider: Preview.SurfaceProvider? = null

    private var observeJob: Job? = null

    // --- 对话信息 ---
    private val _conversationRecords = MutableStateFlow<List<ConversationRecord>>(emptyList())
    private var _conversationSessionId: Long = 0L

    // --- 音频缓存（重播用）---
    private val _assistantAudioAccum = HashMap<String, ByteArrayOutputStream>()
    private val _assistantAudioFinalizeJobs = HashMap<String, Job>()
    private val _audioPcm = LinkedHashMap<String, ByteArray>()
    private var _audioCacheTotalBytes = 0L
    // 当前正在累积的助手音频 item_id（用于打断时定位需截断的条目）
    private var _currentAssistantItemId: String? = null
    // 已被截断的助手音频 item_id 集合；这些条目不再接受迟到的 delta，也不被 response.done 二次覆盖
    private val _truncatedAssistantItemIds = HashSet<String>()

    // 用户语音环形缓冲：始终保留最近 USER_PCM_RING_MAX_FRAMES 帧，新帧总能写入（挤掉最旧帧）
    // 所有访问均在 Main 线程（viewModelScope），无需同步
    private val _userPcmRingFrames = ArrayDeque<UserPcmFrame>()
    private var _userAudioTimelineSamples: Long = 0L
    // pttPressUp 后暂存的语音窗口 PCM，等待 itemId（committed / transcription.completed）
    private var _pendingUserAudioPcm: ByteArray? = null
    @Volatile private var _isCollectingUserSpeech = false
    /** Server VAD：按 item_id 聚合分段；finalize 后从 map 移除，仅保留 [_audioPcm]。 */
    private val _pendingServerVadSpeechSegments = LinkedHashMap<String, PendingUserSpeechSegment>()
    private var _pendingSpeechFinalizeJob: Job? = null
    /** 已 finalize 的 segment 最大 endSample，用于在无非未完成分段时安全裁剪 ring。 */
    private var _serverVadMaxFinalizedEndSample: Long = 0L

    private val _playableAudioItemIds = MutableStateFlow<Set<String>>(emptySet())
    private val _playingItemId = MutableStateFlow<String?>(null)
    private val _audioCacheRevision = MutableStateFlow(0L)
    private var _replayJob: Job? = null

    private data class UserPcmFrame(
        val pcm: ByteArray,
        val startSample: Long,
        val endSample: Long,
    )

    private data class PendingUserSpeechSegment(
        val itemId: String,
        var startMs: Long = -1L,
        var endMs: Long = -1L,
    )

    fun setCameraLifecycleOwner(owner: LifecycleOwner?) {
        cameraLifecycleOwner = owner
    }

    fun setCameraSurfaceProvider(provider: Preview.SurfaceProvider?) {
        cameraSurfaceProvider = provider
        // If vision is already active, we might need to re-bind to show the preview
        val client = _client.value ?: return
        if (!_visionSessionActive) return
        if (client.video.state.value != VideoState.Connected) return
        val owner = cameraLifecycleOwner ?: return
        viewModelScope.launch {
            beginVisionCapture(client, owner)
        }
    }

    fun toggleVisionMode(enabled: Boolean) {
        if (_client.value != null) return
        _visionModeEnabled.value = enabled
    }

    fun setVisionFps(fps: Int) {
        if (_client.value != null) return
        require(fps in ALLOWED_FPS_OPTIONS) { "FPS must be one of $ALLOWED_FPS_OPTIONS" }
        _visionFps.value = fps
    }

    /** 同时具备系统声明的后置与前置摄像头时才允许切换。 */
    private val canSwitchVisionCamera: Boolean =
        application.packageManager.let { pm ->
            pm.hasSystemFeature(PackageManager.FEATURE_CAMERA) &&
                pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)
        }

    fun toggleVisionCamera() {
        if (!canSwitchVisionCamera) return
        _visionUseBackCamera.value = !_visionUseBackCamera.value
        val client = _client.value ?: return
        if (!_visionSessionActive) return
        if (client.video.state.value != VideoState.Connected) return
        val owner = cameraLifecycleOwner ?: return
        viewModelScope.launch {
            try {
                beginVisionCapture(client, owner)
            } catch (t: Throwable) {
                showTransientError("[vision] ${t.message ?: "Camera switch failed"}")
            }
        }
    }

    fun onAppBackground() {
        if (!_visionSessionActive) return
        pauseVisionCaptureOnly()
    }

    fun onAppForeground() {
        if (!_visionSessionActive) return
        val client = _client.value ?: return
        if (client.video.state.value != VideoState.Connected) return
        val owner = cameraLifecycleOwner ?: return
        viewModelScope.launch {
            try {
                beginVisionCapture(client, owner)
            } catch (t: Throwable) {
                showTransientError("[vision] ${t.message ?: "Camera resume failed"}")
            }
        }
    }

    private fun addConversationRecord(
        record: ConversationRecord,
        previousItemId: String? = null,
    ) {
        if (record.sessionId != _conversationSessionId) return
        _conversationRecords.value = _conversationRecords.value.withInsertedAfterPreviousItem(
            record = record,
            previousItemId = previousItemId,
            maxRecords = MAX_CONVERSATION_RECORDS,
        )
    }

    private fun upsertFunctionCallRecord(record: ConversationRecord.FunctionCall) {
        if (record.sessionId != _conversationSessionId) return
        var updated = false
        val records = _conversationRecords.value.map { existing ->
            if (existing is ConversationRecord.FunctionCall && existing.matchesFunctionCall(record)) {
                updated = true
                existing.mergeFunctionCall(record)
            } else {
                existing
            }
        }
        _conversationRecords.value = if (updated) {
            records
        } else {
            (records + record).takeLast(MAX_CONVERSATION_RECORDS)
        }
    }

    private fun updateConversationRecord(
        itemId: String,
        updater: (ConversationRecord) -> ConversationRecord,
    ) {
        _conversationRecords.value = _conversationRecords.value.map { existing ->
            if (existing.itemIdOrNull() == itemId) updater(existing) else existing
        }
    }

    private fun findRecordByItemId(itemId: String): ConversationRecord? =
        _conversationRecords.value.find { record ->
            record.itemIdOrNull() == itemId
        }

    private fun existingItemIds(): Set<String> =
        _conversationRecords.value.mapNotNull { record ->
            record.itemIdOrNull()
        }.toSet()

    private fun clearConversationRecords() {
        _conversationSessionId++
        _conversationRecords.value = emptyList()
        _assistantAudioFinalizeJobs.values.forEach { it.cancel() }
        _assistantAudioFinalizeJobs.clear()
        _assistantAudioAccum.clear()
        _currentAssistantItemId = null
        _truncatedAssistantItemIds.clear()
        _audioPcm.clear()
        _audioCacheTotalBytes = 0L
        _pendingUserAudioPcm = null
        _pendingServerVadSpeechSegments.clear()
        _pendingSpeechFinalizeJob?.cancel()
        _pendingSpeechFinalizeJob = null
        _serverVadMaxFinalizedEndSample = 0L
        _userPcmRingFrames.clear()
        _userAudioTimelineSamples = 0L
        _playableAudioItemIds.value = emptySet()
        _audioCacheRevision.value = _audioCacheRevision.value + 1
        stopAudioReplay()
    }

    private fun cacheAudio(itemId: String, pcm: ByteArray) {
        if (pcm.isEmpty()) return
        var cacheChanged = false
        // 若同 itemId 已有缓存，先移除旧数据
        _audioPcm.remove(itemId)?.let { old ->
            _audioCacheTotalBytes -= old.size
            cacheChanged = true
        }
        // 超出上限时按插入顺序淘汰最旧的条目
        while (_audioCacheTotalBytes + pcm.size > MAX_AUDIO_CACHE_BYTES && _audioPcm.isNotEmpty()) {
            val (oldId, oldPcm) = _audioPcm.entries.iterator().next()
            _audioPcm.remove(oldId)
            _audioCacheTotalBytes -= oldPcm.size
            _playableAudioItemIds.value = _playableAudioItemIds.value - oldId
            cacheChanged = true
        }
        _audioPcm[itemId] = pcm
        _audioCacheTotalBytes += pcm.size
        _playableAudioItemIds.value = _playableAudioItemIds.value + itemId
        // 独立 revision 确保“最后一条只改变可播放状态”时对话页也会立即重组。
        if (cacheChanged || itemId in _playableAudioItemIds.value) {
            _audioCacheRevision.value = _audioCacheRevision.value + 1
        }
    }

    private fun appendAssistantAudio(itemId: String, pcm: ByteArray): Boolean {
        val hadFinalizeJob = _assistantAudioFinalizeJobs.remove(itemId)?.let { job ->
            job.cancel()
            true
        } ?: false
        val stream = _assistantAudioAccum.getOrPut(itemId) {
            ByteArrayOutputStream().also { existing ->
                _audioPcm[itemId]?.let { cached ->
                    existing.write(cached)
                }
            }
        }
        stream.write(pcm)
        return hadFinalizeJob
    }

    private fun scheduleAssistantAudioFinalization(itemId: String) {
        if (_truncatedAssistantItemIds.contains(itemId)) return
        _assistantAudioFinalizeJobs.remove(itemId)?.cancel()
        _assistantAudioFinalizeJobs[itemId] = viewModelScope.launch {
            // OkHttp hands messages to coroutines; a tiny debounce prevents audio.done/response.done
            // from sealing the replay cache before the last audio.delta is collected.
            delay(ASSISTANT_AUDIO_FINALIZE_DELAY_MS)
            _assistantAudioFinalizeJobs.remove(itemId)
            val stream = _assistantAudioAccum.remove(itemId) ?: return@launch
            val pcm = stream.toByteArray()
            if (pcm.isNotEmpty()) {
                cacheAudio(itemId, pcm)
            }
        }
    }

    fun playAudio(itemId: String) {
        val pcm = _audioPcm[itemId] ?: return
        if (pcm.isEmpty()) return
        _replayJob?.cancel()
        _replayJob = viewModelScope.launch(Dispatchers.IO) {
            var track: AudioTrack? = null
            try {
                _playingItemId.value = itemId
                val minBuffer = AudioTrack.getMinBufferSize(
                    REPLAY_SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
                if (minBuffer <= 0) return@launch
                // 使用 MODE_STREAM + 分块写入：MODE_STATIC 在 PCM 较大（多句助手语音 ≥ 1MB）时，
                // 内核分配的 buffer 可能小于请求值，导致 write 截断只播前段，造成"只播 1~2 句"。
                // MODE_STREAM 的 write 会阻塞直到内部缓冲腾出空间，分块 push 即可播完任意长度。
                val streamBufSize = (minBuffer * 4).coerceAtLeast(minBuffer)
                track = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(REPLAY_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    streamBufSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
                if (track.state != AudioTrack.STATE_INITIALIZED) return@launch
                track.play()

                // 分块写入直到全部 PCM 推完；MODE_STREAM 下 write 默认阻塞。
                val chunkSize = streamBufSize
                var offset = 0
                while (offset < pcm.size && isActive) {
                    val remain = pcm.size - offset
                    val toWrite = if (remain < chunkSize) remain else chunkSize
                    val written = track.write(pcm, offset, toWrite)
                    if (written <= 0) break
                    offset += written
                }
                // 写入完成只代表 PCM 进了 AudioTrack buffer；等待播放头追上，避免截掉最后一句。
                val writtenFrames = offset / 2L
                val maxDrainMs = ((writtenFrames * 1000L) / REPLAY_SAMPLE_RATE) + 1_000L
                val deadline = SystemClock.elapsedRealtime() + maxDrainMs
                while (isActive &&
                    track.playState == AudioTrack.PLAYSTATE_PLAYING &&
                    track.playbackHeadPosition.toLong() < writtenFrames &&
                    SystemClock.elapsedRealtime() < deadline
                ) {
                    delay(20L)
                }
            } finally {
                track?.runCatching { stop(); release() }
                if (_playingItemId.value == itemId) {
                    _playingItemId.value = null
                }
            }
        }
    }

    fun stopAudioReplay() {
        _replayJob?.cancel()
        _replayJob = null
        _playingItemId.value = null
    }

    fun togglePttMode(enabled: Boolean) {
        if (_client.value != null) return
        _pttModeEnabled.value = enabled
    }

    fun pttPressDown() {
        if (!_pttSessionActive) return
        if (_pttPressed.value) return
        val client = _client.value ?: return

        _pttPressed.value = true
        _pttDownTimestamp = SystemClock.elapsedRealtime()
        _pttCancelFired = false

        // Client VAD 的用户语音缓存边界是 commit-to-commit：
        // 本次 commit 前、上次 commit 后所有 append 都属于本次用户消息。
        // 因此这里不清 ring，也不清 pending，避免丢掉尚未绑定 itemId 的上一轮音频。
        _isCollectingUserSpeech = true

        viewModelScope.launch {
            try {
                client.audio.startCapture()
            } catch (t: Throwable) {
                _pttPressed.value = false
                _pttThresholdJob?.cancel()
                _isCollectingUserSpeech = false
                showTransientError("[ptt] ${t.message ?: "startCapture failed"}")
            }
        }

        _pttThresholdJob?.cancel()
        _pttThresholdJob = viewModelScope.launch {
            val remaining = FALSE_TOUCH_MS - (SystemClock.elapsedRealtime() - _pttDownTimestamp)
            if (remaining > 0) delay(remaining)
            if (_pttPressed.value && !_pttCancelFired) {
                _pttCancelFired = true
                try {
                    client.cancelResponse()
                } catch (_: Throwable) {
                }
                // 打断时立即截断并保存当前助手音频，防止迟到 delta 污染缓存
                truncateCurrentAssistantAudio()
            }
        }
    }

    fun pttPressUp() {
        if (!_pttSessionActive) return
        val client = _client.value ?: return
        if (!_pttPressed.value) return

        _pttPressed.value = false
        _pttThresholdJob?.cancel()
        _pttThresholdJob = null
        _isCollectingUserSpeech = false

        val elapsed = SystemClock.elapsedRealtime() - _pttDownTimestamp

        viewModelScope.launch {
            if (elapsed >= FALSE_TOUCH_MS) {
                if (!_pttCancelFired) {
                    _pttCancelFired = true
                    try {
                        client.cancelResponse()
                    } catch (_: Throwable) {
                    }
                    // 打断时立即截断并保存当前助手音频，防止迟到 delta 污染缓存
                    truncateCurrentAssistantAudio()
                }
                try {
                    client.audio.stopCapture(commit = false)
                } catch (_: Throwable) {
                }
                // stopCapture 会取消采集协程；稍等 SharedFlow 中已发出的 capturedPcm 落到 ring。
                // 然后再 commit，确保 committed/transcription 事件到达时 pending 已准备好。
                delay(USER_PCM_DRAIN_DELAY_MS)
                // Client VAD/PTT：上次 commit 后到本次 commit 前累计的所有 append
                // 都属于本次用户消息。
                _pendingUserAudioPcm = ringFramesToPcmOrNull()
                _userPcmRingFrames.clear()
                _userAudioTimelineSamples = 0L
                try {
                    client.audio.commit()
                } catch (_: Throwable) {
                }
            } else {
                _userPcmRingFrames.clear()
                _userAudioTimelineSamples = 0L
                try {
                    client.audio.stopCapture(commit = false)
                } catch (_: Throwable) {
                }
                try {
                    client.audio.clear()
                } catch (_: Throwable) {
                }
            }
        }
    }

    private data class SettingsAndVisionToggles(
        val settings: AppSettings,
        val pttModeEnabled: Boolean,
        val visionModeEnabled: Boolean,
        val visionFps: Int,
    )

    private data class AudioReplayData(
        val playableAudioItemIds: Set<String>,
        val playingItemId: String?,
        val audioCacheRevision: Long,
    )

    private data class PlaybackData(
        val visionResults: List<VisionResultItem>,
        val visionStreaming: Boolean,
        val conversationRecords: List<ConversationRecord>,
        val playableAudioItemIds: Set<String>,
        val playingItemId: String?,
        val audioCacheRevision: Long,
    )

    private data class VisionUiSlice(
        val settings: AppSettings,
        val pttModeEnabled: Boolean,
        val visionModeEnabled: Boolean,
        val visionFps: Int,
        val visionUseBackCamera: Boolean,
        val visionResults: List<VisionResultItem>,
        val visionStreaming: Boolean,
        val conversationRecords: List<ConversationRecord>,
        val playableAudioItemIds: Set<String>,
        val playingItemId: String?,
        val audioCacheRevision: Long,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<VoiceUiState> = combine(_client, _connectionWiring) { c, w -> c to w }
        .flatMapLatest { (client, wiring) ->
            val audioReplayFlow = combine(
                _playableAudioItemIds,
                _playingItemId,
                _audioCacheRevision,
            ) { playableIds, playingId, audioCacheRevision ->
                AudioReplayData(playableIds, playingId, audioCacheRevision)
            }
            if (client == null) {
                combine(
                    combine(settings, _errorMessage) { s, err -> s to err },
                    combine(_pttModeEnabled, _visionModeEnabled, _visionFps) { pttMode, visionMode, visionFps ->
                        Triple(pttMode, visionMode, visionFps)
                    },
                    _conversationRecords,
                    audioReplayFlow,
                    _visionUseBackCamera,
                ) { pair, triple, convRecords, audioReplay, useBackCamera ->
                    val (s, err) = pair
                    val (pttMode, visionMode, visionFps) = triple
                    VoiceUiState(
                        phase = VoicePhase.Ready,
                        agentName = displayAgentName(s),
                        sessionConfigMode = s.sessionConfigMode,
                        statusResId = VoicePhase.Ready.toStatusResId(),
                        transcripts = emptyList(),
                        audioLevel = 0f,
                        errorMessage = err,
                        isConfigured = checkIsConfigured(s),
                        language = s.language,
                        pttModeEnabled = pttMode,
                        pttSessionActive = false,
                        pttPressed = false,
                        isConnected = false,
                        visionModeEnabled = visionMode,
                        visionSessionActive = false,
                        visionResults = emptyList(),
                        visionFps = visionFps,
                        visionUseBackCamera = useBackCamera,
                        canSwitchVisionCamera = canSwitchVisionCamera,
                        visionStreaming = false,
                        conversationRecords = convRecords,
                        conversationRecordCount = convRecords.size,
                        userVoiceCount = convRecords.count { it is ConversationRecord.UserVoice },
                        userTextCount = convRecords.count { it is ConversationRecord.UserText },
                        assistantVoiceCount = convRecords.count { it is ConversationRecord.AssistantVoice },
                        functionCallCount = convRecords.count { it is ConversationRecord.FunctionCall },
                        playableAudioItemIds = audioReplay.playableAudioItemIds,
                        playingItemId = audioReplay.playingItemId,
                        audioCacheRevision = audioReplay.audioCacheRevision,
                    )
                }
            } else {
                val togglesFlow = combine(settings, _pttModeEnabled, _visionModeEnabled, _visionFps) { s, p, vm, vf ->
                    SettingsAndVisionToggles(s, p, vm, vf)
                }
                val visionListFlow = combine(
                    _visionResults, _visionStreaming, _conversationRecords,
                    audioReplayFlow,
                ) { r, st, cr, audioReplay ->
                    PlaybackData(
                        r,
                        st,
                        cr,
                        audioReplay.playableAudioItemIds,
                        audioReplay.playingItemId,
                        audioReplay.audioCacheRevision,
                    )
                }
                val visionSliceFlow = combine(togglesFlow, visionListFlow, _visionUseBackCamera) { t, data, useBackCam ->
                    VisionUiSlice(
                        settings = t.settings,
                        pttModeEnabled = t.pttModeEnabled,
                        visionModeEnabled = t.visionModeEnabled,
                        visionFps = t.visionFps,
                        visionUseBackCamera = useBackCam,
                        visionResults = data.visionResults,
                        visionStreaming = data.visionStreaming,
                        conversationRecords = data.conversationRecords,
                        playableAudioItemIds = data.playableAudioItemIds,
                        playingItemId = data.playingItemId,
                        audioCacheRevision = data.audioCacheRevision,
                    )
                }
                combine(
                    combine(
                        client.state,
                        client.audio.captureState,
                        client.audio.playbackState,
                        _awaitingResponse,
                        _pttPressed,
                    ) { sdk, cap, play, awaiting, pttPressed ->
                        VoicePhaseInputs(sdk, cap, play, awaiting, pttPressed)
                    },
                    _transcripts,
                    _errorMessage,
                    _audioLevel,
                    visionSliceFlow,
                ) { inputs, transcripts, err, level, vx ->
                    val phase = mapToVoicePhase(
                        inputs.sdkState,
                        inputs.captureState,
                        inputs.playbackState,
                        inputs.awaitingResponse,
                        wiring,
                    )
                    val connected = !inputs.sdkState.isDisconnected &&
                        inputs.sdkState != StardustState.Failed
                    val showLevel = phase == VoicePhase.Listening ||
                        phase == VoicePhase.Speaking ||
                        (_pttSessionActive && inputs.pttPressed)
                    VoiceUiState(
                        phase = phase,
                        agentName = displayAgentName(vx.settings),
                        sessionConfigMode = vx.settings.sessionConfigMode,
                        statusResId = phase.toStatusResId(),
                        transcripts = transcripts,
                        audioLevel = if (showLevel) level else 0f,
                        errorMessage = err,
                        isConfigured = checkIsConfigured(vx.settings),
                        language = vx.settings.language,
                        pttModeEnabled = vx.pttModeEnabled,
                        pttSessionActive = _pttSessionActive,
                        pttPressed = inputs.pttPressed,
                        isConnected = connected,
                        visionModeEnabled = vx.visionModeEnabled,
                        visionSessionActive = _visionSessionActive,
                        visionResults = vx.visionResults,
                        visionFps = vx.visionFps,
                        visionUseBackCamera = vx.visionUseBackCamera,
                        canSwitchVisionCamera = canSwitchVisionCamera,
                        visionStreaming = vx.visionStreaming,
                        conversationRecords = vx.conversationRecords,
                        conversationRecordCount = vx.conversationRecords.size,
                        userVoiceCount = vx.conversationRecords.count { it is ConversationRecord.UserVoice },
                        userTextCount = vx.conversationRecords.count { it is ConversationRecord.UserText },
                        assistantVoiceCount = vx.conversationRecords.count { it is ConversationRecord.AssistantVoice },
                        functionCallCount = vx.conversationRecords.count { it is ConversationRecord.FunctionCall },
                        playableAudioItemIds = vx.playableAudioItemIds,
                        playingItemId = vx.playingItemId,
                        audioCacheRevision = vx.audioCacheRevision,
                    )
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            VoiceUiState(canSwitchVisionCamera = canSwitchVisionCamera),
        )

    private fun checkIsConfigured(s: AppSettings): Boolean {
        if (!s.serverUrl.startsWith("wss://") && !s.serverUrl.startsWith("ws://")) return false
        val secretOk = s.terminalSecret.isNotBlank()
        val pairOk = s.groupId.isNotBlank() && s.robotId.isNotBlank()
        if (!secretOk && !pairOk) return false
        return when (s.sessionConfigMode) {
            SessionConfigMode.AgentId -> s.agentId.isNotBlank()
            SessionConfigMode.Advanced -> {
                val adv = s.advancedSession
                adv.modelProvider.isNotBlank() &&
                    adv.modelName.isNotBlank() &&
                    adv.instructions.isNotBlank() &&
                    adv.speechVoice.isNotBlank()
            }
        }
    }

    fun onOrbClicked() {
        when (uiState.value.phase) {
            VoicePhase.Idle,
            VoicePhase.Listening,
            VoicePhase.Thinking,
            VoicePhase.Speaking,
            -> disconnect()
            VoicePhase.Connecting -> Unit
            VoicePhase.Ready,
            VoicePhase.Error,
            -> connectFromUser()
        }
    }

    fun onReconnectClicked() {
        viewModelScope.launch {
            disconnectSuspend()
            connectInternal()
        }
    }

    fun onDisconnect() {
        disconnect()
    }

    fun toggleLanguage() {
        val currentLang = settings.value.language
        val nextLang = if (currentLang == "en") "zh" else "en"
        viewModelScope.launch {
            // 先向 AppCompatDelegate 注册新 locale，确保 Activity 重建时
            // attachBaseContext2() 能立即拿到正确的 locale。
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(nextLang)
            AppCompatDelegate.setApplicationLocales(appLocale)
            // 再持久化到 DataStore，触发 MainActivity 中的 recreate()。
            settingsRepo.update { it.copy(language = nextLang) }
        }
    }

    fun showUserMessage(message: String) {
        showTransientError(message)
    }

    private fun connectFromUser() {
        val s = settings.value
        if (!s.serverUrl.startsWith("wss://") && !s.serverUrl.startsWith("ws://")) {
            showTransientError("[config] Server URL must start with ws:// or wss://")
            return
        }
        val secretOk = s.terminalSecret.isNotBlank()
        val pairOk = s.groupId.isNotBlank() && s.robotId.isNotBlank()
        if (!secretOk && !pairOk) {
            showTransientError("[config] Please fill in Terminal Secret, or both Group ID and Robot ID")
            return
        }
        when (s.sessionConfigMode) {
            SessionConfigMode.AgentId -> {
                if (s.agentId.isBlank()) {
                    showTransientError("[config] Agent ID is required")
                    return
                }
            }
            SessionConfigMode.Advanced -> {
                val err = validateAdvancedSession(s.advancedSession)
                if (err != null) {
                    showTransientError("[advanced session] $err")
                    return
                }
            }
        }
        viewModelScope.launch {
            connectInternal()
        }
    }

    private suspend fun connectInternal() {
        observeJob?.cancel()
        stopVisionPipelineFull()
        val previous = _client.value
        if (previous != null) {
            runCatching { previous.audio.stopCapture(commit = false) }
            runCatching { previous.close() }
            _client.value = null
        }
        _transcripts.value = emptyList()
        _awaitingResponse.value = false

        clearConversationRecords()

        val s = settings.value
        _connectionWiring.value = true
        _pttSessionActive = _pttModeEnabled.value
        _visionSessionActive = _visionModeEnabled.value

        val stardustConfig = buildStardustConfig(s)
        val client = StardustSdk.create(stardustConfig)
        _client.value = client
        startObserve(client)
        try {
            client.connect()
            val baseSessionConfig = when (s.sessionConfigMode) {
                SessionConfigMode.AgentId -> SessionConfig(
                    agentId = s.agentId.takeIf { it.isNotBlank() },
                )
                SessionConfigMode.Advanced -> s.advancedSession.toSessionConfig()
            }
            val sessionConfig = if (_pttSessionActive) {
                baseSessionConfig.copy(
                    hearing = (baseSessionConfig.hearing ?: HearingConfig()).copy(
                        turnDetection = JsonNull,
                    ),
                )
            } else {
                baseSessionConfig
            }
            client.updateSession(sessionConfig)
            if (!_pttSessionActive) {
                client.audio.startCapture()
            }
            if (_visionSessionActive) {
                startVisionPipeline(client, stardustConfig)
            }
        } catch (t: Throwable) {
            val prefix = if (s.sessionConfigMode == SessionConfigMode.Advanced) {
                "[advanced session] "
            } else {
                "[connect] "
            }
            showTransientError("$prefix${t.message ?: "unknown"}")
            stopVisionPipelineFull()
            observeJob?.cancel()
            observeJob = null
            runCatching { client.audio.stopCapture(commit = false) }
            runCatching { client.close() }
            _client.value = null
            _audioLevel.value = 0f
        } finally {
            _connectionWiring.value = false
        }
    }

    private fun deriveVideoUrl(realtimeUrl: String): String {
        val t = realtimeUrl.trim().trimEnd('/')
        return if (t.endsWith("/realtime", ignoreCase = true)) {
            t.dropLast("/realtime".length) + "/video"
        } else {
            "$t/video"
        }
    }

    private fun buildStardustConfig(settings: AppSettings): StardustConfig {
        val secret = settings.terminalSecret.trim()
        val hasTerminalSecret = secret.isNotEmpty()
        val videoFps = if (_visionSessionActive) {
            _visionFps.value.coerceIn(1, 30)
        } else {
            2
        }
        return StardustConfig(
            realtimeUrl = settings.serverUrl,
            videoUrl = deriveVideoUrl(settings.serverUrl),
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
            videoMaxFps = videoFps,
        )
    }

    private fun hasCameraHardware(): Boolean =
        application.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    private suspend fun waitForRealtimeVideoReady(client: StardustClient, policy: VideoConnectPolicy): Boolean {
        val ok = withTimeoutOrNull(REALTIME_READY_TIMEOUT_MS) {
            client.state.first { st ->
                when (policy) {
                    VideoConnectPolicy.RequireSessionCreated ->
                        st == StardustState.SessionCreated || st == StardustState.SessionUpdated
                    VideoConnectPolicy.RequireSessionUpdated ->
                        st == StardustState.SessionUpdated
                }
            }
            true
        }
        return ok == true
    }

    private suspend fun startVisionPipeline(client: StardustClient, sessionConfig: StardustConfig) {
        if (!hasCameraHardware()) {
            showTransientError(application.getString(R.string.vision_no_camera))
            return
        }
        val owner = cameraLifecycleOwner ?: run {
            showTransientError("[vision] Camera lifecycle not ready")
            return
        }
        if (!waitForRealtimeVideoReady(client, sessionConfig.videoConnectPolicy)) {
            showTransientError("[vision] Timeout waiting for realtime session")
            return
        }
        try {
            client.video.connect()
        } catch (t: Throwable) {
            showTransientError("[vision] ${t.message ?: "Video connect failed"}")
            return
        }
        acceptVisionResults = true
        try {
            beginVisionCapture(client, owner)
        } catch (t: Throwable) {
            acceptVisionResults = false
            pauseVisionCaptureOnly()
            runCatching { client.video.disconnect() }
            showTransientError("[vision] ${t.message ?: "Camera start failed"}")
        }
    }

    private suspend fun beginVisionCapture(client: StardustClient, owner: LifecycleOwner) {
        _visionCaptureJob?.cancel()
        _visionCaptureJob = null
        _cameraFrameCapture?.stop()
        val capture = CameraFrameCapture(owner, application)
        _cameraFrameCapture = capture
        val selector = if (_visionUseBackCamera.value) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        capture.start(_visionFps.value, selector, cameraSurfaceProvider)
        _visionStreaming.value = true
        _visionCaptureJob = viewModelScope.launch(Dispatchers.IO) {
            capture.frames.collect { jpeg ->
                try {
                    client.video.sendJpegFrame(jpeg)
                } catch (t: Throwable) {
                    handleVisionSendError(t)
                }
            }
        }
    }

    private fun handleVisionSendError(t: Throwable) {
        showTransientError("[vision] ${t.message ?: "Vision frame send failed"}")
        _visionCaptureJob?.cancel()
        _visionCaptureJob = null
        viewModelScope.launch {
            stopVisionPipelineFull()
        }
    }

    private fun pauseVisionCaptureOnly() {
        _visionCaptureJob?.cancel()
        _visionCaptureJob = null
        _cameraFrameCapture?.stop()
        _cameraFrameCapture = null
        _visionStreaming.value = false
    }

    private suspend fun stopVisionPipelineFull() {
        acceptVisionResults = false
        pauseVisionCaptureOnly()
        val c = _client.value
        if (c != null) {
            runCatching { c.video.disconnect() }
        }
    }

    private fun startObserve(client: StardustClient) {
        observeJob?.cancel()
        val sessionId = _conversationSessionId
        observeJob = viewModelScope.launch {
            supervisorScope {
                launch {
                    client.audio.captureLevel.collect { level ->
                        _audioLevel.value = level
                    }
                }
                launch {
                    client.audio.capturedPcm.collect { frame ->
                        // 环形缓冲：始终保留最近 USER_PCM_RING_MAX_FRAMES 帧，新帧总能写入
                        val startSample = _userAudioTimelineSamples
                        val endSample = startSample + (frame.size / REPLAY_BYTES_PER_SAMPLE)
                        _userAudioTimelineSamples = endSample
                        _userPcmRingFrames.addLast(
                            UserPcmFrame(
                                pcm = frame,
                                startSample = startSample,
                                endSample = endSample,
                            )
                        )
                        if (_userPcmRingFrames.size > USER_PCM_RING_MAX_FRAMES) {
                            _userPcmRingFrames.removeFirst()
                        }
                        tryFinalizeAllServerVadSegments(force = false)
                        syncServerVadFinalizeJob()
                    }
                }
                launch {
                    client.events.collect { event ->
                        if (_conversationSessionId != sessionId) return@collect

                        when (event) {
                            is StardustEvent.ResponseCreated ->
                                _awaitingResponse.value = true
                            is StardustEvent.ResponseDone -> {
                                _awaitingResponse.value = false
                                handleResponseDone()
                            }
                            is StardustEvent.ResponseAudioDelta -> {
                                _awaitingResponse.value = false
                                handleAssistantAudioDelta(event, sessionId)
                            }
                            is StardustEvent.ConversationItemCreated -> {
                                event.toTranscriptItemOrNull()?.let { item ->
                                    _transcripts.value =
                                        (_transcripts.value + item).takeLast(MAX_TRANSCRIPTS)
                                }
                                handleConversationItemCreated(event, sessionId)
                            }
                            is StardustEvent.InputAudioTranscriptionCompleted -> {
                                handleInputAudioTranscriptionCompleted(event, sessionId)
                            }
                            is StardustEvent.ResponseFunctionCallArgumentsDone -> {
                                handleFunctionCallDone(event, sessionId)
                            }
                            is StardustEvent.ResponseAudioDone -> {
                                handleAssistantAudioDone(event, sessionId)
                            }
                            is StardustEvent.ResponseAudioTranscriptDone -> {
                                handleAssistantAudioTranscriptDone(event, sessionId)
                            }
                            is StardustEvent.ResponseOutputItemDone -> {
                                handleResponseOutputItemDone(event, sessionId)
                            }
                            is StardustEvent.ResponseContentPartDone -> {
                                handleResponseContentPartDone(event, sessionId)
                            }
                            is StardustEvent.ResponseVideoDone -> {
                                if (_visionSessionActive && acceptVisionResults) {
                                    val parsed = VisionResultParser.parse(event)
                                    if (parsed != null) {
                                        _visionResults.value =
                                            (_visionResults.value + parsed).takeLast(MAX_VISION_RESULTS)
                                    }
                                }
                            }
                            is StardustEvent.Unknown -> {
                                handleUnknownEventForConvInfo(event, sessionId)
                            }
                            is StardustEvent.InputAudioBufferSpeechStarted -> {
                                handleSpeechStarted(event)
                            }
                            is StardustEvent.InputAudioBufferSpeechStopped -> {
                                handleSpeechStopped(event)
                            }
                            is StardustEvent.InputAudioBufferCommitted -> {
                                handleInputBufferCommitted(event, sessionId)
                            }
                            else -> Unit
                        }
                    }
                }
                launch {
                    client.errors.collect { error ->
                        val msg = "[${error.code.name}] ${error.message}"
                        _errorMessage.value = msg
                        delay(ERROR_DISPLAY_MS)
                        _errorMessage.compareAndSet(msg, null)
                    }
                }
                if (_visionSessionActive) {
                    launch {
                        client.video.state.collect { videoState ->
                            when (videoState) {
                                VideoState.Failed -> {
                                    showTransientError("[vision] Video connection lost")
                                    stopVisionPipelineFull()
                                }
                                VideoState.Disconnected -> {
                                    if (acceptVisionResults) {
                                        _visionStreaming.value = false
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * conversation.item.input_audio_transcription.completed 是语音输入的唯一可靠信号。
     * 服务端「音频分离投递」策略下，conversation.item.created 里的 content 可能是
     * input_text 类型（转写文本），导致 toConversationRecord 误生成 UserText。
     * 收到此事件后，将已有的 UserText 记录升级为 UserVoice，或新建 UserVoice 记录。
     */
    private fun handleInputAudioTranscriptionCompleted(
        event: StardustEvent.InputAudioTranscriptionCompleted,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            val transcript = (payload["transcript"] as? JsonPrimitive)?.contentOrNull ?: ""
            val existing = findRecordByItemId(itemId)
            if (existing != null) {
                updateConversationRecord(itemId) { prev ->
                    when (prev) {
                        is ConversationRecord.UserVoice ->
                            prev.copy(
                                text = transcript.ifBlank { prev.text },
                                hasAudio = true,
                                audioSegments = if (prev.audioSegments > 0) prev.audioSegments else 1,
                            )
                        is ConversationRecord.UserText ->
                            // 升级为 UserVoice：保留原 id/timestamp/sessionId
                            ConversationRecord.UserVoice(
                                id = prev.id,
                                timestamp = prev.timestamp,
                                sessionId = prev.sessionId,
                                text = transcript,
                                itemId = prev.itemId,
                                hasAudio = true,
                                audioSegments = 1,
                            )
                        else -> prev
                    }
                }
            } else {
                addConversationRecord(
                    ConversationRecord.UserVoice(
                        sessionId = sessionId,
                        text = transcript,
                        itemId = itemId,
                        hasAudio = true,
                        audioSegments = 1,
                    )
                )
            }
            val pcm = bindUserAudioItem(itemId, consumePending = true)
            if (pcm != null && _pttSessionActive) {
                _userPcmRingFrames.clear()
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleConversationItemCreated(
        event: StardustEvent.ConversationItemCreated,
        sessionId: Long,
    ) {
        try {
            val record = event.toConversationRecord(sessionId) ?: return
            val itemId = record.itemIdOrNull()
            // 若同 itemId 的占位记录已存在（例如 AudioDelta 先到），则更新文本而非新增
            if (itemId != null && findRecordByItemId(itemId) != null) {
                updateConversationRecord(itemId) { prev ->
                    when {
                        prev is ConversationRecord.AssistantVoice && record is ConversationRecord.AssistantVoice ->
                            prev.copy(text = record.text.ifBlank { prev.text })
                        prev is ConversationRecord.UserVoice && record is ConversationRecord.UserVoice ->
                            prev.copy(text = record.text.ifBlank { prev.text })
                        else -> prev
                    }
                }
            } else {
                val previousItemId = (event.payload["previous_item_id"] as? JsonPrimitive)?.contentOrNull
                addConversationRecord(record, previousItemId = previousItemId)
            }
            // 对于用户语音项（content 为 input_audio 时立即生成 UserVoice），
            // 也用此 item.id 关联 pending PCM。这样即使 transcription.completed 未到
            // 或 item_id 与本事件不一致，UI 行的 itemId 仍能命中重播缓存。
            // 不消费 pending：留给后续 transcription.completed 处理。
            if (record is ConversationRecord.UserVoice && itemId != null) {
                bindUserAudioItem(itemId, consumePending = false)
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleFunctionCallDone(
        event: StardustEvent.ResponseFunctionCallArgumentsDone,
        sessionId: Long,
    ) {
        try {
            val record = event.toFunctionCallRecord(sessionId)
            if (record is ConversationRecord.FunctionCall) {
                upsertFunctionCallRecord(record)
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleAssistantAudioDelta(
        event: StardustEvent.ResponseAudioDelta,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            // 已被截断的 item：丢弃迟到的 delta，避免污染已写入缓存的截断版本
            if (_truncatedAssistantItemIds.contains(itemId)) return
            _currentAssistantItemId = itemId
            // 累积助手 PCM 数据用于重播
            event.decodedPcm16?.let { pcm ->
                val needsFinalizeAgain = appendAssistantAudio(itemId, pcm)
                if (needsFinalizeAgain || itemId in _playableAudioItemIds.value) {
                    scheduleAssistantAudioFinalization(itemId)
                }
            }
            if (findRecordByItemId(itemId) != null) {
                updateConversationRecord(itemId) { existing ->
                    if (existing is ConversationRecord.AssistantVoice) {
                        existing.copy(
                            hasAudio = true,
                            audioSegments = existing.audioSegments + 1,
                        )
                    } else existing
                }
            } else {
                val record = event.toAssistantAudioDeltaRecord(sessionId)
                if (record != null) {
                    addConversationRecord(record)
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleAssistantAudioDone(
        event: StardustEvent.ResponseAudioDone,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            if (_truncatedAssistantItemIds.contains(itemId)) {
                // 已截断：truncateCurrentAssistantAudio() 已写入裁剪后的版本，不覆盖
                _assistantAudioFinalizeJobs.remove(itemId)?.cancel()
                _assistantAudioAccum.remove(itemId)
            } else {
                // 正常完成：稍等一个很短窗口，吸收可能晚于 done 被 collect 到的最后几个 delta。
                scheduleAssistantAudioFinalization(itemId)
            }
            updateConversationRecord(itemId) { existing ->
                if (existing is ConversationRecord.AssistantVoice) {
                    existing.copy(hasAudio = true)
                } else existing
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleSpeechStarted(event: StardustEvent.InputAudioBufferSpeechStarted) {
        // 按 item_id 写入 start_ms；不清空 ring（burst 下与 speech_stopped 同时到达，清空会丢 PCM）。
        val itemId = (event.payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
        val startMs = (event.payload["audio_start_ms"] as? JsonPrimitive)
            ?.content?.toLongOrNull() ?: -1L
        val segment = getOrCreateServerVadSegment(itemId)
        if (startMs >= 0L) {
            segment.startMs = startMs
        }
        _isCollectingUserSpeech = true
        tryFinalizeAllServerVadSegments(force = false)
        syncServerVadFinalizeJob()
    }

    private fun handleSpeechStopped(event: StardustEvent.InputAudioBufferSpeechStopped) {
        _isCollectingUserSpeech = false
        val itemId = (event.payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
        val audioEndMs = (event.payload["audio_end_ms"] as? JsonPrimitive)
            ?.content?.toLongOrNull() ?: -1L
        if (audioEndMs <= 0L) return
        val segment = getOrCreateServerVadSegment(itemId)
        segment.endMs = audioEndMs
        // speech_stopped 可能早于本地 ring 收到 audio_end_ms 对应尾帧；由采集帧与 catch-up 协程轮询时间轴追上后再 finalize。
        tryFinalizeAllServerVadSegments(force = false)
        syncServerVadFinalizeJob()
    }

    private fun handleInputBufferCommitted(
        event: StardustEvent.InputAudioBufferCommitted,
        @Suppress("UNUSED_PARAMETER") sessionId: Long,
    ) {
        try {
            _isCollectingUserSpeech = false
            val itemId = (event.payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            val pcm = bindUserAudioItem(itemId, consumePending = false) ?: return
            // 用 committed 的 item_id 缓存一次（快速绑定），但不消费 pending，也不清空 ring。
            // transcription.completed 是最终权威，会以其 item_id 覆盖缓存并清空 pending，
            // 从而确保 UI 行的 item_id 与重播缓存 key 一致。
            updateConversationRecord(itemId) { prev ->
                when (prev) {
                    is ConversationRecord.UserVoice ->
                        prev.copy(
                            hasAudio = true,
                            audioSegments = if (prev.audioSegments > 0) prev.audioSegments else 1,
                        )
                    is ConversationRecord.UserText ->
                        ConversationRecord.UserVoice(
                            id = prev.id,
                            timestamp = prev.timestamp,
                            sessionId = prev.sessionId,
                            text = prev.text,
                            itemId = prev.itemId,
                            hasAudio = true,
                            audioSegments = 1,
                        )
                    else -> prev
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleAssistantAudioTranscriptDone(
        event: StardustEvent.ResponseAudioTranscriptDone,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            val transcript = (payload["transcript"] as? JsonPrimitive)?.contentOrNull
            if (transcript.isNullOrBlank()) return
            if (findRecordByItemId(itemId) != null) {
                updateConversationRecord(itemId) { existing ->
                    if (existing is ConversationRecord.AssistantVoice) {
                        existing.copy(text = transcript)
                    } else existing
                }
            } else {
                addConversationRecord(
                    ConversationRecord.AssistantVoice(
                        sessionId = sessionId,
                        text = transcript,
                        itemId = itemId,
                        responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull,
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * response.output_item.done — item 完整结构。
     * 文本输出从 content[].transcript 取文案；function_call 输出从 item.name/arguments 生成函数记录。
     */
    private fun handleResponseOutputItemDone(
        event: StardustEvent.ResponseOutputItemDone,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val item = payload["item"] as? JsonObject ?: return
            val itemId = (item["item_id"] as? JsonPrimitive)?.contentOrNull
                ?: (item["id"] as? JsonPrimitive)?.contentOrNull
                ?: return
            val functionRecord = event.toFunctionCallRecord(sessionId)
            if (functionRecord != null) {
                upsertFunctionCallRecord(functionRecord)
                return
            }
            val roleStr = (item["role"] as? JsonPrimitive)?.contentOrNull ?: return
            if (roleStr.lowercase() !in listOf("assistant", "agent")) return
            val transcript = extractTranscriptFromContent(item)
            if (transcript.isNullOrBlank()) return
            if (findRecordByItemId(itemId) != null) {
                updateConversationRecord(itemId) { existing ->
                    if (existing is ConversationRecord.AssistantVoice && existing.text.isBlank()) {
                        existing.copy(text = transcript)
                    } else existing
                }
            } else {
                addConversationRecord(
                    ConversationRecord.AssistantVoice(
                        sessionId = sessionId,
                        text = transcript,
                        itemId = itemId,
                        responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull,
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * response.content_part.done — 单个 content part 完成，part.transcript 包含转写文字。
     */
    private fun handleResponseContentPartDone(
        event: StardustEvent.ResponseContentPartDone,
        sessionId: Long,
    ) {
        try {
            val payload = event.payload
            val itemId = (payload["item_id"] as? JsonPrimitive)?.contentOrNull ?: return
            val part = payload["part"] as? JsonObject ?: return
            val transcript = (part["transcript"] as? JsonPrimitive)?.contentOrNull
                ?: (part["text"] as? JsonPrimitive)?.contentOrNull
            if (transcript.isNullOrBlank()) return
            if (findRecordByItemId(itemId) != null) {
                updateConversationRecord(itemId) { existing ->
                    if (existing is ConversationRecord.AssistantVoice && existing.text.isBlank()) {
                        existing.copy(text = transcript)
                    } else existing
                }
            } else {
                addConversationRecord(
                    ConversationRecord.AssistantVoice(
                        sessionId = sessionId,
                        text = transcript,
                        itemId = itemId,
                        responseId = (payload["response_id"] as? JsonPrimitive)?.contentOrNull,
                    )
                )
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleUnknownEventForConvInfo(
        event: StardustEvent.Unknown,
        sessionId: Long,
    ) {
        try {
            val record = event.toFunctionCallRecordIfApplicable(sessionId, existingItemIds())
            if (record != null) {
                addConversationRecord(record)
            }
        } catch (_: Throwable) {
        }
    }

    private fun showTransientError(message: String) {
        _errorMessage.value = message
        viewModelScope.launch {
            delay(ERROR_DISPLAY_MS)
            _errorMessage.compareAndSet(message, null)
        }
    }

    private fun disconnect() {
        viewModelScope.launch { disconnectSuspend() }
    }

    private suspend fun disconnectSuspend() {
        _pttThresholdJob?.cancel()
        _pttThresholdJob = null
        _pttPressed.value = false
        _isCollectingUserSpeech = false
        _pendingUserAudioPcm = null
        _pendingServerVadSpeechSegments.clear()
        _pendingSpeechFinalizeJob?.cancel()
        _pendingSpeechFinalizeJob = null
        _serverVadMaxFinalizedEndSample = 0L
        _userPcmRingFrames.clear()
        _userAudioTimelineSamples = 0L
        _currentAssistantItemId = null
        _assistantAudioFinalizeJobs.values.forEach { it.cancel() }
        _assistantAudioFinalizeJobs.clear()
        _assistantAudioAccum.clear()
        _truncatedAssistantItemIds.clear()
        stopAudioReplay()
        stopVisionPipelineFull()
        observeJob?.cancel()
        observeJob = null
        val c = _client.value ?: return
        runCatching { c.audio.stopCapture(commit = false) }
        runCatching { c.close() }
        _client.value = null
        _audioLevel.value = 0f
        _transcripts.value = emptyList()
        _visionResults.value = emptyList()
        _awaitingResponse.value = false
    }

    override fun onCleared() {
        observeJob?.cancel()
        _pttThresholdJob?.cancel()
        _pttPressed.value = false
        _isCollectingUserSpeech = false
        _pendingUserAudioPcm = null
        _pendingServerVadSpeechSegments.clear()
        _pendingSpeechFinalizeJob?.cancel()
        _pendingSpeechFinalizeJob = null
        _serverVadMaxFinalizedEndSample = 0L
        _userPcmRingFrames.clear()
        _userAudioTimelineSamples = 0L
        _currentAssistantItemId = null
        _assistantAudioFinalizeJobs.values.forEach { it.cancel() }
        _assistantAudioFinalizeJobs.clear()
        _truncatedAssistantItemIds.clear()
        stopAudioReplay()
        _conversationRecords.value = emptyList()
        _assistantAudioAccum.clear()
        _audioPcm.clear()
        runBlocking {
            stopVisionPipelineFull()
        }
        val c = _client.value
        if (c != null) {
            runBlocking {
                runCatching { c.audio.stopCapture(commit = false) }
                runCatching { c.close() }
            }
        }
        super.onCleared()
    }

    /**
     * PTT 打断时，立即将当前助手音频条目的已累积 PCM 写入可重播缓存，并置 truncated 标志。
     * 应在向服务端发出 cancelResponse / conversation.item.truncate 的同时同步调用，
     * 以确保：
     *   1. 已播放的部分可被重播；
     *   2. 后续迟到的 delta（100~200ms 内可能到达）被 handleAssistantAudioDelta 丢弃；
     *   3. response.audio.done / response.done 不会覆盖已保存的截断版本。
     * 注：此处保存的是「发出打断时刻已累积的 PCM」，与实际播放时长近似，略有误差可接受。
     */
    private fun truncateCurrentAssistantAudio() {
        val itemId = _currentAssistantItemId ?: return
        if (_truncatedAssistantItemIds.contains(itemId)) return  // 幂等
        _truncatedAssistantItemIds.add(itemId)
        _assistantAudioFinalizeJobs.remove(itemId)?.cancel()
        // 取出已累积的 PCM（截断时刻的快照），写入可重播缓存（尚未写入时才写）
        val pcm = _assistantAudioAccum.remove(itemId)?.toByteArray()
        if (pcm != null && pcm.isNotEmpty() && !_playableAudioItemIds.value.contains(itemId)) {
            cacheAudio(itemId, pcm)
        }
    }

    /**
     * response.done 到达时，将 _assistantAudioAccum 中尚未写入可重播缓存的助手 PCM 补充写入。
     * 当 response 因打断（cancelResponse）而结束时，response.audio.done 可能不再到达，
     * 利用此回调确保已累积的 PCM 不会丢失。
     */
    private fun handleResponseDone() {
        try {
            val itemIds = _assistantAudioAccum.keys.toList()
            for (itemId in itemIds) {
                // 已截断的 item：truncateCurrentAssistantAudio() 已写入裁剪版本，不覆盖
                if (_truncatedAssistantItemIds.contains(itemId)) continue
                // 正常打断（非 PTT truncate）但 response.audio.done 未到达的情况：
                // 同样走 debounce，避免 response.done 先于最后的 audio.delta 被 collect。
                scheduleAssistantAudioFinalization(itemId)
            }
        } catch (_: Throwable) {
        }
    }

    private fun bindUserAudioItem(itemId: String, consumePending: Boolean): ByteArray? {
        if (_pttSessionActive) {
            val pcm = _pendingUserAudioPcm ?: ringFramesToPcmOrNull()
            if (pcm != null) {
                cacheAudio(itemId, pcm)
                if (consumePending) {
                    _pendingUserAudioPcm = null
                }
            }
            return pcm
        }

        if (_audioPcm[itemId] != null) {
            return _audioPcm[itemId]
        }
        getOrCreateServerVadSegment(itemId)
        tryFinalizeServerVadSegmentForItem(itemId, force = false)
        return _audioPcm[itemId]
    }

    private fun tryFinalizeSingleServerVadSegment(segment: PendingUserSpeechSegment, force: Boolean): Boolean {
        if (segment.startMs < 0L || segment.endMs <= segment.startMs) return false

        val endSample = msToSampleIndex(segment.endMs)
        if (!force && _userAudioTimelineSamples < endSample) return false

        val pcm = ringFramesForTimeRangeOrNull(segment.startMs, segment.endMs) ?: return false
        val itemId = segment.itemId
        cacheAudio(itemId, pcm)
        if (endSample > _serverVadMaxFinalizedEndSample) {
            _serverVadMaxFinalizedEndSample = endSample
        }
        _pendingServerVadSpeechSegments.remove(itemId)
        return true
    }

    private fun tryFinalizeAllServerVadSegments(force: Boolean): Boolean {
        var any = false
        for (segment in _pendingServerVadSpeechSegments.values.toList()) {
            if (tryFinalizeSingleServerVadSegment(segment, force)) any = true
        }
        if (any) {
            trimServerVadUserPcmRing()
        }
        return any
    }

    private fun tryFinalizeServerVadSegmentForItem(itemId: String, force: Boolean): Boolean {
        val seg = _pendingServerVadSpeechSegments[itemId] ?: return false
        if (!tryFinalizeSingleServerVadSegment(seg, force)) return false
        trimServerVadUserPcmRing()
        syncServerVadFinalizeJob()
        return true
    }

    /** 仍有分段在等 ring 时间轴追上 audio_end_ms。 */
    private fun serverVadAwaitingRingTimeline(): Boolean =
        _pendingServerVadSpeechSegments.values.any { seg ->
            seg.startMs >= 0L &&
                seg.endMs > seg.startMs &&
                _userAudioTimelineSamples < msToSampleIndex(seg.endMs)
        }

    /**
     * 当 ring 采样时间轴尚未覆盖 `audio_end_ms` 时，采集协程每帧仍会 [tryFinalizeAllServerVadSegments]；
     * 此处只启动**单次**补轮询：按约一帧间隔重试非 force finalize，时间轴追上即退出；
     * 超时后再 force 一次作为兜底。不在此路径内盲 sleep 固定 600ms 才开始尝试。
     */
    private fun syncServerVadFinalizeJob() {
        if (!serverVadAwaitingRingTimeline()) {
            _pendingSpeechFinalizeJob?.cancel()
            _pendingSpeechFinalizeJob = null
            return
        }
        if (_pendingSpeechFinalizeJob?.isActive == true) return
        _pendingSpeechFinalizeJob = viewModelScope.launch {
            try {
                val deadline = SystemClock.elapsedRealtime() + SERVER_VAD_RING_CATCHUP_MAX_WAIT_MS
                while (isActive && serverVadAwaitingRingTimeline()) {
                    tryFinalizeAllServerVadSegments(force = false)
                    if (!serverVadAwaitingRingTimeline()) break
                    if (SystemClock.elapsedRealtime() >= deadline) break
                    delay(SERVER_VAD_RING_CATCHUP_POLL_MS)
                }
                if (isActive && serverVadAwaitingRingTimeline()) {
                    tryFinalizeAllServerVadSegments(force = true)
                }
            } finally {
                _pendingSpeechFinalizeJob = null
                syncServerVadFinalizeJob()
            }
        }
    }

    /**
     * 安全裁剪用户 PCM ring：有未完成分段时只裁到最早已知 start 之前；
     * 若尚无任一未完成分段的 start_ms（如 stop 先于 start），则不裁。
     * 无非未完成分段时裁到已 finalize 的最大 endSample，释放旧缓冲。
     */
    private fun trimServerVadUserPcmRing() {
        val unfinalized = _pendingServerVadSpeechSegments.values
        val trimSample: Long? = when {
            unfinalized.isNotEmpty() -> {
                val knownStarts = unfinalized.mapNotNull { s ->
                    if (s.startMs >= 0L) msToSampleIndex(s.startMs) else null
                }
                if (knownStarts.isNotEmpty()) knownStarts.minOrNull() else null
            }
            _serverVadMaxFinalizedEndSample > 0L -> _serverVadMaxFinalizedEndSample
            else -> null
        }
        if (trimSample != null && trimSample > 0L) {
            dropRingFramesBeforeSample(trimSample)
        }
    }

    private fun getOrCreateServerVadSegment(itemId: String): PendingUserSpeechSegment =
        _pendingServerVadSpeechSegments.getOrPut(itemId) {
            PendingUserSpeechSegment(itemId = itemId)
        }

    /** 将环形缓冲中的所有帧合并为一个 ByteArray；为空时返回 null。 */
    private fun ringFramesToPcmOrNull(): ByteArray? {
        if (_userPcmRingFrames.isEmpty()) return null
        val out = ByteArrayOutputStream(_userPcmRingFrames.sumOf { it.pcm.size })
        _userPcmRingFrames.forEach { out.write(it.pcm) }
        return out.toByteArray()
    }

    /**
     * 根据服务端给出的输入音频时间戳取出 ring 中有交集的整块 PCM。
     * audio_start_ms / audio_end_ms 是从 input_audio_buffer 开始计算的绝对时间。
     * 重播可接受前后多半块音频，避免在 PCM 帧内部做不必要的 byte 级裁切。
     */
    private fun ringFramesForTimeRangeOrNull(startMs: Long, endMs: Long): ByteArray? {
        if (_userPcmRingFrames.isEmpty() || endMs <= startMs) return null
        val targetStartSample = msToSampleIndex(startMs)
        val targetEndSample = msToSampleIndex(endMs)
        if (targetEndSample <= targetStartSample) return null

        val out = ByteArrayOutputStream()
        for (frame in _userPcmRingFrames) {
            if (frame.endSample <= targetStartSample || frame.startSample >= targetEndSample) continue
            out.write(frame.pcm)
        }
        return out.toByteArray().takeIf { it.isNotEmpty() }
    }

    private fun dropRingFramesBeforeSample(sample: Long) {
        while (_userPcmRingFrames.isNotEmpty()) {
            val first = _userPcmRingFrames.first()
            when {
                first.endSample <= sample -> _userPcmRingFrames.removeFirst()
                first.startSample < sample -> {
                    _userPcmRingFrames.removeFirst()
                    val byteStart = ((sample - first.startSample) * REPLAY_BYTES_PER_SAMPLE).toInt()
                    val remaining = first.pcm.copyOfRange(byteStart, first.pcm.size)
                    if (remaining.isNotEmpty()) {
                        _userPcmRingFrames.addFirst(
                            first.copy(
                                pcm = remaining,
                                startSample = sample,
                            )
                        )
                    }
                    return
                }
                else -> return
            }
        }
    }

    private fun msToSampleIndex(ms: Long): Long =
        (ms * REPLAY_SAMPLE_RATE) / 1_000L
}

/** 从 item.content 数组中提取 transcript 或 text，返回第一个非空值。 */
private fun extractTranscriptFromContent(item: JsonObject): String? {
    val content = item["content"] as? JsonArray ?: return null
    for (part in content) {
        val obj = part as? JsonObject ?: continue
        val t = (obj["transcript"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["text"] as? JsonPrimitive)?.contentOrNull
        if (!t.isNullOrBlank()) return t
    }
    return null
}

private fun ConversationRecord.FunctionCall.matchesFunctionCall(
    other: ConversationRecord.FunctionCall,
): Boolean =
    (itemId != null && itemId == other.itemId) ||
        (callId != null && callId == other.callId)

private fun ConversationRecord.FunctionCall.mergeFunctionCall(
    other: ConversationRecord.FunctionCall,
): ConversationRecord.FunctionCall =
    copy(
        name = other.name.takeUnless { it == "unknown" } ?: name,
        arguments = other.arguments.ifBlank { arguments },
        callId = other.callId ?: callId,
        itemId = other.itemId ?: itemId,
        responseId = other.responseId ?: responseId,
    )

private fun displayAgentName(s: AppSettings): String = when (s.sessionConfigMode) {
    SessionConfigMode.AgentId -> s.agentId.ifBlank { "Assistant" }
    SessionConfigMode.Advanced -> {
        val a = s.advancedSession
        val label = "${a.modelProvider}/${a.modelName}".trim()
        label.ifBlank { "Assistant" }
    }
}

private data class VoicePhaseInputs(
    val sdkState: StardustState,
    val captureState: CaptureAudioState,
    val playbackState: PlaybackAudioState,
    val awaitingResponse: Boolean,
    val pttPressed: Boolean,
)

private fun mapToVoicePhase(
    sdkState: StardustState,
    captureState: CaptureAudioState,
    playbackState: PlaybackAudioState,
    awaitingResponse: Boolean,
    connectionWiring: Boolean,
): VoicePhase = when {
    connectionWiring && sdkState == StardustState.Idle -> VoicePhase.Connecting
    else -> mapSdkToVoicePhase(sdkState, captureState, playbackState, awaitingResponse)
}
