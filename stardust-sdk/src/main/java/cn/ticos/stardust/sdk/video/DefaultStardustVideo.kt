package cn.ticos.stardust.sdk.video

import cn.ticos.stardust.sdk.StardustErrorCode
import cn.ticos.stardust.sdk.StardustSdkError
import cn.ticos.stardust.sdk.StardustSdkException
import cn.ticos.stardust.sdk.StardustState
import cn.ticos.stardust.sdk.StardustVideo
import cn.ticos.stardust.sdk.VideoConnectPolicy
import cn.ticos.stardust.sdk.VideoState
import cn.ticos.stardust.sdk.internal.StardustLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

internal class DefaultStardustVideo(
    private val connectWs: suspend (url: String, headers: Map<String, String>, listener: WebSocketListener) -> WebSocket,
    private val realtimeStateProvider: () -> StardustState,
    private val configVideoUrl: String,
    private val authTokenProvider: suspend () -> String,
    private val queryParamsProvider: suspend () -> Map<String, String>,
    private val connectPolicy: VideoConnectPolicy,
    private val emitError: suspend (StardustSdkError) -> Unit,
    private val onConnectedChanged: (Boolean) -> Unit,
    private val logger: StardustLogger,
) : StardustVideo {
    private val _state = MutableStateFlow(VideoState.Idle)
    override val state: StateFlow<VideoState> = _state.asStateFlow()
    private val packetizer = JpegFramePacketizer()
    private var ws: WebSocket? = null
    private var lastFrameAtMs = 0L
    var maxFps: Int = 2

    override suspend fun connect() {
        if (!isRealtimeReady(realtimeStateProvider(), connectPolicy)) {
            _state.value = VideoState.WaitingRealtime
            val error = StardustSdkError(
                code = StardustErrorCode.VIDEO_REALTIME_NOT_READY,
                message = "Realtime session is not ready for video connection",
                recoverable = true,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
        _state.value = VideoState.Connecting
        val token = authTokenProvider().trim()
        val finalUrl = appendQuery(configVideoUrl, queryParamsProvider())
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                _state.value = VideoState.Connected
                onConnectedChanged(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _state.value = VideoState.Disconnected
                onConnectedChanged(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                _state.value = VideoState.Failed
                onConnectedChanged(false)
                logger.e("Video websocket failure", t)
            }
        }
        val headers = buildMap {
            if (token.isNotEmpty()) {
                put("Authorization", "Bearer $token")
            }
        }
        ws = connectWs(finalUrl, headers, listener)
    }

    override suspend fun sendJpegFrame(jpeg: ByteArray) {
        val socket = ws
        if (socket == null || _state.value != VideoState.Connected) {
            val error = StardustSdkError(
                code = StardustErrorCode.VIDEO_NOT_CONNECTED,
                message = "Video websocket is not connected",
                recoverable = true,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
        if (!isRealtimeReady(realtimeStateProvider(), connectPolicy)) {
            val error = StardustSdkError(
                code = StardustErrorCode.VIDEO_REALTIME_NOT_READY,
                message = "Realtime session is not ready",
                recoverable = true,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
        if (!allowSendFrame()) return
        val packet = packetizer.packetize(jpeg)
        if (!socket.send(packet.toByteString())) {
            val error = StardustSdkError(
                code = StardustErrorCode.VIDEO_PACKETIZE_FAILED,
                message = "Failed to send packetized frame",
                recoverable = true,
            )
            emitError(error)
            throw StardustSdkException(error)
        }
    }

    override suspend fun disconnect() {
        ws?.close(1000, "normal")
        ws = null
        _state.value = VideoState.Disconnected
        onConnectedChanged(false)
    }

    private fun allowSendFrame(): Boolean {
        val fps = maxFps.coerceIn(1, 30)
        val interval = 1000L / fps
        val now = System.currentTimeMillis()
        if (now - lastFrameAtMs < interval) {
            return false
        }
        lastFrameAtMs = now
        return true
    }

    private fun isRealtimeReady(state: StardustState, policy: VideoConnectPolicy): Boolean {
        return when (policy) {
            VideoConnectPolicy.RequireSessionCreated ->
                state == StardustState.SessionCreated || state == StardustState.SessionUpdated

            VideoConnectPolicy.RequireSessionUpdated ->
                state == StardustState.SessionUpdated
        }
    }

    private fun appendQuery(base: String, queryParams: Map<String, String>): String {
        if (queryParams.isEmpty()) return base
        val separator = if (base.contains("?")) "&" else "?"
        val query = queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return base + separator + query
    }
}
