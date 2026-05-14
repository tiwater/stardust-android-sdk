package cn.ticos.stardust.sample.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.ticos.stardust.sample.ui.screen.MainScreen
import cn.ticos.stardust.sample.ui.screen.ComplexSessionConfigScreen
import cn.ticos.stardust.sample.ui.screen.ConversationInfoScreen
import cn.ticos.stardust.sample.ui.screen.SettingsScreen
import cn.ticos.stardust.sample.viewmodel.SampleViewModelFactory
import cn.ticos.stardust.sample.viewmodel.SettingsViewModel
import cn.ticos.stardust.sample.viewmodel.TtsViewModel
import cn.ticos.stardust.sample.viewmodel.VoiceViewModel

sealed class AppRoute(val route: String) {
    data object Main : AppRoute("main")
    data object Settings : AppRoute("settings")
    data object ComplexSessionConfig : AppRoute("complex_session_config")
    data object ConversationInfo : AppRoute("conversation_info")
}

@Composable
fun AppNavigation(
    factory: SampleViewModelFactory,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = AppRoute.Main.route,
        modifier = modifier,
    ) {
        composable(AppRoute.Main.route) {
            val voiceVm: VoiceViewModel = viewModel(factory = factory)
            MainScreen(
                voiceViewModel = voiceVm,
                onOpenSettings = { navController.navigate(AppRoute.Settings.route) },
                onOpenConversationInfo = { navController.navigate(AppRoute.ConversationInfo.route) },
            )
        }
        composable(AppRoute.Settings.route) {
            val settingsVm: SettingsViewModel = viewModel(factory = factory)
            SettingsScreen(
                viewModel = settingsVm,
                onNavigateBack = { navController.popBackStack() },
                onEditAdvancedConfig = {
                    navController.navigate(AppRoute.ComplexSessionConfig.route)
                },
            )
        }
        composable(AppRoute.ComplexSessionConfig.route) {
            val settingsVm: SettingsViewModel = viewModel(factory = factory)
            val ttsVm: TtsViewModel = viewModel(factory = factory)
            ComplexSessionConfigScreen(
                settingsViewModel = settingsVm,
                ttsViewModel = ttsVm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.ConversationInfo.route) {
            val mainEntry = navController.getBackStackEntry(AppRoute.Main.route)
            val voiceVm: VoiceViewModel = viewModel(
                viewModelStoreOwner = mainEntry,
                factory = factory,
            )
            ConversationInfoScreen(
                voiceViewModel = voiceVm,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
