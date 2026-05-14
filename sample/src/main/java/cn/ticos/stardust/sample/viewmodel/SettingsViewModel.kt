package cn.ticos.stardust.sample.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.ticos.stardust.sample.data.SettingsRepository
import cn.ticos.stardust.sample.model.AdvancedSessionSettings
import cn.ticos.stardust.sample.model.AppSettings
import cn.ticos.stardust.sample.model.SessionConfigMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val settings = settingsRepo.settings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        AppSettings(),
    )

    suspend fun persistAll(
        agentId: String,
        serverUrl: String,
        terminalSecret: String,
        groupId: String,
        robotId: String,
        autoPlayAudio: Boolean,
        sessionConfigMode: SessionConfigMode,
    ) {
        settingsRepo.update {
            it.copy(
                agentId = agentId.trim(),
                serverUrl = serverUrl.trim(),
                terminalSecret = terminalSecret.trim(),
                groupId = groupId.trim(),
                robotId = robotId.trim(),
                autoPlayAudio = autoPlayAudio,
                sessionConfigMode = sessionConfigMode,
            )
        }
    }

    suspend fun persistSessionConfigMode(mode: SessionConfigMode) {
        settingsRepo.update { it.copy(sessionConfigMode = mode) }
    }

    suspend fun persistAdvancedSession(advanced: AdvancedSessionSettings) {
        settingsRepo.update { it.copy(advancedSession = advanced) }
    }

    suspend fun resetAdvancedSessionToDefaults() {
        settingsRepo.update { it.copy(advancedSession = AdvancedSessionSettings()) }
    }
}
