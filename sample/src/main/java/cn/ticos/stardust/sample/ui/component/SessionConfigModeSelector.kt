package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.SessionConfigMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionConfigModeSelector(
    currentMode: SessionConfigMode,
    onModeChange: (SessionConfigMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        SessionConfigMode.AgentId  to stringResource(R.string.session_mode_agent_id),
        SessionConfigMode.Advanced to stringResource(R.string.session_mode_advanced),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (mode, label) ->
            SegmentedButton(
                selected = currentMode == mode,
                onClick = { onModeChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label) },
            )
        }
    }
}
