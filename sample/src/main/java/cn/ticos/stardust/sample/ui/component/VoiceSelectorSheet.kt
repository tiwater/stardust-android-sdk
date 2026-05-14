package cn.ticos.stardust.sample.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.Speaker
import cn.ticos.stardust.sample.ui.theme.AppColors
import cn.ticos.stardust.sample.viewmodel.TtsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelectorSheet(
    serverUrl: String,
    ttsViewModel: TtsViewModel,
    currentVoice: String,
    onVoiceSelected: (Speaker) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val speakers by ttsViewModel.speakers.collectAsStateWithLifecycle()
    val loading by ttsViewModel.isLoading.collectAsStateWithLifecycle()
    val error by ttsViewModel.error.collectAsStateWithLifecycle()
    val hasMore by ttsViewModel.hasMore.collectAsStateWithLifecycle()
    val filters by ttsViewModel.filters.collectAsStateWithLifecycle()

    var nameDraft by remember { mutableStateOf(filters.nameQuery.orEmpty()) }
    var providerDraft by remember { mutableStateOf(filters.provider.orEmpty()) }
    var tagsDraft by remember { mutableStateOf(filters.tags.orEmpty()) }

    LaunchedEffect(serverUrl) {
        ttsViewModel.loadSpeakers(serverUrl)
    }

    LaunchedEffect(nameDraft) {
        delay(300)
        val q = nameDraft.trim().takeIf { it.isNotEmpty() }
        if (q != filters.nameQuery) {
            ttsViewModel.updateFilters { it.copy(nameQuery = q) }
        }
    }

    LaunchedEffect(providerDraft) {
        delay(300)
        val p = providerDraft.trim().takeIf { it.isNotEmpty() }
        if (p != filters.provider) {
            ttsViewModel.updateFilters { it.copy(provider = p) }
        }
    }

    LaunchedEffect(tagsDraft) {
        delay(300)
        val t = tagsDraft.trim().takeIf { it.isNotEmpty() }
        if (t != filters.tags) {
            ttsViewModel.updateFilters { it.copy(tags = t) }
        }
    }

    var custom by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Background,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 32.dp,
            ),
        ) {
            item {
                Text(
                    text = stringResource(R.string.select_voice),
                    style = MaterialTheme.typography.titleLarge,
                    color = AppColors.Gray900,
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_voice)) },
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilterRow(
                    label = stringResource(R.string.language_filter),
                    options = listOf(
                        null to stringResource(R.string.all_option),
                        "chinese" to "Chinese",
                        "english" to "English",
                        "japanese" to "Japanese",
                    ),
                    selected = filters.language,
                    onSelect = { lang ->
                        ttsViewModel.updateFilters { it.copy(language = lang) }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilterRow(
                    label = stringResource(R.string.gender_filter),
                    options = listOf(
                        null to stringResource(R.string.all_option),
                        "male" to "male",
                        "female" to "female",
                    ),
                    selected = filters.gender,
                    onSelect = { g ->
                        ttsViewModel.updateFilters { it.copy(gender = g) }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = providerDraft,
                    onValueChange = { providerDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.provider_filter)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.filter_optional_hint)) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsDraft,
                    onValueChange = { tagsDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tags_filter)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.tags_hint)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            error?.let { err ->
                item {
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { ttsViewModel.loadSpeakers(serverUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.retry))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (loading && speakers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }
            } else if (!loading && speakers.isEmpty() && error == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.no_speakers_found),
                            modifier = Modifier.align(Alignment.Center),
                            color = AppColors.Gray400,
                        )
                    }
                }
            }

            items(speakers, key = { it.voice }) { sp ->
                val selected = sp.voice == currentVoice
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onVoiceSelected(sp)
                            onDismiss()
                        }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = sp.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) AppColors.Blue600 else AppColors.Gray900,
                    )
                    Text(
                        text = sp.voice,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.Gray400,
                    )
                    sp.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                        Text(
                            text = tags.joinToString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Gray400,
                        )
                    }
                    sp.language?.takeIf { it.isNotEmpty() }?.let { langs ->
                        Text(
                            text = langs.joinToString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppColors.Gray400,
                        )
                    }
                }
            }

            if (hasMore) {
                item(key = "load_more_btn") {
                    OutlinedButton(
                        onClick = { ttsViewModel.loadMore(serverUrl) },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Text(stringResource(R.string.load_more))
                    }
                }
            }

            if (loading && speakers.isNotEmpty()) {
                item(key = "loading_more") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            item(key = "custom_voice") {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.custom_voice_id),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.Gray600,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.custom_voice_id_hint)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val id = custom.trim()
                            if (id.isNotEmpty()) {
                                onVoiceSelected(Speaker(name = id, voice = id))
                                onDismiss()
                            }
                        },
                    ) {
                        Text(stringResource(R.string.use_custom_voice_id))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    label: String,
    options: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.first == selected }?.second ?: options[0].second
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            value = display,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                )
            }
        }
    }
}
