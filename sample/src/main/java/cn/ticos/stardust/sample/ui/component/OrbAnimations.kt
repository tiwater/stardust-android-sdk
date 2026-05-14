package cn.ticos.stardust.sample.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.ui.theme.AppColors

/**
 * Speaking 外扩波纹：与详细设计 7.3.4 一致的多环进度与透明度。
 */
@Composable
fun Modifier.speakingRipplesBehind(
    audioLevel: Float,
    orbRadiusPx: Float,
    maxRippleRadiusPx: Float,
): Modifier {
    val transition = rememberInfiniteTransition(label = "ripple")
    val animProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rippleProgress",
    )
    return drawBehind {
        val rippleCount = 3
        val baseRadius = orbRadiusPx * 1.2f
        for (i in 0 until rippleCount) {
            val phaseOffset = i * (1f / rippleCount)
            val progress = ((animProgress + phaseOffset) % 1f)
            val radius = baseRadius + (maxRippleRadiusPx - baseRadius) * progress
            val alpha = (1f - progress) * audioLevel * 0.4f
            drawCircle(
                color = AppColors.Primary.copy(alpha = alpha),
                radius = radius,
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}
