package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.viewmodel.VoiceViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisionFpsSelector(
    selectedFps: Int,
    onFpsSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 宽屏：标签与 chip 组在同一行；窄屏：chip 组整体换行，不会被拆开
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.vision_fps_label),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceViewModel.ALLOWED_FPS_OPTIONS.forEach { fps ->
                FilterChip(
                    selected = fps == selectedFps,
                    onClick = { onFpsSelected(fps) },
                    label = { Text(stringResource(R.string.vision_fps_chip, fps)) },
                )
            }
        }
    }
}
