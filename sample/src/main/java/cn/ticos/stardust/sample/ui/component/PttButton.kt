package cn.ticos.stardust.sample.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.ui.theme.AppColors
import kotlin.math.min

@Composable
fun PttButton(
    isPressed: Boolean,
    audioLevel: Float,
    onPressDown: () -> Unit,
    onPressUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) AppColors.Blue600 else AppColors.Blue50,
        animationSpec = tween(150),
        label = "pttBg",
    )
    val textColor = if (isPressed) Color.White else AppColors.Blue600
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "pttScale",
    )
    val glowDp = (2f + min(1f, audioLevel) * 6f).dp
    val borderColor = if (isPressed) {
        AppColors.Blue600.copy(alpha = 0.35f + audioLevel * 0.45f)
    } else {
        AppColors.Blue600.copy(alpha = 0.15f)
    }

    // Use rememberUpdatedState so the pointerInput block (keyed on Unit) always calls the
    // latest lambda without needing to restart gesture detection on recomposition.
    val latestOnPressDown by rememberUpdatedState(onPressDown)
    val latestOnPressUp by rememberUpdatedState(onPressUp)

    val desc = stringResource(R.string.ptt_button_desc)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .scale(scale)
            .semantics { contentDescription = desc }
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = if (isPressed) glowDp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(28.dp)
            )
            .background(backgroundColor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    latestOnPressDown()
                    waitForUpOrCancellation()
                    latestOnPressUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (isPressed) R.string.ptt_button_pressed else R.string.ptt_button_idle,
            ),
            color = textColor,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
