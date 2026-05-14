package cn.ticos.stardust.sample.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.SessionConfigMode
import cn.ticos.stardust.sample.ui.component.AdvancedConfigSummaryCard
import cn.ticos.stardust.sample.ui.component.AppSectionCard
import cn.ticos.stardust.sample.ui.component.AppTextField
import cn.ticos.stardust.sample.ui.component.SessionConfigModeSelector
import cn.ticos.stardust.sample.ui.theme.Spacing
import cn.ticos.stardust.sample.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onEditAdvancedConfig: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var sessionMode by remember(settings.sessionConfigMode) { mutableStateOf(settings.sessionConfigMode) }
    var agentId by remember(settings.agentId) { mutableStateOf(settings.agentId) }
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var terminalSecret by remember(settings.terminalSecret) { mutableStateOf(settings.terminalSecret) }
    var groupId by remember(settings.groupId) { mutableStateOf(settings.groupId) }
    var robotId by remember(settings.robotId) { mutableStateOf(settings.robotId) }
    var autoPlay by remember(settings.autoPlayAudio) { mutableStateOf(settings.autoPlayAudio) }
    var secretVisible by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.config_center),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.config_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = Spacing.lg)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(Spacing.sm))

            AppSectionCard(title = stringResource(R.string.section_connection)) {
                AppTextField(
                    label = stringResource(R.string.server_url_label),
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = stringResource(R.string.server_url_placeholder),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            AppSectionCard(title = stringResource(R.string.section_auth)) {
                AppTextField(
                    label = stringResource(R.string.terminal_secret_label),
                    value = terminalSecret,
                    onValueChange = { terminalSecret = it },
                    placeholder = stringResource(R.string.terminal_secret_placeholder),
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { secretVisible = !secretVisible }) {
                            Icon(
                                imageVector = if (secretVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                AppTextField(
                    label = stringResource(R.string.group_id_label),
                    value = groupId,
                    onValueChange = { groupId = it },
                    placeholder = stringResource(R.string.group_id_placeholder),
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                AppTextField(
                    label = stringResource(R.string.robot_id_label),
                    value = robotId,
                    onValueChange = { robotId = it },
                    placeholder = stringResource(R.string.robot_id_placeholder),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            AppSectionCard(title = stringResource(R.string.section_behavior)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.auto_play_audio),
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(checked = autoPlay, onCheckedChange = { autoPlay = it })
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            AppSectionCard(title = stringResource(R.string.session_config_mode)) {
                SessionConfigModeSelector(
                    currentMode = sessionMode,
                    onModeChange = { newMode ->
                        sessionMode = newMode
                        scope.launch { viewModel.persistSessionConfigMode(newMode) }
                    },
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                if (sessionMode == SessionConfigMode.AgentId) {
                    AppTextField(
                        label = stringResource(R.string.agent_label),
                        value = agentId,
                        onValueChange = { agentId = it },
                        placeholder = stringResource(R.string.agent_id_placeholder),
                    )
                } else {
                    AdvancedConfigSummaryCard(
                        advanced = settings.advancedSession,
                        onEditClick = onEditAdvancedConfig,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .padding(top = 2.dp, end = Spacing.sm)
                            .size(18.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.security_tip_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = stringResource(R.string.security_tip_content),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            Button(
                onClick = {
                    val err = validateSettings(sessionMode, agentId, serverUrl, terminalSecret, groupId, robotId)
                    if (err != null) {
                        val errorMsg = when (err) {
                            "EMPTY_AGENT_ID"       -> context.getString(R.string.error_agent_id_empty)
                            "INVALID_URL"          -> context.getString(R.string.error_invalid_url)
                            "MISSING_CREDENTIALS"  -> context.getString(R.string.error_missing_credentials)
                            else                   -> err
                        }
                        scope.launch { snackbar.showSnackbar(errorMsg) }
                    } else {
                        scope.launch {
                            viewModel.persistAll(
                                agentId = agentId,
                                serverUrl = serverUrl,
                                terminalSecret = terminalSecret,
                                groupId = groupId,
                                robotId = robotId,
                                autoPlayAudio = autoPlay,
                                sessionConfigMode = sessionMode,
                            )
                            onNavigateBack()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.save_changes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

private fun validateSettings(
    mode: SessionConfigMode,
    agentId: String,
    serverUrl: String,
    terminalSecret: String,
    groupId: String,
    robotId: String,
): String? {
    if (!serverUrl.startsWith("wss://") && !serverUrl.startsWith("ws://")) {
        return "INVALID_URL"
    }
    val secretOk = terminalSecret.isNotBlank()
    val pairOk = groupId.isNotBlank() && robotId.isNotBlank()
    if (!secretOk && !pairOk) {
        return "MISSING_CREDENTIALS"
    }
    if (mode == SessionConfigMode.AgentId && agentId.isBlank()) {
        return "EMPTY_AGENT_ID"
    }
    return null
}
