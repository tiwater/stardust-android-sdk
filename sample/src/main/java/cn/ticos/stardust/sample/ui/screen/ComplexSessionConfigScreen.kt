package cn.ticos.stardust.sample.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.AdvancedSessionSettings
import cn.ticos.stardust.sample.ui.component.AppLabeledSlider
import cn.ticos.stardust.sample.ui.component.AppSectionCard
import cn.ticos.stardust.sample.ui.component.AppTextField
import cn.ticos.stardust.sample.ui.component.VoiceSelectorSheet
import cn.ticos.stardust.sample.ui.theme.Spacing
import cn.ticos.stardust.sample.viewmodel.validateAdvancedSession
import cn.ticos.stardust.sample.viewmodel.SettingsViewModel
import cn.ticos.stardust.sample.viewmodel.TtsViewModel
import kotlinx.coroutines.launch

private val MODEL_PROVIDERS = listOf(
    "tiwater",
    "aliyun",
    "bytedance",
    "qcloud",
    "baidu",
    "deepseek",
    "openai",
    "customization",
)

private val TIWATER_MODEL_NAMES = listOf(
    "stardust-6.0",
    "stardust-5.0",
    "stardust-3.0",
    "stardust-2.5-max",
    "stardust-2.5-pro",
    "stardust-2.5-turbo",
    "stardust-2.5-lite",
)

private val HEARING_PROVIDERS = listOf(
    "",
    "aliyun",
    "aliyun_streaming",
    "bytedance",
    "bytedance_streaming",
    "baidu",
    "qcloud",
    "jdcloud",
    "http",
)

