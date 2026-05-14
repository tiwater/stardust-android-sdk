package cn.ticos.stardust.sample.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cn.ticos.stardust.sample.data.SettingsRepository
import cn.ticos.stardust.sample.data.TtsApiClient

class SampleViewModelFactory(
    private val application: Application,
    private val settingsRepository: SettingsRepository,
    private val ttsApiClient: TtsApiClient,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(VoiceViewModel::class.java) ->
                VoiceViewModel(application, settingsRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settingsRepository) as T
            modelClass.isAssignableFrom(TtsViewModel::class.java) ->
                TtsViewModel(ttsApiClient) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
