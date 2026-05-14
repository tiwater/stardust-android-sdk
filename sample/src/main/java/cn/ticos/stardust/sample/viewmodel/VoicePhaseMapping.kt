package cn.ticos.stardust.sample.viewmodel

import cn.ticos.stardust.sdk.CaptureAudioState
import cn.ticos.stardust.sdk.PlaybackAudioState
import cn.ticos.stardust.sdk.StardustState
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.VoicePhase

val StardustState.isDisconnected: Boolean
    get() = this == StardustState.Idle || this == StardustState.Closed

fun mapSdkToVoicePhase(
    sdkState: StardustState,
    captureState: CaptureAudioState,
    playbackState: PlaybackAudioState,
    awaitingResponse: Boolean,
): VoicePhase = when {
    sdkState == StardustState.Failed -> VoicePhase.Error
    sdkState == StardustState.Connecting ||
        sdkState == StardustState.Reconnecting ||
        sdkState == StardustState.Closing -> VoicePhase.Connecting
    sdkState.isDisconnected -> VoicePhase.Ready
    playbackState == PlaybackAudioState.Failed -> VoicePhase.Error
    playbackState == PlaybackAudioState.Playing -> VoicePhase.Speaking
    awaitingResponse -> VoicePhase.Thinking
    captureState == CaptureAudioState.Recording ||
        captureState == CaptureAudioState.Stopping -> VoicePhase.Listening
    sdkState == StardustState.Connected ||
        sdkState == StardustState.SessionCreated ||
        sdkState == StardustState.SessionUpdated -> VoicePhase.Idle
    else -> VoicePhase.Ready
}

fun VoicePhase.toStatusResId(): Int = when (this) {
    VoicePhase.Ready -> R.string.status_ready
    VoicePhase.Connecting -> R.string.status_connecting
    VoicePhase.Idle -> R.string.status_idle
    VoicePhase.Listening -> R.string.status_listening
    VoicePhase.Thinking -> R.string.status_thinking
    VoicePhase.Speaking -> R.string.status_speaking
    VoicePhase.Error -> R.string.status_error
}
