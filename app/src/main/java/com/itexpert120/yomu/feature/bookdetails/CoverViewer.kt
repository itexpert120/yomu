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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuBackPreviewHandler
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.rememberYomuBackPreviewState
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuBackPreview
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
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
        // The scrim fades; the cover and its actions materialize in place on top of it.
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (animate) fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) else EnterTransition.None,
            exit = if (animate) fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) else ExitTransition.None,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
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
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(space.lg)
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
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = statusTop + space.xs, end = space.sm),
                    ) {
                        CircleAction(Icons.Rounded.SaveAlt, "Save to gallery", onSave)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = statusTop + space.xs, start = space.sm),
                    ) {
                        CircleAction(Icons.Rounded.Close, "Close", close)
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(imageVector = icon, contentDescription = description)
    }
}

/**
 * Copies [source] into the device's shared Pictures collection via MediaStore. Works without a
 * runtime permission on API 29+; older versions may fail (caught) if storage permission is absent.
 */
private val galleryExports = CoverExport()

internal suspend fun saveImageToGallery(
    context: Context,
    source: File,
    displayName: String,
): Boolean = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
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
