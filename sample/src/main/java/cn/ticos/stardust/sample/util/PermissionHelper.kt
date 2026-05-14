package cn.ticos.stardust.sample.util

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class RecordAudioPermission(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun rememberRecordAudioPermissionState(
    onPermissionResult: (Boolean) -> Unit = {},
): RecordAudioPermission {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        onPermissionResult(isGranted)
    }
    return RecordAudioPermission(
        hasPermission = granted,
        requestPermission = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
        openAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}

data class CameraPermission(
    val hasPermission: Boolean,
    val requestPermission: () -> Unit,
    val openAppSettings: () -> Unit,
)

@Composable
fun rememberCameraPermissionState(
    onPermissionResult: (Boolean) -> Unit = {},
): CameraPermission {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        onPermissionResult(isGranted)
    }
    return CameraPermission(
        hasPermission = granted,
        requestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        openAppSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        },
    )
}
