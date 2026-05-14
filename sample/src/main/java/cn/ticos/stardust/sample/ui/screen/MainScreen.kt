package cn.ticos.stardust.sample.ui.screen

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.ticos.stardust.sample.R
import cn.ticos.stardust.sample.model.SessionConfigMode
import cn.ticos.stardust.sample.ui.component.PageIndicator
import cn.ticos.stardust.sample.ui.component.PttButton
import cn.ticos.stardust.sample.ui.component.PttModeCheckbox
import cn.ticos.stardust.sample.ui.component.StatusText
import cn.ticos.stardust.sample.ui.component.VisionFpsSelector
import cn.ticos.stardust.sample.ui.component.VisionModeCheckbox
import cn.ticos.stardust.sample.ui.component.VisionResultPanel
import cn.ticos.stardust.sample.ui.component.VoiceHub
import cn.ticos.stardust.sample.ui.theme.AppColors
import cn.ticos.stardust.sample.util.rememberCameraPermissionState
import cn.ticos.stardust.sample.util.rememberRecordAudioPermissionState
import cn.ticos.stardust.sample.viewmodel.VoiceViewModel
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    voiceViewModel: VoiceViewModel,
    onOpenSettings: () -> Unit,
    onOpenConversationInfo: () -> Unit,
) {
    val uiState by voiceViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var agentNameExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, voiceViewModel) {
        voiceViewModel.setCameraLifecycleOwner(lifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> voiceViewModel.onAppBackground()
                Lifecycle.Event.ON_START -> voiceViewModel.onAppForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            voiceViewModel.setCameraLifecycleOwner(null)
            voiceViewModel.setCameraSurfaceProvider(null)
        }
    }

    val openAppDetails: () -> Unit = {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    val permission = rememberRecordAudioPermissionState { granted ->
        if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.permission_denied),
                    actionLabel = context.getString(R.string.open_app_settings),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openAppDetails()
                }
            }
        }
    }

    val cameraPermission = rememberCameraPermissionState { granted ->
        if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.camera_permission_denied),
                    actionLabel = context.getString(R.string.open_app_settings),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openAppDetails()
                }
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg,
            withDismissAction = true,
        )
    }

    Scaffold(
        containerColor = if (uiState.visionSessionActive && uiState.isConnected) Color.Black else AppColors.Background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Camera Preview Background
            if (uiState.visionSessionActive && uiState.isConnected) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }.also {
                            voiceViewModel.setCameraSurfaceProvider(it.surfaceProvider)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay for better contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                )
            }

            // Main Content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Normal Top Bar – left text truncated, right buttons
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { agentNameExpanded = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (uiState.isConfigured) AppColors.Blue600 else AppColors.Gray400,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                !uiState.isConfigured -> stringResource(R.string.not_activated)
                                uiState.sessionConfigMode == SessionConfigMode.AgentId ->
                                    stringResource(R.string.agent_id_top_bar_format, uiState.agentName)
                                else -> uiState.agentName
                            },
                            color = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { voiceViewModel.toggleLanguage() },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (uiState.visionSessionActive && uiState.isConnected) Color.White.copy(alpha = 0.2f) else AppColors.Gray50,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Text(
                                text = if (uiState.language == "zh") "EN" else "中",
                                fontSize = if (uiState.language == "zh") 14.sp else 18.sp,
                                color = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(
                            onClick = onOpenConversationInfo,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (uiState.visionSessionActive && uiState.isConnected) Color.White.copy(alpha = 0.2f) else AppColors.Gray50,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.conversation_info),
                                tint = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600,
                            )
                        }
                        IconButton(
                            onClick = { voiceViewModel.toggleVisionCamera() },
                            enabled = uiState.canSwitchVisionCamera,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (uiState.visionSessionActive && uiState.isConnected) Color.White.copy(alpha = 0.2f) else AppColors.Gray50,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                if (uiState.visionUseBackCamera) Icons.Filled.CameraRear else Icons.Filled.CameraFront,
                                contentDescription = stringResource(
                                    if (uiState.canSwitchVisionCamera) {
                                        R.string.vision_switch_camera
                                    } else {
                                        R.string.vision_switch_camera_unavailable
                                    },
                                ),
                                tint = if (uiState.canSwitchVisionCamera) {
                                    if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600
                                } else {
                                    AppColors.Gray400
                                },
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (uiState.visionSessionActive && uiState.isConnected) Color.White.copy(alpha = 0.2f) else AppColors.Gray50,
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600
                            )
                        }
                    }
                }

                // Orb Button - Fixed in the physical center of the screen
                VoiceHub(
                    modifier = Modifier.align(Alignment.Center),
                    phase = uiState.phase,
                    isConfigured = uiState.isConfigured,
                    onClick = {
                        if (uiState.isConfigured) {
                            val needMic = !permission.hasPermission
                            val needCam = uiState.visionModeEnabled && !cameraPermission.hasPermission
                            when {
                                needMic -> permission.requestPermission()
                                needCam -> cameraPermission.requestPermission()
                                else -> voiceViewModel.onOrbClicked()
                            }
                        }
                    }
                )

                // Bottom controls and information
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (uiState.statusResId != R.string.status_ready) {
                        StatusText(
                            text = stringResource(uiState.statusResId),
                            modifier = Modifier.padding(horizontal = 32.dp),
                            color = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600
                        )
                    }

                    if (!uiState.isConnected) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PttModeCheckbox(
                                    checked = uiState.pttModeEnabled,
                                    onCheckedChange = { voiceViewModel.togglePttMode(it) },
                                )
                                VisionModeCheckbox(
                                    checked = uiState.visionModeEnabled,
                                    onCheckedChange = { voiceViewModel.toggleVisionMode(it) },
                                )
                            }
                            if (uiState.visionModeEnabled) {
                                VisionFpsSelector(
                                    selectedFps = uiState.visionFps,
                                    onFpsSelected = { voiceViewModel.setVisionFps(it) },
                                )
                            }
                        }
                    }

                    if (uiState.visionSessionActive && uiState.isConnected) {
                        VisionResultPanel(
                            results = uiState.visionResults,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (!uiState.isConfigured) {
                        Text(
                            text = stringResource(R.string.config_instruction),
                            fontSize = 16.sp,
                            color = AppColors.Gray400,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    if (uiState.pttSessionActive && uiState.isConnected) {
                        PttButton(
                            isPressed = uiState.pttPressed,
                            audioLevel = uiState.audioLevel,
                            onPressDown = {
                                val needMic = !permission.hasPermission
                                val needCam = uiState.visionSessionActive && !cameraPermission.hasPermission
                                when {
                                    needMic -> permission.requestPermission()
                                    needCam -> cameraPermission.requestPermission()
                                    else -> voiceViewModel.pttPressDown()
                                }
                            },
                            onPressUp = { voiceViewModel.pttPressUp() },
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }

                    PageIndicator(
                        count = 5,
                        selectedIndex = 2,
                    )
                }
            }

            // Expanded agent name overlay – declared OUTSIDE the padded Box to fill full screen width.
            if (agentNameExpanded) {
                val bgColor = if (uiState.visionSessionActive && uiState.isConnected)
                    Color.Black else MaterialTheme.colorScheme.background
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    color = bgColor,
                    onClick = { agentNameExpanded = false }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp)
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Row(
                            modifier = Modifier.height(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (uiState.isConfigured) AppColors.Blue600 else AppColors.Gray400,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    !uiState.isConfigured -> stringResource(R.string.not_activated)
                                    uiState.sessionConfigMode == SessionConfigMode.AgentId ->
                                        stringResource(R.string.agent_id_top_bar_format, uiState.agentName)
                                    else -> uiState.agentName
                                },
                                color = if (uiState.visionSessionActive && uiState.isConnected) Color.White else AppColors.Gray600,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
