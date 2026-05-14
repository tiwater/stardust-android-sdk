package cn.ticos.stardust.sample.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Spacing {
    val xs  =  4.dp
    val sm  =  8.dp
    val md  = 16.dp
    val lg  = 24.dp
    val xl  = 32.dp
    val xxl = 48.dp
}

private val LightColors = lightColorScheme(
    primary              = AppColors.Blue600,
    onPrimary            = Color.White,
    background           = AppColors.Background,
    surface              = AppColors.Surface,
    surfaceVariant       = AppColors.Gray50,
    onBackground         = AppColors.Gray900,
    onSurface            = AppColors.Gray900,
    onSurfaceVariant     = AppColors.Gray600,
    outline              = AppColors.Gray400,
    secondaryContainer   = AppColors.Blue50,
    onSecondaryContainer = AppColors.Blue600,
    error                = AppColors.Error,
)

private val DarkColors = darkColorScheme(
    primary              = AppColors.Blue400,
    onPrimary            = Color(0xFF1E3A5F),
    background           = AppColors.DarkBackground,
    surface              = AppColors.DarkSurface,
    surfaceVariant       = AppColors.DarkSurfaceVariant,
    onBackground         = AppColors.DarkOnSurface,
    onSurface            = AppColors.DarkOnSurface,
    onSurfaceVariant     = AppColors.DarkOnSurfaceVariant,
    outline              = Color(0xFF64748B),
    secondaryContainer   = Color(0xFF1E3A5F),
    onSecondaryContainer = AppColors.Blue400,
    error                = Color(0xFFF87171),
)

@Composable
fun StardustSampleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
