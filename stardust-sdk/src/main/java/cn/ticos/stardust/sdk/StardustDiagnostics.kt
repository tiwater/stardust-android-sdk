package cn.ticos.stardust.sdk

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class DiagnosticsSnapshot(
    val reconnectAttempts: Long,
    val sentEvents: Long,
    val receivedEvents: Long,
    val realtimeConnected: Boolean,
    val videoConnected: Boolean,
    val lastErrorCode: StardustErrorCode?,
)

class StardustDiagnostics {
    private val reconnectAttempts = AtomicLong(0)
    private val sentEvents = AtomicLong(0)
    private val receivedEvents = AtomicLong(0)
    private val _snapshot = MutableStateFlow(
        DiagnosticsSnapshot(
            reconnectAttempts = 0,
            sentEvents = 0,
            receivedEvents = 0,
            realtimeConnected = false,
            videoConnected = false,
            lastErrorCode = null,
        ),
    )

    val snapshot: StateFlow<DiagnosticsSnapshot> = _snapshot.asStateFlow()

    internal fun onReconnectAttempt() = reconnectAttempts.incrementAndGet().also { publish() }
    internal fun onEventSent() = sentEvents.incrementAndGet().also { publish() }
    internal fun onEventReceived() = receivedEvents.incrementAndGet().also { publish() }

    internal fun updateRealtimeConnected(connected: Boolean) {
        _snapshot.value = _snapshot.value.copy(realtimeConnected = connected)
    }

    internal fun updateVideoConnected(connected: Boolean) {
        _snapshot.value = _snapshot.value.copy(videoConnected = connected)
    }

    internal fun updateLastError(code: StardustErrorCode?) {
        _snapshot.value = _snapshot.value.copy(lastErrorCode = code)
    }

    private fun publish() {
        _snapshot.value = _snapshot.value.copy(
            reconnectAttempts = reconnectAttempts.get(),
            sentEvents = sentEvents.get(),
            receivedEvents = receivedEvents.get(),
        )
    }
}
