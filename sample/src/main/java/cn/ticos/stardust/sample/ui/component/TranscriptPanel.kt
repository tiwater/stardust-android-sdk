package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.ticos.stardust.sample.model.Role
import cn.ticos.stardust.sample.model.TranscriptItem

@Composable
fun TranscriptPanel(
    transcripts: List<TranscriptItem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 160.dp),
        reverseLayout = true,
    ) {
        items(transcripts, key = { it.id }) { item ->
            TranscriptRow(item)
        }
    }
}

@Composable
private fun TranscriptRow(item: TranscriptItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            textAlign = if (item.role == Role.User) TextAlign.End else TextAlign.Start,
        )
    }
}