private val SPEECH_EMOTIONS = listOf(
    "neutral",
    "happy",
    "sad",
    "angry",
    "surprised",
    "fearful",
    "disgusted",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComplexSessionConfigScreen(
    settingsViewModel: SettingsViewModel,
    ttsViewModel: TtsViewModel,
    onNavigateBack: () -> Unit,
) {
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var editState by remember(settings.advancedSession) {
        mutableStateOf(settings.advancedSession)
    }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_session_config)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showRestoreDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = stringResource(R.string.restore_recommended_defaults),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = {
                        val err = validateAdvancedSession(editState)
                        if (err != null) {
                            scope.launch { snackbar.showSnackbar(err) }
                        } else {
                            scope.launch {
                                settingsViewModel.persistAdvancedSession(editState)
                                onNavigateBack()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            ModelCard(editState = editState, onChange = { editState = it })
            Spacer(modifier = Modifier.height(Spacing.md))
            SpeechCard(
                editState = editState,
                onChange = { editState = it },
                onPickVoice = { showVoiceSheet = true },
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            HearingCard(editState = editState, onChange = { editState = it })
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text(stringResource(R.string.restore_recommended_defaults)) },
            text = { Text(stringResource(R.string.restore_defaults_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        editState = AdvancedSessionSettings()
                        showRestoreDialog = false
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showVoiceSheet) {
        VoiceSelectorSheet(
            serverUrl = settings.serverUrl,
            ttsViewModel = ttsViewModel,
            currentVoice = editState.speechVoice,
            onVoiceSelected = { sp ->
                editState = editState.copy(speechVoice = sp.voice)
            },
            onDismiss = { showVoiceSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelCard(
    editState: AdvancedSessionSettings,
    onChange: (AdvancedSessionSettings) -> Unit,
) {
    AppSectionCard(title = stringResource(R.string.model_config)) {
        DropdownField(
            label = stringResource(R.string.model_provider),
            value = editState.modelProvider,
            options = MODEL_PROVIDERS,
            onSelect = { p ->
                val nextName = if (p == "tiwater" && editState.modelName !in TIWATER_MODEL_NAMES) {
                    TIWATER_MODEL_NAMES[3]
                } else {
                    editState.modelName
                }
                onChange(editState.copy(modelProvider = p, modelName = nextName))
            },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        if (editState.modelProvider == "tiwater") {
            DropdownField(
                label = stringResource(R.string.model_name),
                value = editState.modelName,
                options = TIWATER_MODEL_NAMES,
                onSelect = { n -> onChange(editState.copy(modelName = n)) },
            )
        } else {
            AppTextField(
                value = editState.modelName,
                onValueChange = { onChange(editState.copy(modelName = it)) },
                label = stringResource(R.string.model_name),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.model_modalities) + ": text, audio",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AppTextField(
            value = editState.instructions,
            onValueChange = { onChange(editState.copy(instructions = it)) },
            label = stringResource(R.string.system_instructions),
            placeholder = stringResource(R.string.system_instructions_hint),
            singleLine = false,
            minLines = 4,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AppLabeledSlider(
            label = stringResource(R.string.temperature),
            value = editState.temperature.toFloat(),
            onValueChange = { onChange(editState.copy(temperature = it.toDouble().coerceIn(0.01, 1.0))) },
            valueRange = 0.01f..1f,
            displayValue = "%.2f".format(editState.temperature),
            rangeStart = "0.01",
            rangeEnd = "1.00",
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppLabeledSlider(
            label = stringResource(R.string.top_p),
            value = editState.topP.toFloat(),
            onValueChange = { onChange(editState.copy(topP = it.toDouble().coerceIn(0.0, 1.0))) },
            valueRange = 0f..1f,
            displayValue = "%.2f".format(editState.topP),
            rangeStart = "0.00",
            rangeEnd = "1.00",
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppIntField(
            label = stringResource(R.string.top_k),
            value = editState.topK,
            onChange = { onChange(editState.copy(topK = it)) },
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppIntField(
            label = stringResource(R.string.max_tokens),
            value = editState.maxResponseOutputTokens,
            onChange = { onChange(editState.copy(maxResponseOutputTokens = it)) },
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppIntField(
            label = stringResource(R.string.history_length),
            value = editState.historyConversationLength,
            onChange = { onChange(editState.copy(historyConversationLength = it)) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeechCard(
    editState: AdvancedSessionSettings,
    onChange: (AdvancedSessionSettings) -> Unit,
    onPickVoice: () -> Unit,
) {
    AppSectionCard(title = stringResource(R.string.speech_config)) {
        AppTextField(
            value = editState.speechVoice,
            onValueChange = { onChange(editState.copy(speechVoice = it)) },
            label = stringResource(R.string.voice_id),
            trailingIcon = {
                IconButton(onClick = onPickVoice) {
                    Icon(
                        imageVector = Icons.Outlined.RecordVoiceOver,
                        contentDescription = stringResource(R.string.select_voice),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.output_audio_format) + ": pcm16",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        DropdownField(
            label = stringResource(R.string.emotion),
            value = editState.speechEmotion,
            options = SPEECH_EMOTIONS,
            onSelect = { e -> onChange(editState.copy(speechEmotion = e)) },
        )
        Text(
            text = stringResource(R.string.emotion_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AppLabeledSlider(
            label = stringResource(R.string.speed_ratio),
            value = editState.speechSpeedRatio.toFloat(),
            onValueChange = { onChange(editState.copy(speechSpeedRatio = it.toInt().coerceIn(1, 100))) },
            valueRange = 1f..100f,
            displayValue = editState.speechSpeedRatio.toString(),
            rangeStart = "1",
            rangeEnd = "100",
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppLabeledSlider(
            label = stringResource(R.string.pitch_ratio),
            value = editState.speechPitchRatio.toFloat(),
            onValueChange = { onChange(editState.copy(speechPitchRatio = it.toInt().coerceIn(1, 100))) },
            valueRange = 1f..100f,
            displayValue = editState.speechPitchRatio.toString(),
            rangeStart = "1",
            rangeEnd = "100",
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppLabeledSlider(
            label = stringResource(R.string.volume_ratio),
            value = editState.speechVolumeRatio.toFloat(),
            onValueChange = { onChange(editState.copy(speechVolumeRatio = it.toInt().coerceIn(1, 100))) },
            valueRange = 1f..100f,
            displayValue = editState.speechVolumeRatio.toString(),
            rangeStart = "1",
            rangeEnd = "100",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HearingCard(
    editState: AdvancedSessionSettings,
    onChange: (AdvancedSessionSettings) -> Unit,
) {
    AppSectionCard(title = stringResource(R.string.hearing_config)) {
        Text(
            text = stringResource(R.string.input_audio_format) + ": pcm16",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        DropdownField(
            label = stringResource(R.string.hearing_provider),
            value = editState.hearingProvider.ifBlank { stringResource(R.string.all_option) },
            options = HEARING_PROVIDERS,
            displayMapper = { it.ifBlank { stringResource(R.string.all_option) } },
            onSelect = { p -> onChange(editState.copy(hearingProvider = p)) },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AppIntField(
            label = stringResource(R.string.prefix_padding_ms),
            value = editState.hearingPrefixPaddingMs,
            onChange = { onChange(editState.copy(hearingPrefixPaddingMs = it)) },
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        AppIntField(
            label = stringResource(R.string.silence_duration_ms),
            value = editState.hearingSilenceDurationMs,
            onChange = { onChange(editState.copy(hearingSilenceDurationMs = it)) },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        AppLabeledSlider(
            label = stringResource(R.string.sensitivity),
            value = editState.hearingSensitivity.toFloat(),
            onValueChange = { onChange(editState.copy(hearingSensitivity = it.toDouble().coerceIn(0.0, 1.0))) },
            valueRange = 0f..1f,
            displayValue = "%.2f".format(editState.hearingSensitivity),
            rangeStart = "0.00",
            rangeEnd = "1.00",
        )
    }
}

@Composable
private fun AppIntField(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    AppTextField(
        value = text,
        onValueChange = { t ->
            text = t
            t.toIntOrNull()?.let(onChange)
        },
        label = label,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    displayMapper: @Composable (String) -> String = { it },
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = displayMapper(value)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            readOnly = true,
            value = displayValue,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            shape = RoundedCornerShape(12.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            for (option in options) {
                val optionDisplay = displayMapper(option)
                DropdownMenuItem(
                    text = { Text(optionDisplay) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
