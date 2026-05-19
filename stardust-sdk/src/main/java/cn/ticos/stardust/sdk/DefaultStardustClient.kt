package cn.ticos.stardust.sdk

import cn.ticos.stardust.sdk.audio.DefaultStardustAudio
import cn.ticos.stardust.sdk.internal.EventParser
import cn.ticos.stardust.sdk.internal.ProtocolEncoder
import cn.ticos.stardust.sdk.internal.StardustJson
import cn.ticos.stardust.sdk.internal.StardustLogger
import cn.ticos.stardust.sdk.internal.buildConversationItemPayload
import cn.ticos.stardust.sdk.model.ResponseConfig
import cn.ticos.stardust.sdk.model.SessionConfig
import cn.ticos.stardust.sdk.video.DefaultStardustVideo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class DefaultStardustClient(
    private val config: StardustConfig,
) : StardustClient {
    private val logger = StardustLogger(config.logLevel)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val wsSendQueue = Channel<String>(Channel.UNLIMITED)
    /** Preserves WebSocket receive order (critical for audio delta before audio.done). */
    private val wsReceiveQueue = Channel<String>(Channel.UNLIMITED)
    private val isClosed = AtomicBoolean(false)

    private val _state = MutableStateFlow(StardustState.Idle)
    override val state: StateFlow<StardustState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StardustEvent>(extraBufferCapacity = 128)
    override val events: SharedFlow<StardustEvent> = _events.asSharedFlow()

    private val _errors = MutableSharedFlow<StardustSdkError>(extraBufferCapacity = 64)
    override val errors: SharedFlow<StardustSdkError> = _errors.asSharedFlow()

    override val diagnostics: StardustDiagnostics = StardustDiagnostics()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.writeTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // CLEARTEXT required for ws://; TLS specs used first for wss:// / https.
        .connectionSpecs(
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT),
        )
        .build()

    @Volatile
    private var realtimeWs: WebSocket? = null
    private var connectDeferred: CompletableDeferred<Unit>? = null
    private var lastSessionConfig: SessionConfig? = null
    private var reconnecting = AtomicBoolean(false)

    /**
     * 调用 [cancelResponse] 后设为 true，抑制后续到达的 ResponseAudioDelta 被入队播放。
     * 直到 ResponseAudioDone / ResponseDone 到达后重置，避免 PTT 打断时因网络延迟残留的
     * 音频片段重新触发播放（即使服务端忽略了 response.cancel 也能保证本地静默）。
     */
    @Volatile
    private var suppressAudioDelta = false

    private val internalAudio = DefaultStardustAudio(
        scope = scope,
        sendJson = { json -> enqueueSend(json) },
        emitError = { emitError(it) },
        logger = logger,
        autoPlay = config.autoPlayAudio,
    )

    private val internalVideo = DefaultStardustVideo(
        connectWs = { url, headers, listener ->
            val request = Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            httpClient.newWebSocket(request, listener)
        },
        realtimeStateProvider = { _state.value },
        configVideoUrl = config.videoUrl,
        authTokenProvider = { resolveToken() },
        queryParamsProvider = { buildQueryParams() },
        connectPolicy = config.videoConnectPolicy,
        emitError = { emitError(it) },
        onConnectedChanged = { diagnostics.updateVideoConnected(it) },
        logger = logger,
    ).also { video ->
        video.maxFps = config.videoMaxFps.coerceIn(1, 30)
    }

    override val audio: StardustAudio = internalAudio
    override val video: StardustVideo = internalVideo

    init {
        scope.launch {
            for (payload in wsSendQueue) {
                val sent = realtimeWs?.send(payload) ?: false
                if (!sent) {
                    emitError(
                        StardustSdkError(
                            code = StardustErrorCode.WEBSOCKET_CLOSED,
                            message = "Realtime websocket is not available",
                            recoverable = true,
                        ),
                    )
                } else {
                    diagnostics.onEventSent()
                    logger.d("send=${logger.redactJson(payload)}")
                }
            }
        }
        scope.launch {
            for (text in wsReceiveQueue) {
                dispatchRealtimeMessage(text)
            }
        }
    }

    override suspend fun connect() {
        ensureNotClosed()
        if (_state.value == StardustState.Connected ||
            _state.value == StardustState.SessionCreated ||
            _state.value == StardustState.SessionUpdated
        ) {
            return
        }
        _state.value = StardustState.Connecting
        connectDeferred = CompletableDeferred()
        val request = buildRealtimeRequest()
        realtimeWs = httpClient.newWebSocket(request, realtimeListener())
        connectDeferred?.await()
    }

    override suspend fun updateSession(session: SessionConfig) {
        ensureNotClosed()
        waitUntilConnected()
        lastSessionConfig = session
        val payload = ProtocolEncoder.sessionUpdatePayload(session)
        enqueueSend(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun sendText(text: String, userId: String, previousItemId: String?) {
        ensureNotClosed()
        waitUntilConnected()
        val payload = buildConversationItemPayload(
            userId = userId,
            previousItemId = previousItemId,
            text = text,
            imageUrls = emptyList(),
        )
        enqueueSend(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun sendImage(imageUrl: String, prompt: String?, userId: String, previousItemId: String?) {
        ensureNotClosed()
        waitUntilConnected()
        val payload = buildConversationItemPayload(
            userId = userId,
            previousItemId = previousItemId,
            text = prompt,
            imageUrls = listOf(imageUrl),
        )
        enqueueSend(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun sendMultimodalMessage(text: String?, imageUrls: List<String>, userId: String, previousItemId: String?) {
        ensureNotClosed()
        waitUntilConnected()
        val payload = buildConversationItemPayload(
            userId = userId,
            previousItemId = previousItemId,
            text = text,
            imageUrls = imageUrls,
        )
        enqueueSend(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun createResponse(response: ResponseConfig) {
        ensureNotClosed()
        waitUntilConnected()
        val payload = ProtocolEncoder.responseCreatePayload(response)
        enqueueSend(StardustJson.encodeToString(JsonObject.serializer(), payload))
    }

    override suspend fun cancelResponse(userId: String) {
        ensureNotClosed()
        // 先抑制后续 delta 入队，再清空本地队列，防止竞态条件导致残留音频继续播放。
        // 服务端在 Client VAD 模式下可能忽略 response.cancel（参见 API 文档），此标志
        // 保证即使服务端仍继续下发音频，客户端也不会播放。
        suppressAudioDelta = true
        internalAudio.stopPlayback(clearQueue = true)
        enqueueSend("""{"type":"response.cancel","user_id":"$userId"}""")
    }

    override suspend fun sendRawEvent(json: String) {
        ensureNotClosed()
        waitUntilConnected()
        enqueueSend(json)
    }

    override suspend fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        _state.value = StardustState.Closing
        internalVideo.disconnect()
        internalAudio.release()
        realtimeWs?.close(1000, "client close")
        realtimeWs = null
        wsSendQueue.close()
        wsReceiveQueue.close()
        scope.coroutineContext.cancel()
        _state.value = StardustState.Closed
        diagnostics.updateRealtimeConnected(false)
    }

    private suspend fun enqueueSend(json: String) {
        wsSendQueue.send(json)
    }

    private fun realtimeListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                diagnostics.updateRealtimeConnected(true)
                _state.value = StardustState.Connected
                connectDeferred?.complete(Unit)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                wsReceiveQueue.trySend(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                diagnostics.updateRealtimeConnected(false)
                if (!isClosed.get()) {
                    scope.launch {
                        _state.value = StardustState.Closed
                        handleReconnect(
                            StardustSdkError(
                                code = StardustErrorCode.WEBSOCKET_CLOSED,
                                message = "Realtime closed: $code/$reason",
                                recoverable = true,
                            ),
                        )
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch {
                    diagnostics.updateRealtimeConnected(false)
                    if (response?.code == 401) {
                        connectDeferred?.completeExceptionally(t)
                        _state.value = StardustState.Failed
                        emitError(
                            StardustSdkError(
                                code = StardustErrorCode.AUTH_FAILED,
                                message = "Realtime auth failed",
                                cause = t,
                                recoverable = false,
                            ),
                        )
                        return@launch
                    }
                    connectDeferred?.completeExceptionally(t)
                    _state.value = StardustState.Failed
                    handleReconnect(
                        StardustSdkError(
                            code = StardustErrorCode.WEBSOCKET_CONNECT_FAILED,
                            message = "Realtime connection failed",
                            cause = t,
                            recoverable = true,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun dispatchRealtimeMessage(text: String) {
        diagnostics.onEventReceived()
        logger.d("recv=${logger.redactJson(text)}")
        try {
            val event = EventParser.parse(text)
            _events.emit(event)
            onEvent(event)
        } catch (t: Throwable) {
            emitError(
                StardustSdkError(
                    code = StardustErrorCode.JSON_PARSE_FAILED,
                    message = "Failed to parse event",
                    cause = t,
                    rawEvent = text,
                    recoverable = true,
                ),
            )
        }
    }

    private suspend fun onEvent(event: StardustEvent) {
        when (event) {
            is StardustEvent.SessionCreated -> _state.value = StardustState.SessionCreated
            is StardustEvent.SessionUpdated -> _state.value = StardustState.SessionUpdated
            is StardustEvent.InputAudioBufferSpeechStarted -> {
                internalAudio.stopPlayback(clearQueue = true)
            }

            is StardustEvent.ResponseCreated -> {
                suppressAudioDelta = false
                internalAudio.onResponseCreated()
            }

            is StardustEvent.ResponseAudioDone -> {
                suppressAudioDelta = false
                audio.onServerResponseAudioDone()
            }

            is StardustEvent.ResponseDone -> {
                suppressAudioDelta = false
                audio.onServerResponseDone()
            }

            is StardustEvent.ResponseAudioDelta -> {
                if (!suppressAudioDelta) {
                    internalAudio.enqueueDeltaPcm(event.decodedPcm16)
                }
            }
            is StardustEvent.Error -> emitError(
                StardustSdkError(
                    code = StardustErrorCode.SERVER_ERROR,
                    message = event.message ?: "Server error",
                    rawEvent = event.rawJson,
                    recoverable = true,
                ),
            )

            else -> Unit
        }
    }

    private suspend fun handleReconnect(error: StardustSdkError) {
        emitError(error)
        if (!config.autoReconnectRealtime || !config.reconnectPolicy.enabled || !error.recoverable) return
        if (!reconnecting.compareAndSet(false, true)) return
        _state.value = StardustState.Reconnecting
        try {
            var delayMs = config.reconnectPolicy.initialDelayMs
            val startedAt = System.currentTimeMillis()
            for (attempt in 0 until config.reconnectPolicy.maxAttempts) {
                if (!scope.isActive || isClosed.get()) return
                if (config.reconnectPolicy.maxElapsedMs != null &&
                    System.currentTimeMillis() - startedAt > config.reconnectPolicy.maxElapsedMs
                ) {
                    return
                }
                diagnostics.onReconnectAttempt()
                try {
                    connect()
                    if (config.reconnectPolicy.resendLastSessionUpdate) {
                        lastSessionConfig?.let { updateSession(it) }
                    }
                    return
                } catch (_: Throwable) {
                    if (attempt == config.reconnectPolicy.maxAttempts - 1) break
                    delay(delayMs)
                    delayMs = min(delayMs * 2, config.reconnectPolicy.maxDelayMs)
                }
            }
            _state.value = StardustState.Failed
        } finally {
            reconnecting.set(false)
        }
    }

    private suspend fun emitError(error: StardustSdkError) {
        diagnostics.updateLastError(error.code)
        _errors.emit(error)
        logger.e("error=${error.code}: ${error.message}", error.cause)
    }

    private suspend fun waitUntilConnected() {
        if (_state.value == StardustState.Connected ||
            _state.value == StardustState.SessionCreated ||
            _state.value == StardustState.SessionUpdated
        ) {
            return
        }
        if (_state.value == StardustState.Connecting || _state.value == StardustState.Reconnecting) {
            connectDeferred?.await()
            return
        }
        throw StardustSdkException(
            StardustSdkError(
                code = StardustErrorCode.WEBSOCKET_CLOSED,
                message = "Realtime is not connected",
                recoverable = true,
            ),
        )
    }

    private suspend fun resolveToken(): String = config.tokenProvider.invoke()

    private suspend fun buildRealtimeRequest(): Request {
        val token = resolveToken().trim()
        val finalUrl = appendQuery(config.realtimeUrl, buildQueryParams())
        val builder = Request.Builder()
            .url(finalUrl)
            .header("Sec-WebSocket-Protocol", "realtime")
        if (token.isNotEmpty()) {
            builder.header("Authorization", "Bearer $token")
        }
        return builder.build()
    }

    private suspend fun buildQueryParams(): Map<String, String> {
        val params = config.queryParams.toMutableMap()
        config.terminalSecretProvider?.invoke()?.let { params["terminal_secret"] = it }
        return params
    }

    private fun appendQuery(base: String, queryParams: Map<String, String>): String {
        if (queryParams.isEmpty()) return base
        val separator = if (base.contains("?")) "&" else "?"
        val query = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return base + separator + query
    }

    private fun ensureNotClosed() {
        if (isClosed.get()) {
            throw StardustSdkException(
                StardustSdkError(
                    code = StardustErrorCode.WEBSOCKET_CLOSED,
                    message = "Client already closed",
                    recoverable = false,
                ),
            )
        }
    }
}

object StardustSdk {
    fun create(config: StardustConfig): StardustClient = DefaultStardustClient(config)
}
