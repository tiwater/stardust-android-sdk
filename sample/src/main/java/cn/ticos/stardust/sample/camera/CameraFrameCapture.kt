package cn.ticos.stardust.sample.camera

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * CameraX [ImageAnalysis] → JPEG bytes；帧率在分析器内按 [fps] 节流。
 */
class CameraFrameCapture(
    private val lifecycleOwner: LifecycleOwner,
    private val context: Context,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _frames = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val frames: SharedFlow<ByteArray> = _frames.asSharedFlow()

    private var provider: ProcessCameraProvider? = null
    private val running = AtomicBoolean(false)
    private var lastEmitNs = 0L

    suspend fun start(
        fps: Int,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
        surfaceProvider: Preview.SurfaceProvider? = null,
    ) = withContext(Dispatchers.Main) {
        if (!running.compareAndSet(false, true)) return@withContext
        lastEmitNs = 0L
        val safeFps = fps.coerceIn(1, 30)
        val intervalNs = 1_000_000_000L / safeFps

        val cameraProvider = suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(appContext)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                ContextCompat.getMainExecutor(appContext),
            )
        }
        provider = cameraProvider
        cameraProvider.unbindAll()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(appContext)) { imageProxy ->
            if (!running.get()) {
                imageProxy.close()
                return@setAnalyzer
            }
            val now = System.nanoTime()
            if (now - lastEmitNs < intervalNs) {
                imageProxy.close()
                return@setAnalyzer
            }
            val rowStride = imageProxy.planes[0].rowStride
            val pixelStride = imageProxy.planes[0].pixelStride
            val width = imageProxy.width
            val height = imageProxy.height
            val dup = imageProxy.planes[0].buffer.duplicate()
            val rowBytes = ByteArray(dup.remaining())
            dup.get(rowBytes)
            imageProxy.close()
            lastEmitNs = System.nanoTime()
            scope.launch {
                try {
                    val jpeg = rgbaBufferToJpeg(rowBytes, rowStride, pixelStride, width, height)
                    if (jpeg != null) {
                        _frames.emit(jpeg)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "JPEG encode failed", t)
                }
            }
        }

        if (surfaceProvider != null) {
            val preview = Preview.Builder().build()
            preview.surfaceProvider = surfaceProvider
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis,
                preview,
            )
        } else {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis,
            )
        }
    }

    fun stop() {
        running.set(false)
        scope.cancel()
        val p = provider
        if (p != null) {
            try {
                ContextCompat.getMainExecutor(appContext).execute { p.unbindAll() }
            } catch (_: Throwable) {
            }
        }
        provider = null
    }

    companion object {
        private const val TAG = "CameraFrameCapture"
    }
}

private fun rgbaBufferToJpeg(
    rowBytes: ByteArray,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    quality: Int = 80,
): ByteArray? {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        if (pixelStride == 4 && rowStride == 4 * width) {
            val wrap = java.nio.ByteBuffer.wrap(rowBytes)
            bitmap.copyPixelsFromBuffer(wrap)
        } else {
            val pixels = IntArray(width * height)
            var out = 0
            for (y in 0 until height) {
                val rowStart = y * rowStride
                for (x in 0 until width) {
                    val i = rowStart + x * pixelStride
                    if (i + 3 >= rowBytes.size) return null
                    val r = rowBytes[i].toInt() and 0xff
                    val g = rowBytes[i + 1].toInt() and 0xff
                    val b = rowBytes[i + 2].toInt() and 0xff
                    val a = rowBytes[i + 3].toInt() and 0xff
                    pixels[out++] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        val stream = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
            return null
        }
        return stream.toByteArray()
    } finally {
        bitmap.recycle()
    }
}
