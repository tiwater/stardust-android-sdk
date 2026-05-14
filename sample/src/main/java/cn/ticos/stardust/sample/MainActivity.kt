package cn.ticos.stardust.sample

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import cn.ticos.stardust.sample.navigation.AppNavigation
import cn.ticos.stardust.sample.ui.theme.AppColors
import cn.ticos.stardust.sample.ui.theme.StardustSampleTheme
import cn.ticos.stardust.sample.viewmodel.SampleViewModelFactory
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var audioManager: AudioManager? = null
    private var previousAudioMode: Int? = null
    private var previousSpeakerphoneOn: Boolean? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        val factory = SampleViewModelFactory(
            application,
            app.settingsRepository,
            app.ttsApiClient,
        )
        setContent {
            StardustSampleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = AppColors.Background,
                ) {
                    AppNavigation(factory = factory)
                }
            }
        }

        // 在旧版 Android（API < 33）上，AppCompatDelegate.setApplicationLocales()
        // 不一定能可靠地触发 Activity 重建，导致 stringResource() 无法即时刷新。
        // 这里主动监听语言设置变化，语言切换后显式调用 recreate()，确保全部文字立即更新。
        lifecycleScope.launch {
            var initialized = false
            app.settingsRepository.settings
                .map { it.language }
                .distinctUntilChanged()
                .collect {
                    if (initialized) recreate() else initialized = true
                }
        }
    }

    override fun onStart() {
        super.onStart()
        configureConversationAudio()
    }

    override fun onStop() {
        restoreConversationAudio()
        super.onStop()
    }

    private fun configureConversationAudio() {
        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        if (previousAudioMode == null) {
            previousAudioMode = manager.mode
            @Suppress("DEPRECATION")
            previousSpeakerphoneOn = manager.isSpeakerphoneOn
        }

        requestConversationAudioFocus(manager)
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeConversationAudioToSpeaker(manager)
    }

    private fun restoreConversationAudio() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            manager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = previousSpeakerphoneOn ?: false
        }
        previousAudioMode?.let { manager.mode = it }
        audioFocusRequest?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.abandonAudioFocusRequest(it)
            }
        }
        audioFocusRequest = null
        previousAudioMode = null
        previousSpeakerphoneOn = null
    }

    private fun requestConversationAudioFocus(manager: AudioManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .build()
        manager.requestAudioFocus(request)
        audioFocusRequest = request
    }

    private fun routeConversationAudioToSpeaker(manager: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = manager.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speaker != null) {
                manager.setCommunicationDevice(speaker)
            }
        } else {
            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = true
        }
    }
}
