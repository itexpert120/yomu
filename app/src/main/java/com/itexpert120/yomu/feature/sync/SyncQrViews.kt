package com.itexpert120.yomu.feature.sync

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuLoadingState
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * The sync words as a QR code. Always dark modules on white, whatever the theme, since that is
 * what scanners read reliably.
 */
@Composable
internal fun SyncQrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            0,
            0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(YomuShapeTokens.hero)
            .background(Color.White)
            // Doubles as the quiet zone, and keeps the corner finders clear of the hero radius.
            .padding(YomuTheme.space.lg),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.width / matrix.width
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) {
                        // A hair of overlap avoids hairline gaps between modules at fractional sizes.
                        drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                    }
                }
            }
        }
    }
}

/** Camera preview that reports the first QR code it reads. Asks for the camera permission itself. */
@Composable
internal fun SyncQrScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by remember { mutableStateOf(false) }
    var cameraFailed by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        denied = !ok
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    // Coming back from system settings: pick up a permission granted there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            granted = true
            denied = false
        }
    }

    val showDenied = denied && !granted
    val showMessage = showDenied || cameraFailed
    Box(
        modifier = modifier
            .fillMaxWidth()
            // A message sizes to its content so large text never clips in the square.
            .then(if (showMessage) Modifier else Modifier.aspectRatio(1f))
            .clip(YomuShapeTokens.hero)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            cameraFailed -> YomuEmptyState(
                icon = Icons.Rounded.NoPhotography,
                title = "Camera unavailable",
                body = "The camera couldn't start on this device. Type the words instead.",
                compact = true,
            )

            granted -> {
                CameraQrPreview(onScanned, onUnavailable = { cameraFailed = true })
                ScanReticle(Modifier.fillMaxSize())
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(YomuTheme.space.md),
                    shape = YomuShapeTokens.pill,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ) {
                    Text(
                        text = "Point the camera at the code on your other device",
                        modifier = Modifier.padding(horizontal = YomuTheme.space.sm, vertical = YomuTheme.space.xxs),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            showDenied -> YomuEmptyState(
                icon = Icons.Rounded.NoPhotography,
                title = "Camera access is off",
                body = "Allow camera access in system settings to scan, or type the words instead.",
                compact = true,
            ) {
                YomuButton(
                    text = "Open settings",
                    onClick = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            }

            else -> YomuLoadingState(message = "Waiting for camera access…")
        }
    }
}

/** A rounded frame that shows where to place the code. White, since it sits on the camera image. */
@Composable
private fun ScanReticle(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = size.minDimension * 0.62f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f),
            size = Size(side, side),
            cornerRadius = CornerRadius(24.dp.toPx()),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun CameraQrPreview(onScanned: (String) -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnScanned by rememberUpdatedState(onScanned)
    val latestOnUnavailable by rememberUpdatedState(onUnavailable)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val decodeExecutor = Executors.newSingleThreadExecutor()
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            // Report each different code once; the same code stays in view for many frames.
            var last: String? = null
            analysis.setAnalyzer(
                decodeExecutor,
                QrFrameAnalyzer { value ->
                    mainExecutor.execute {
                        if (value != last) {
                            last = value
                            latestOnScanned(value)
                        }
                    }
                },
            )
            try {
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                awaitCancellation()
            } finally {
                provider.unbind(preview, analysis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No usable camera (none on the device, in use, or disabled by policy): typing still works.
            latestOnUnavailable()
        } finally {
            decodeExecutor.shutdown()
        }
    }

    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
}

/**
 * Reads QR codes from camera frames with ZXing. Only the luminance plane is needed, and QR
 * decoding is rotation-independent, so frames are used as they arrive.
 */
private class QrFrameAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val decoder = QrLuminanceDecoder()
    private var luminance = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val length = buffer.remaining()
            if (luminance.size < length) luminance = ByteArray(length)
            buffer.get(luminance, 0, length)
            decoder.decode(luminance, plane.rowStride, it.width, it.height)?.let(onDecoded)
        }
    }
}

/** Decodes a QR code from an 8-bit luminance image whose rows may be padded to [rowStride]. */
internal class QrLuminanceDecoder {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    fun decode(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        // The row stride is the data width, the image size the crop.
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
