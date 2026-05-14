package cn.ticos.stardust.sdk.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Base64
import cn.ticos.stardust.sdk.CaptureAudioState
import cn.ticos.stardust.sdk.PlaybackAudioState
import cn.ticos.stardust.sdk.StardustAudio
import cn.ticos.stardust.sdk.StardustErrorCode
import cn.ticos.stardust.sdk.StardustSdkError
import cn.ticos.stardust.sdk.StardustSdkException
import cn.ticos.stardust.sdk.internal.StardustJson
import cn.ticos.stardust.sdk.internal.StardustLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class DefaultStardustAudio(
    private val scope: CoroutineScope,
    private val sendJson: suspend (String) -> Unit,
    private val emitError: suspend (StardustSdkError) -> Unit,
    private val logger: StardustLogger,
    autoPlay: Boolean,
) : StardustAudio {
    private val _captureState = MutableStateFlow(CaptureAudioState.Idle)
    override val captureState: StateFlow<CaptureAudioState> = _captureState.asStateFlow()

    private val _playbackState = MutableStateFlow(PlaybackAudioState.Idle)
    override val playbackState: StateFlow<PlaybackAudioState> = _playbackState.asStateFlow()

    private val _capturedPcm = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val capturedPcm: SharedFlow<ByteArray> = _capturedPcm.asSharedFlow()

    private val _captureLevel = MutableStateFlow(0f)
    override val captureLevel: StateFlow<Float> = _captureLevel.asStateFlow()

    private val playbackEnabled = AtomicBoolean(autoPlay)

    /**
     * True while [startCapture] is active. Drives the mic loop only — never tied to local speaker state.
     */
    private val captureActive = AtomicBoolean(false)

    /**
     * True from first queued assistant PCM until [onServerResponseAudioDone] / [onServerResponseDone],
     * so UI stays "Speaking" across inter-chunk gaps while mic remains in [CaptureAudioState.Recording].
     */
    private val assistantPlayoutStreamOpen = AtomicBoolean(false)

    /** Chunks received from [playbackQueue] but not yet fully consumed (written or dropped). */
    private val pendingPlaybackChunks = AtomicInteger(0)

    private val playbackQueue = Channel<ByteArray>(capacity = Channel.UNLIMITED)
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null

    init {
        playbackJob = scope.launch(Dispatchers.IO) {
            for (chunk in playbackQueue) {
                if (!playbackEnabled.get()) {
                    pendingPlaybackChunks.decrementAndGet()
                    applyPlaybackSignals()
                    continue
                }
                val track = ensureAudioTrack()
                try {
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                        track.play()
                    }
                    track.write(chunk, 0, chunk.size)
                } catch (t: Throwable) {
                    _playbackState.value = PlaybackAudioState.Failed
                    emitError(
                        StardustSdkError(
                            code = StardustErrorCode.AUDIO_PLAYBACK_FAILED,
                            message = "Audio playback failed",
                            cause = t,
                            recoverable = true,
                        ),
                    )
                } finally {
                    // coerceAtLeast: stopPlayback(clearQueue=true) may have already reset the counter to 0
                    var prev: Int
                    do {
                        prev = pendingPlaybackChunks.get()
                    } while (!pendingPlaybackChunks.compareAndSet(prev, (prev - 1).coerceAtLeast(0)))
                    // Safety: when the queue drains to zero, reset the stream-open flag so the state
                    // can exit Playing even if response.audio.done / response.done never arrives
                    // (e.g. due to concurrent coroutine scheduling where the last ResponseAudioDelta
                    // coroutine executes after the ResponseAudioDone coroutine has already cleared the flag).
                    if (pendingPlaybackChunks.get() == 0) {
                        assistantPlayoutStreamOpen.set(false)
                    }
                    if (_playbackState.value != PlaybackAudioState.Failed) {
                        applyPlaybackSignals()
                    }
                }
            }
        }
    }

    override suspend fun startCapture() {
        if (captureActive.get()) return
        val sampleRate = 24_000
        val channelMask = AudioFormat.CHANNEL_IN_MONO
        val encoding = AudioFormat.ENCODING_PCM_16BIT
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBuffer <= 0) {
            val error = StardustSdkError(
                code = StardustErrorCode.AUDIO_RECORD_UNSUPPORTED_FORMAT,
                message = "24kHz mono PCM16 capture unsupported on this device",
                recoverable = false,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            sampleRate,
            channelMask,
            encoding,
            minBuffer * 2,
        )
        val localRecorder = recorder ?: return
        if (localRecorder.state != AudioRecord.STATE_INITIALIZED) {
            val error = StardustSdkError(
                code = StardustErrorCode.AUDIO_RECORD_FAILED,
                message = "AudioRecord init failed",
                recoverable = true,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
        localRecorder.startRecording()
        captureActive.set(true)
        _captureState.value = CaptureAudioState.Recording
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(1920)
            while (isActive && captureActive.get()) {
                val read = localRecorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val frame = buffer.copyOf(read)
                    _captureLevel.value = computeRmsLevel(frame)
                    _capturedPcm.tryEmit(frame)
                    appendPcm(frame)
                }
            }
        }
    }

    override suspend fun stopCapture(commit: Boolean) {
        captureActive.set(false)
        _captureState.value = CaptureAudioState.Stopping
        captureJob?.cancel()
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        if (commit) {
            this.commit()
        }
        _captureState.value = CaptureAudioState.Idle
        _captureLevel.value = 0f
    }

    override suspend fun appendPcm(pcm16: ByteArray) {
        val payload = buildJsonObject {
            put("type", "input_audio_buffer.append")
            put("audio", Base64.encodeToString(pcm16, Base64.NO_WRAP))
        }
        sendJson(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun commit() {
        sendJson("""{"type":"input_audio_buffer.commit"}""")
    }

    override suspend fun clear() {
        sendJson("""{"type":"input_audio_buffer.clear"}""")
    }

    override suspend fun stopPlayback(clearQueue: Boolean) {
        assistantPlayoutStreamOpen.set(false)
        audioTrack?.runCatching {
            pause()
            flush()
            stop()
        }
        if (clearQueue) {
            while (true) {
                val result = playbackQueue.tryReceive()
                if (result.isFailure) break
            }
            // Reset to 0: any in-flight chunk that was already dequeued will find pendingPlaybackChunks <= 0
            // in its finally block and will not flip state back to Playing.
            pendingPlaybackChunks.set(0)
        }
        _playbackState.value = PlaybackAudioState.Idle
    }

    override fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled.set(enabled)
    }

    override fun onServerResponseAudioDone() {
        assistantPlayoutStreamOpen.set(false)
        applyPlaybackSignals()
    }

    override fun onServerResponseDone() {
        assistantPlayoutStreamOpen.set(false)
        applyPlaybackSignals()
    }

    suspend fun enqueueDeltaPcm(pcm16: ByteArray?) {
        if (pcm16 == null || pcm16.isEmpty()) return
        assistantPlayoutStreamOpen.set(true)
        pendingPlaybackChunks.incrementAndGet()
        playbackQueue.send(pcm16)
        applyPlaybackSignals()
    }

    suspend fun release() {
        stopCapture(commit = false)
        stopPlayback(clearQueue = true)
        playbackJob?.cancel()
        playbackQueue.close()
        audioTrack?.release()
        audioTrack = null
    }

    private fun applyPlaybackSignals() {
        if (_playbackState.value == PlaybackAudioState.Failed) {
            return
        }
        val playout = assistantPlayoutStreamOpen.get() || pendingPlaybackChunks.get() > 0
        _playbackState.value = if (playout) {
            PlaybackAudioState.Playing
        } else {
            PlaybackAudioState.Idle
        }
    }

    private fun ensureAudioTrack(): AudioTrack {
        audioTrack?.let { return it }
        val minBuffer = AudioTrack.getMinBufferSize(
            24_000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val sessionId = recorder?.audioSessionId?.takeIf { it > 0 }
            ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(24_000)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuffer * 2,
            AudioTrack.MODE_STREAM,
            sessionId,
        )
        audioTrack = track
        logger.d("AudioTrack initialized sessionId=$sessionId")
        return track
    }
}
