package cn.ticos.stardust.sample

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import cn.ticos.stardust.sample.data.SettingsRepository
import cn.ticos.stardust.sample.data.TtsApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var ttsApiClient: TtsApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository.create(this)
        ttsApiClient = TtsApiClient()
        
        // Apply saved language on startup
        MainScope().launch {
            val settings = settingsRepository.settings.first()
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(settings.language)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }
}
