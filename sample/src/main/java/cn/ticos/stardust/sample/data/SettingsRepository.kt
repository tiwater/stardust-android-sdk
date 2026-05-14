package cn.ticos.stardust.sample.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import cn.ticos.stardust.sample.model.AdvancedSessionSettings
import cn.ticos.stardust.sample.model.AppSettings
import cn.ticos.stardust.sample.model.DEFAULT_REALTIME_URL
import cn.ticos.stardust.sample.model.SessionConfigMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "stardust_sample_settings")

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefsToAppSettings(prefs) }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefsToAppSettings(prefs)
            val next = transform(current)
            prefs[KEY_AGENT_ID] = next.agentId
            prefs[KEY_SERVER_URL] = next.serverUrl
            prefs[KEY_TERMINAL_SECRET] = next.terminalSecret
            prefs[KEY_GROUP_ID] = next.groupId
            prefs[KEY_ROBOT_ID] = next.robotId
            prefs[KEY_AUTO_PLAY] = next.autoPlayAudio
            prefs[KEY_LANGUAGE] = next.language
            prefs[KEY_SESSION_CONFIG_MODE] = next.sessionConfigMode.name
            val adv = next.advancedSession
            prefs[KEY_ADV_MODEL_PROVIDER] = adv.modelProvider
            prefs[KEY_ADV_MODEL_NAME] = adv.modelName
            prefs[KEY_ADV_INSTRUCTIONS] = adv.instructions
            prefs[KEY_ADV_TEMPERATURE] = adv.temperature.toString()
            prefs[KEY_ADV_TOP_P] = adv.topP.toString()
            prefs[KEY_ADV_TOP_K] = adv.topK.toString()
            prefs[KEY_ADV_MAX_TOKENS] = adv.maxResponseOutputTokens.toString()
            prefs[KEY_ADV_HISTORY_LENGTH] = adv.historyConversationLength.toString()
            prefs[KEY_ADV_SPEECH_VOICE] = adv.speechVoice
            prefs[KEY_ADV_SPEECH_EMOTION] = adv.speechEmotion
            prefs[KEY_ADV_SPEED_RATIO] = adv.speechSpeedRatio.toString()
            prefs[KEY_ADV_PITCH_RATIO] = adv.speechPitchRatio.toString()
            prefs[KEY_ADV_VOLUME_RATIO] = adv.speechVolumeRatio.toString()
            prefs[KEY_ADV_HEARING_PROVIDER] = adv.hearingProvider
            prefs[KEY_ADV_HEARING_PREFIX_PADDING_MS] = adv.hearingPrefixPaddingMs.toString()
            prefs[KEY_ADV_HEARING_SILENCE_DURATION_MS] = adv.hearingSilenceDurationMs.toString()
            prefs[KEY_ADV_HEARING_SENSITIVITY] = adv.hearingSensitivity.toString()
        }
    }

    private fun prefsToAppSettings(prefs: Preferences): AppSettings {
        val defaults = AdvancedSessionSettings()
        return AppSettings(
            agentId = prefs[KEY_AGENT_ID] ?: "",
            serverUrl = prefs[KEY_SERVER_URL] ?: DEFAULT_REALTIME_URL,
            terminalSecret = prefs[KEY_TERMINAL_SECRET] ?: "",
            groupId = prefs[KEY_GROUP_ID] ?: "",
            robotId = prefs[KEY_ROBOT_ID] ?: "",
            autoPlayAudio = prefs[KEY_AUTO_PLAY] ?: true,
            language = prefs[KEY_LANGUAGE] ?: "en",
            sessionConfigMode = prefs[KEY_SESSION_CONFIG_MODE]
                ?.let { runCatching { SessionConfigMode.valueOf(it) }.getOrNull() }
                ?: SessionConfigMode.AgentId,
            advancedSession = AdvancedSessionSettings(
                modelProvider = prefs[KEY_ADV_MODEL_PROVIDER] ?: defaults.modelProvider,
                modelName = prefs[KEY_ADV_MODEL_NAME] ?: defaults.modelName,
                instructions = prefs[KEY_ADV_INSTRUCTIONS] ?: defaults.instructions,
                temperature = prefs[KEY_ADV_TEMPERATURE]?.toDoubleOrNull() ?: defaults.temperature,
                topP = prefs[KEY_ADV_TOP_P]?.toDoubleOrNull() ?: defaults.topP,
                topK = prefs[KEY_ADV_TOP_K]?.toIntOrNull() ?: defaults.topK,
                maxResponseOutputTokens = prefs[KEY_ADV_MAX_TOKENS]?.toIntOrNull()
                    ?: defaults.maxResponseOutputTokens,
                historyConversationLength = prefs[KEY_ADV_HISTORY_LENGTH]?.toIntOrNull()
                    ?: defaults.historyConversationLength,
                speechVoice = prefs[KEY_ADV_SPEECH_VOICE] ?: defaults.speechVoice,
                speechEmotion = prefs[KEY_ADV_SPEECH_EMOTION] ?: defaults.speechEmotion,
                speechSpeedRatio = prefs[KEY_ADV_SPEED_RATIO]?.toIntOrNull()
                    ?: defaults.speechSpeedRatio,
                speechPitchRatio = prefs[KEY_ADV_PITCH_RATIO]?.toIntOrNull()
                    ?: defaults.speechPitchRatio,
                speechVolumeRatio = prefs[KEY_ADV_VOLUME_RATIO]?.toIntOrNull()
                    ?: defaults.speechVolumeRatio,
                hearingProvider = prefs[KEY_ADV_HEARING_PROVIDER] ?: defaults.hearingProvider,
                hearingPrefixPaddingMs = prefs[KEY_ADV_HEARING_PREFIX_PADDING_MS]?.toIntOrNull()
                    ?: defaults.hearingPrefixPaddingMs,
                hearingSilenceDurationMs = prefs[KEY_ADV_HEARING_SILENCE_DURATION_MS]?.toIntOrNull()
                    ?: defaults.hearingSilenceDurationMs,
                hearingSensitivity = prefs[KEY_ADV_HEARING_SENSITIVITY]?.toDoubleOrNull()
                    ?: defaults.hearingSensitivity,
            ),
        )
    }

    companion object {
        private val KEY_AGENT_ID = stringPreferencesKey("agent_id")
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_TERMINAL_SECRET = stringPreferencesKey("terminal_secret")
        private val KEY_GROUP_ID = stringPreferencesKey("group_id")
        private val KEY_ROBOT_ID = stringPreferencesKey("robot_id")
        private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
        private val KEY_LANGUAGE = stringPreferencesKey("language")

        private val KEY_SESSION_CONFIG_MODE = stringPreferencesKey("session_config_mode")
        private val KEY_ADV_MODEL_PROVIDER = stringPreferencesKey("adv_model_provider")
        private val KEY_ADV_MODEL_NAME = stringPreferencesKey("adv_model_name")
        private val KEY_ADV_INSTRUCTIONS = stringPreferencesKey("adv_instructions")
        private val KEY_ADV_TEMPERATURE = stringPreferencesKey("adv_temperature")
        private val KEY_ADV_TOP_P = stringPreferencesKey("adv_top_p")
        private val KEY_ADV_TOP_K = stringPreferencesKey("adv_top_k")
        private val KEY_ADV_MAX_TOKENS = stringPreferencesKey("adv_max_tokens")
        private val KEY_ADV_HISTORY_LENGTH = stringPreferencesKey("adv_history_length")
        private val KEY_ADV_SPEECH_VOICE = stringPreferencesKey("adv_speech_voice")
        private val KEY_ADV_SPEECH_EMOTION = stringPreferencesKey("adv_speech_emotion")
        private val KEY_ADV_SPEED_RATIO = stringPreferencesKey("adv_speed_ratio")
        private val KEY_ADV_PITCH_RATIO = stringPreferencesKey("adv_pitch_ratio")
        private val KEY_ADV_VOLUME_RATIO = stringPreferencesKey("adv_volume_ratio")
        private val KEY_ADV_HEARING_PROVIDER = stringPreferencesKey("adv_hearing_provider")
        private val KEY_ADV_HEARING_PREFIX_PADDING_MS =
            stringPreferencesKey("adv_hearing_prefix_padding_ms")
        private val KEY_ADV_HEARING_SILENCE_DURATION_MS =
            stringPreferencesKey("adv_hearing_silence_duration_ms")
        private val KEY_ADV_HEARING_SENSITIVITY = stringPreferencesKey("adv_hearing_sensitivity")

        fun create(context: Context): SettingsRepository =
            SettingsRepository(context.applicationContext.dataStore)
    }
}
