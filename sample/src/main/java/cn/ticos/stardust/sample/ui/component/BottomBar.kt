package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BottomBar(
    onSettingsClick: () -> Unit,
    onReconnectClick: () -> Unit,
    onTranscriptToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.height(56.dp), tonalElevation = 2.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onSettingsClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Settings, contentDescription = null)
            }
            IconButton(onClick = onReconnectClick, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
            }
            IconButton(onClick = onTranscriptToggle, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Chat, contentDescription = null)
            }
        }
    }
}
