package com.itexpert120.yomu.feature.bookdetails

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.StackColors
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBackPreviewHandler
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.rememberYomuBackPreviewState
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuBackPreview
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Full-screen, dismissable cover viewer with close + save-to-gallery affordances. */
// Tints the dialogue's system bars transparent via the (deprecated) window setters so the backdrop
// bleeds edge-to-edge; the replacement API doesn't apply to a Dialogue window the same way.
@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoverViewerDialog(
    coverPath: String,
    title: String,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val statusTop =
        WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val space = YomuTheme.space
    val animate = yomuAnimationsEnabled()
    // The viewer animates in and out inside its dialog window, so every dismissal path starts the
    // exit and the caller is only told to drop the dialog once that has finished.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visibleState.targetState = false }
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) onClose()
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Let the dialog draw edge-to-edge (behind the status/nav bars) and tint those bars
        // transparent so the black backdrop bleeds under them instead of leaving padding.
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = window.attributes
                attributes.layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attributes
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        val backPreview = rememberYomuBackPreviewState()
        YomuBackPreviewHandler(backPreview, onBack = close)
        val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
        // A dark room whatever the theme: Stack's dark desk, dimmed, so the cover's colours carry.
        val room = StackColors.Dark
        // The backdrop fades; the cover and its actions materialize in place on top of it.
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (animate) fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) else EnterTransition.None,
            exit = if (animate) fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) else ExitTransition.None,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(room.desk.copy(alpha = 0.97f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = close,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .animateEnterExit(enter = yomuPopupEnter(), exit = yomuPopupExit())
                        .yomuBackPreview(backPreview),
                    contentAlignment = Alignment.Center,
                ) {
                    // Pinch to zoom, drag to pan, double-tap to toggle. Scale is clamped; panning
                    // is only allowed while zoomed in (and resets when returning to 1×).
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offset by remember { mutableStateOf(Offset.Zero) }
                    AsyncImage(
                        model = File(coverPath),
                        contentDescription = "$title, cover",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = space.lg,
                                end = space.lg,
                                top = statusTop + 72.dp,
                                bottom = navBottom + 96.dp,
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { close() },
                                    onDoubleTap = {
                                        if (scale > 1f) {
                                            scale = 1f
                                            offset = Offset.Zero
                                        } else {
                                            scale = 2.5f
                                        }
                                    },
                                )
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    offset = if (scale > 1f) offset + pan else Offset.Zero
                                }
                            }
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = statusTop + space.xs, start = space.xs)
                            .size(48.dp)
                            .yomuPressable(onClick = close, shape = CircleShape)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .semantics { contentDescription = "Close cover" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(StackIcons.Close, contentDescription = null, tint = room.ink)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = navBottom + 32.dp)
                            .height(52.dp)
                            .yomuPressable(onClick = onSave, shape = CircleShape)
                            .clip(CircleShape)
                            .background(room.ink)
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(StackIcons.Download, contentDescription = null, tint = room.onInk)
                        Text("Save to gallery", style = StackType.Label.copy(fontSize = 15.sp), color = room.onInk)
                    }
                }
            }
        }
    }
}

private val galleryExports = CoverExport()

/**
 * Copies [source] into the device's shared Pictures collection via MediaStore, as [baseName] plus
 * the extension of the image's actual format (covers are PNG, WebP, or whatever image the user
 * picked). Works without a runtime permission on API 29+; older versions may fail (caught) if
 * storage permission is absent.
 */
internal suspend fun saveImageToGallery(
    context: Context,
    source: File,
    baseName: String,
): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val format = runCatching { sniffImageFormat(source) }.getOrDefault(GalleryImageFormat.Png)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$baseName.${format.extension}")
        put(MediaStore.Images.Media.MIME_TYPE, format.mime)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Open Reader")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    galleryExports.export(
        create = { resolver.insert(collection, values)?.toString() },
        copy = { destination ->
            checkNotNull(resolver.openOutputStream(android.net.Uri.parse(destination))).use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
        },
        publish = { destination ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(android.net.Uri.parse(destination), published, null, null) > 0
            } else {
                true
            }
        },
        delete = { destination -> resolver.delete(android.net.Uri.parse(destination), null, null) > 0 },
    )
}

private enum class GalleryImageFormat(val mime: String, val extension: String) {
    Png("image/png", "png"),
    Jpeg("image/jpeg", "jpg"),
    Webp("image/webp", "webp"),
}

/** Identifies the image by its magic bytes; anything unrecognised is treated as PNG. */
private fun sniffImageFormat(file: File): GalleryImageFormat {
    val header = ByteArray(12)
    val read = file.inputStream().use { it.read(header) }
    return when {
        read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte() -> GalleryImageFormat.Jpeg
        read >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP" -> GalleryImageFormat.Webp
        else -> GalleryImageFormat.Png
    }
}
