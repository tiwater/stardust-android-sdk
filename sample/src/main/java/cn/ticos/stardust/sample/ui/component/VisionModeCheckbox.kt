package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.R

@Composable
fun VisionModeCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.vision_checkbox_desc)
    FilterChip(
        selected = checked,
        onClick = { onCheckedChange(!checked) },
        label = {
            Text(
                text = stringResource(R.string.vision_mode_checkbox),
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        modifier = modifier.semantics { contentDescription = desc },
    )
}
