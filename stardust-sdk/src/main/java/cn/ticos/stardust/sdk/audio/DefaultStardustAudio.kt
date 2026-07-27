package cn.ticos.stardust.sdk.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
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
import java.util.concurrent.atomic.AtomicLong

private sealed interface PlaybackIngress {
    val generation: Long

    data class Pcm(
        val bytes: ByteArray,
        override val generation: Long,
    ) : PlaybackIngress

    data class SegmentEnd(
        override val generation: Long,
    ) : PlaybackIngress
}

private data class QueuedPlayoutChunk(
    val bytes: ByteArray,
    val startsPlayout: Boolean,
    val generation: Long,
)

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
    private val pendingPlaybackBytes = AtomicLong(0)
    private val playbackGeneration = AtomicLong(0)
    private val playoutStateLock = Any()

    private val pcmPlayoutBuffer = PcmPlayoutBuffer()

    /** Serializes delta append + initial prebuffering so WebSocket message order is preserved. */
    private val playbackIngress = Channel<PlaybackIngress>(capacity = Channel.UNLIMITED)

    private val playbackQueue = Channel<QueuedPlayoutChunk>(capacity = Channel.UNLIMITED)
    private var playbackIngressJob: Job? = null
    private var playbackJob: Job? = null
    @Volatile
    private var audioTrack: AudioTrack? = null
    @Volatile
    private var audioTrackCapacityFrames: Int = 0
    @Volatile
    private var minPlaybackBufferBytes: Int = 0
    @Volatile
    private var lastObservedUnderruns: Int = 0
    private val lastDeltaArrivalNs = AtomicLong(0)
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null

    init {
        playbackIngressJob = scope.launch(Dispatchers.IO) {
            for (item in playbackIngress) {
                when (item) {
                    is PlaybackIngress.Pcm -> {
                        if (item.bytes.isEmpty()) continue
                        val deviceMinimum = queryMinPlaybackBufferBytes().coerceAtLeast(0)
                        val output = synchronized(playoutStateLock) {
                            if (item.generation != playbackGeneration.get()) {
                                null
                            } else {
                                assistantPlayoutStreamOpen.set(true)
                                pcmPlayoutBuffer.ensureStartThresholdAtLeast(deviceMinimum)
                                pcmPlayoutBuffer.append(item.bytes)
                            }
                        }
                        output?.let { chunk ->
                            enqueuePlaybackChunk(chunk, item.generation)
                        }
                        applyPlaybackSignals()
                    }
                    is PlaybackIngress.SegmentEnd -> {
                        val output = synchronized(playoutStateLock) {
                            if (item.generation != playbackGeneration.get()) {
                                null
                            } else {
                                lastDeltaArrivalNs.set(0)
                                assistantPlayoutStreamOpen.set(false)
                                pcmPlayoutBuffer.endSegment()
                            }
                        }
                        output?.let { chunk ->
                            enqueuePlaybackChunk(chunk, item.generation)
                        }
                        applyPlaybackSignals()
                    }
                }
            }
        }
        playbackJob = scope.launch(Dispatchers.IO) {
            for (chunk in playbackQueue) {
                try {
                    if (chunk.generation != playbackGeneration.get() || !playbackEnabled.get()) {
                        continue
                    }
                    val track = ensureAudioTrack()
                    val startedNow = track.playState != AudioTrack.PLAYSTATE_PLAYING
                    val writeStartedNs = SystemClock.elapsedRealtimeNanos()
                    val written = if (startedNow) {
                        writeStartingChunk(track, chunk.bytes, chunk.generation)
                    } else {
                        writePcmToTrack(track, chunk.bytes, chunk.generation)
                    }
                    val writeElapsedMs =
                        (SystemClock.elapsedRealtimeNanos() - writeStartedNs) / 1_000_000
                    val underrunsAfter = track.underrunCount
                    if (written != chunk.bytes.size &&
                        chunk.generation == playbackGeneration.get()
                    ) {
                        error("AudioTrack short write ${written}/${chunk.bytes.size}")
                    }
                    if (chunk.startsPlayout || startedNow) {
                        lastObservedUnderruns = underrunsAfter
                    } else if (underrunsAfter > lastObservedUnderruns) {
                        val newTargetBytes = pcmPlayoutBuffer.increaseStartThreshold()
                        logger.w(
                            "AudioTrack underrun +${underrunsAfter - lastObservedUnderruns}; " +
                                "nextStartBufferMs=${bytesToMillis(newTargetBytes.toLong())}",
                        )
                        lastObservedUnderruns = underrunsAfter
                    }
                    logger.d(
                        "AudioTrack write bytes=$written durationMs=$writeElapsedMs " +
                            "appPendingMs=${pendingAudioMillis()} underruns=$underrunsAfter " +
                            "bufferFrames=${track.bufferSizeInFrames} " +
                            "headFrames=${track.playbackHeadPosition.toLong() and 0xffffffffL}",
                    )
                } catch (t: Throwable) {
                    if (chunk.generation != playbackGeneration.get()) continue
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
                    decrementPendingChunk(chunk.bytes.size)
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
        synchronized(playoutStateLock) {
            playbackGeneration.incrementAndGet()
            assistantPlayoutStreamOpen.set(false)
            pcmPlayoutBuffer.clear()
        }
        while (playbackIngress.tryReceive().isSuccess) {
            // drain pending deltas not yet handled by the playout actor
        }
        audioTrack?.runCatching {
            pause()
            flush()
            stop()
        }
        if (clearQueue) {
            while (true) {
                val result = playbackQueue.tryReceive()
                if (result.isFailure) break
                result.getOrNull()?.let { queued ->
                    decrementPendingChunk(queued.bytes.size)
                }
            }
        }
        lastDeltaArrivalNs.set(0)
        _playbackState.value = PlaybackAudioState.Idle
    }

    override fun setPlaybackEnabled(enabled: Boolean) {
        playbackEnabled.set(enabled)
    }

    override fun onServerResponseAudioDone() {
        signalSegmentPlaybackEnd()
    }

    override fun onServerResponseDone() {
        signalSegmentPlaybackEnd()
    }

    fun onResponseCreated() = Unit

    suspend fun enqueueDeltaPcm(pcm16: ByteArray?) {
        if (pcm16 == null || pcm16.isEmpty()) return
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val previousNs = lastDeltaArrivalNs.getAndSet(nowNs)
        val gapMs = if (previousNs == 0L) 0L else (nowNs - previousNs) / 1_000_000
        logger.d(
            "Audio delta bytes=${pcm16.size} arrivalGapMs=$gapMs " +
                "appPendingMs=${pendingAudioMillis()}",
        )
        playbackIngress.send(
            PlaybackIngress.Pcm(
                bytes = pcm16,
                generation = playbackGeneration.get(),
            ),
        )
    }

    private fun signalSegmentPlaybackEnd() {
        playbackIngress.trySend(
            PlaybackIngress.SegmentEnd(generation = playbackGeneration.get()),
        )
    }

    private suspend fun enqueuePlaybackChunk(chunk: PlayoutChunk, generation: Long) {
        pendingPlaybackChunks.incrementAndGet()
        pendingPlaybackBytes.addAndGet(chunk.bytes.size.toLong())
        playbackQueue.send(
            QueuedPlayoutChunk(
                bytes = chunk.bytes,
                startsPlayout = chunk.startsPlayout,
                generation = generation,
            ),
        )
        applyPlaybackSignals()
    }

    suspend fun release() {
        stopCapture(commit = false)
        stopPlayback(clearQueue = true)
        pcmPlayoutBuffer.clear()
        playbackIngress.close()
        playbackIngressJob?.cancel()
        playbackJob?.cancel()
        playbackQueue.close()
        audioTrack?.release()
        audioTrack = null
    }

    /**
     * Prewrites the initial watermark before play(), then expands the effective AudioTrack
     * buffer for steady-state writes. This avoids starting an empty streaming track.
     */
    private fun writeStartingChunk(
        track: AudioTrack,
        pcm: ByteArray,
        generation: Long,
    ): Int {
        track.runCatching { flush() }
        val targetBytes = minOf(
            pcm.size,
            pcmPlayoutBuffer.currentStartThresholdBytes(),
        ).frameAligned()
        if (targetBytes <= 0) return 0

        val targetFrames = (targetBytes / PCM_BYTES_PER_FRAME).coerceAtLeast(1)
        val effectiveFrames = track.setBufferSizeInFrames(targetFrames)
            .takeIf { it > 0 }
            ?: track.bufferSizeInFrames
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            track.setStartThresholdInFrames(targetFrames.coerceAtMost(effectiveFrames))
        }

        var written = writePcmRange(
            track = track,
            pcm = pcm,
            initialOffset = 0,
            endExclusive = targetBytes,
            generation = generation,
        )
        if (written != targetBytes || generation != playbackGeneration.get()) return written

        // Before API 31 the streaming start threshold cannot be set independently. A very short
        // final response may therefore need silent tail padding to reach the device's effective
        // start buffer. Normal streams already prebuffer at least getMinBufferSize().
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            pcm.size < pcmPlayoutBuffer.currentStartThresholdBytes() &&
            effectiveFrames > targetFrames
        ) {
            val padding = ByteArray((effectiveFrames - targetFrames) * PCM_BYTES_PER_FRAME)
            writePcmToTrack(track, padding, generation)
            if (generation != playbackGeneration.get()) return written
        }

        track.play()
        val capacityFrames = audioTrackCapacityFrames
        if (capacityFrames > 0) {
            track.setBufferSizeInFrames(capacityFrames)
        }

        if (targetBytes < pcm.size) {
            written += writePcmRange(
                track = track,
                pcm = pcm,
                initialOffset = targetBytes,
                endExclusive = pcm.size,
                generation = generation,
            )
        }
        return written
    }

    private fun writePcmToTrack(
        track: AudioTrack,
        pcm: ByteArray,
        generation: Long,
    ): Int = writePcmRange(track, pcm, 0, pcm.size, generation)

    private fun writePcmRange(
        track: AudioTrack,
        pcm: ByteArray,
        initialOffset: Int,
        endExclusive: Int,
        generation: Long,
    ): Int {
        var offset = initialOffset
        while (offset < endExclusive && generation == playbackGeneration.get()) {
            val written = track.write(pcm, offset, endExclusive - offset)
            if (written <= 0) {
                if (generation != playbackGeneration.get()) break
                error("AudioTrack.write failed with code=$written")
            }
            offset += written
        }
        return offset - initialOffset
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
        val minBuffer = queryMinPlaybackBufferBytes()
        check(minBuffer > 0) {
            "24kHz mono PCM16 playback unsupported: minBuffer=$minBuffer"
        }
        val sessionId = recorder?.audioSessionId?.takeIf { it > 0 }
            ?: AudioManager.AUDIO_SESSION_ID_GENERATE
        val streamBufSize = maxOf(minBuffer, PLAYBACK_MAX_BUFFER_BYTES).frameAlignedUp()
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
            streamBufSize,
            AudioTrack.MODE_STREAM,
            sessionId,
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            error("AudioTrack initialization failed")
        }
        audioTrackCapacityFrames = track.bufferCapacityInFrames
        audioTrack = track
        logger.d(
            "AudioTrack initialized sessionId=$sessionId minBufferBytes=$minBuffer " +
                "capacityFrames=${track.bufferCapacityInFrames}",
        )
        return track
    }

    private fun queryMinPlaybackBufferBytes(): Int {
        minPlaybackBufferBytes.takeIf { it != 0 }?.let { return it }
        val queried = AudioTrack.getMinBufferSize(
            PCM_SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (queried <= 0) return queried
        return queried.frameAlignedUp().also { minPlaybackBufferBytes = it }
    }

    private fun decrementPendingChunk(bytes: Int) {
        var previousChunks: Int
        do {
            previousChunks = pendingPlaybackChunks.get()
        } while (!pendingPlaybackChunks.compareAndSet(
                previousChunks,
                (previousChunks - 1).coerceAtLeast(0),
            )
        )

        var previousBytes: Long
        do {
            previousBytes = pendingPlaybackBytes.get()
        } while (!pendingPlaybackBytes.compareAndSet(
                previousBytes,
                (previousBytes - bytes).coerceAtLeast(0),
            )
        )
    }

    private fun pendingAudioMillis(): Long {
        val bytes = pendingPlaybackBytes.get() + pcmPlayoutBuffer.pendingBytes()
        return bytesToMillis(bytes)
    }

    private fun bytesToMillis(bytes: Long): Long =
        bytes * 1_000L / (PCM_SAMPLE_RATE_HZ * PCM_BYTES_PER_FRAME)

    private fun Int.frameAligned(): Int = this - (this % PCM_BYTES_PER_FRAME)

    private fun Int.frameAlignedUp(): Int {
        val remainder = this % PCM_BYTES_PER_FRAME
        return if (remainder == 0) this else this + (PCM_BYTES_PER_FRAME - remainder)
    }
}
