package cn.ticos.stardust.sample.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.ConversationRecord
import cn.ticos.stardust.sample.ui.theme.AppColors
import cn.ticos.stardust.sample.ui.theme.Spacing
import cn.ticos.stardust.sample.viewmodel.VoiceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RecordFilter {
    All, User, Assistant, Function
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationInfoScreen(
    voiceViewModel: VoiceViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf(RecordFilter.All) }

    val filteredRecords = remember(uiState.conversationRecords, selectedFilter, uiState.audioCacheRevision) {
        when (selectedFilter) {
            RecordFilter.All -> uiState.conversationRecords
            RecordFilter.User -> uiState.conversationRecords.filter {
                it is ConversationRecord.UserVoice || it is ConversationRecord.UserText
            }
            RecordFilter.Assistant -> uiState.conversationRecords.filterIsInstance<ConversationRecord.AssistantVoice>()
            RecordFilter.Function -> uiState.conversationRecords.filterIsInstance<ConversationRecord.FunctionCall>()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.conversation_info)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.md),
        ) {
            ConversationSummaryCard(
                totalCount = uiState.conversationRecordCount,
                userVoiceCount = uiState.userVoiceCount,
                userTextCount = uiState.userTextCount,
                assistantVoiceCount = uiState.assistantVoiceCount,
                functionCallCount = uiState.functionCallCount,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            RecordFilterChipRow(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it },
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (filteredRecords.isEmpty()) {
                EmptyStateMessage(
                    isFiltered = selectedFilter != RecordFilter.All &&
                        uiState.conversationRecordCount > 0,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    contentPadding = PaddingValues(bottom = Spacing.md),
                ) {
                    items(
                        items = filteredRecords,
                        key = { it.id },
                    ) { record ->
                        val itemId = when (record) {
                            is ConversationRecord.UserVoice -> record.itemId
                            is ConversationRecord.AssistantVoice -> record.itemId
                            else -> null
                        }
                        ConversationRecordCard(
                            record = record,
                            isPlayable = itemId != null && itemId in uiState.playableAudioItemIds,
                            isPlaying = itemId != null && itemId == uiState.playingItemId,
                            onPlay = { itemId?.let { voiceViewModel.playAudio(it) } },
                            onStop = { voiceViewModel.stopAudioReplay() },
                        )
                    }
                    if (uiState.conversationRecordCount >= VoiceViewModel.MAX_CONVERSATION_RECORDS) {
                        item {
                            Text(
                                text = stringResource(
                                    R.string.conv_info_max_reached,
                                    VoiceViewModel.MAX_CONVERSATION_RECORDS,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationSummaryCard(
    totalCount: Int,
    userVoiceCount: Int,
    userTextCount: Int,
    assistantVoiceCount: Int,
    functionCallCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 0.dp,
    ) {
        if (totalCount == 0) {
            Text(
                text = stringResource(R.string.conv_info_empty),
                modifier = Modifier.padding(Spacing.md),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = stringResource(R.string.conv_info_total, totalCount),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    StatChip(
                        label = stringResource(R.string.conv_info_user_voice_short),
                        count = userVoiceCount,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (userTextCount > 0) {
                        StatChip(
                            label = stringResource(R.string.conv_info_user_text_short),
                            count = userTextCount,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    }
                    StatChip(
                        label = stringResource(R.string.conv_info_assistant_short),
                        count = assistantVoiceCount,
                        color = AppColors.Green600,
                    )
                    StatChip(
                        label = stringResource(R.string.conv_info_function_short),
                        count = functionCallCount,
                        color = AppColors.Orange600,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun RecordFilterChipRow(
    selectedFilter: RecordFilter,
    onFilterSelected: (RecordFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RecordFilter.entries.forEach { filter ->
            val label = when (filter) {
                RecordFilter.All -> stringResource(R.string.conv_info_filter_all)
                RecordFilter.User -> stringResource(R.string.conv_info_filter_user)
                RecordFilter.Assistant -> stringResource(R.string.conv_info_filter_assistant)
                RecordFilter.Function -> stringResource(R.string.conv_info_filter_function)
            }
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(
    isFiltered: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                if (isFiltered) R.string.conv_info_filter_empty else R.string.conv_info_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConversationRecordCard(
    record: ConversationRecord,
    isPlayable: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: () -> Unit = {},
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val (labelResId, labelColor, icon) = when (record) {
        is ConversationRecord.UserVoice -> Triple(
            R.string.conv_info_user_voice, MaterialTheme.colorScheme.primary, Icons.Default.Mic
        )
        is ConversationRecord.UserText -> Triple(
            R.string.conv_info_user_text, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), Icons.Outlined.Chat
        )
        is ConversationRecord.AssistantVoice -> Triple(
            R.string.conv_info_assistant_voice, AppColors.Green600, Icons.Default.VolumeUp
        )
        is ConversationRecord.FunctionCall -> Triple(
            R.string.conv_info_function_call, AppColors.Orange600, Icons.Default.Code
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = labelColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = stringResource(labelResId),
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor,
                    )
                }
                Text(
                    text = formatTimestamp(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            when (record) {
                is ConversationRecord.UserVoice -> {
                    ExpandableText(
                        text = record.text.ifBlank { stringResource(R.string.conv_info_transcribing) },
                    )
                    if (record.hasAudio) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            AudioInfoChip(segments = record.audioSegments)
                            if (isPlayable || isPlaying) {
                                AudioPlaybackButton(
                                    isPlaying = isPlaying,
                                    onPlay = onPlay,
                                    onStop = onStop,
                                )
                            }
                        }
                    }
                }
                is ConversationRecord.UserText -> {
                    ExpandableText(text = record.text)
                }
                is ConversationRecord.AssistantVoice -> {
                    ExpandableText(
                        text = record.text.ifBlank { stringResource(R.string.conv_info_generating) },
                    )
                    if (record.hasAudio) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            AudioInfoChip(segments = record.audioSegments)
                            if (isPlayable || isPlaying) {
                                AudioPlaybackButton(
                                    isPlaying = isPlaying,
                                    onPlay = onPlay,
                                    onStop = onStop,
                                )
                            }
                        }
                    }
                }
                is ConversationRecord.FunctionCall -> {
                    Text(
                        text = record.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    ExpandableJsonText(json = record.arguments)
                }
            }
        }
    }
}

@Composable
private fun ExpandableText(
    text: String,
    maxLines: Int = 3,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val needsExpand = text.length > 200 || text.count { it == '\n' } > maxLines

    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
        )
        if (needsExpand) {
            Text(
                text = stringResource(if (expanded) R.string.conv_info_collapse else R.string.conv_info_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun ExpandableJsonText(
    json: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val formatted = remember(json) { formatJsonSafe(json) }
    val isLong = formatted.length > 100 || formatted.count { it == '\n' } > 3

    Column(modifier = modifier) {
        SelectionContainer {
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded || !isLong) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(Spacing.sm),
                    )
                    .padding(Spacing.sm),
            )
        }
        if (isLong) {
            Text(
                text = stringResource(if (expanded) R.string.conv_info_collapse else R.string.conv_info_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun AudioPlaybackButton(
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = if (isPlaying) onStop else onPlay,
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (isPlaying) R.string.conv_info_stop_audio else R.string.conv_info_play_audio,
            ),
            tint = if (isPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AudioInfoChip(
    segments: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(top = Spacing.xs)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(Spacing.sm),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.xs))
        Text(
            text = stringResource(R.string.conv_info_audio_segments, segments),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}

private fun formatJsonSafe(raw: String): String {
    if (raw.isBlank()) return raw
    return try {
        val element = kotlinx.serialization.json.Json.parseToJsonElement(raw)
        val prettyJson = kotlinx.serialization.json.Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }
        prettyJson.encodeToString(
            kotlinx.serialization.json.JsonElement.serializer(),
            element,
        )
    } catch (_: Throwable) {
        raw
    }
}
