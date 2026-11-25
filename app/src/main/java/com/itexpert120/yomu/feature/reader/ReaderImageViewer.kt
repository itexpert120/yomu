package com.itexpert120.yomu.feature.reader

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
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
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.rememberYomuBackPreviewState
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuBackPreview
import com.itexpert120.yomu.core.designsystem.yomuPopupEnter
import com.itexpert120.yomu.core.designsystem.yomuPopupExit
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.reader.ReaderImage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * A picture from the book, full screen in the cover viewer's dark room: pinch or double-tap to zoom,
 * drag to pan while zoomed, swipe down (or tap, or back) to close; Save puts it in Pictures / Open
 * Reader and Share hands it to another app. [message] is the outcome of the last save.
 */
// Tints the dialog's system bars transparent via the (deprecated) window setters, as the cover
// viewer does, so the dark room runs edge to edge.
@Suppress("DEPRECATION")
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderImageViewer(
    image: ReaderImage?,
    message: String?,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    // The last picture keeps showing while the viewer plays its exit.
    var shown by remember { mutableStateOf(image) }
    if (image != null) shown = image
    val picture = shown ?: return
    val visibleState = remember(picture) { MutableTransitionState(false) }
    // Closed from here (a tap, a swipe, back): the exit plays first, then the caller hears of it.
    var closing by remember(picture) { mutableStateOf(false) }
    visibleState.targetState = image != null && !closing
    val currentOnClose by rememberUpdatedState(onClose)
    val close = { closing = true }
    LaunchedEffect(visibleState.isIdle, visibleState.currentState) {
        if (visibleState.isIdle && !visibleState.currentState) {
            if (image != null) currentOnClose()
            shown = null
        }
    }
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    val animate = yomuAnimationsEnabled()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val attributes = window.attributes
                attributes.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = attributes
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        val backPreview = rememberYomuBackPreviewState()
        YomuBackPreviewHandler(backPreview, onBack = close)
        // A dark room whatever the reading theme, so the picture's own colours carry.
        val room = StackColors.Dark
        var scale by remember(picture) { mutableFloatStateOf(1f) }
        var offset by remember(picture) { mutableStateOf(Offset.Zero) }
        // How far a swipe has pulled the picture down to close it.
        var pullDown by remember(picture) { mutableFloatStateOf(0f) }
        var area by remember { mutableStateOf(IntSize.Zero) }
        val scope = rememberCoroutineScope()
        fun clamp(value: Offset, at: Float): Offset {
            val maxX = area.width * (at - 1f) / 2f
            val maxY = area.height * (at - 1f) / 2f
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }
        AnimatedVisibility(
            visibleState = visibleState,
            enter = if (animate) fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) else EnterTransition.None,
            exit = if (animate) fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) else ExitTransition.None,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { area = it }
                    .graphicsLayer {
                        // The room lightens as the picture is pulled away.
                        alpha = 1f - (abs(pullDown) / (size.height * 0.6f)).coerceIn(0f, 0.6f)
                    }
                    .background(room.desk.copy(alpha = 0.97f)),
            ) {
                AsyncImage(
                    model = picture.bytes,
                    contentDescription = picture.description ?: "Picture from the book",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .animateEnterExit(enter = yomuPopupEnter(), exit = yomuPopupExit())
                        .yomuBackPreview(backPreview)
                        .padding(top = statusTop + 64.dp, bottom = navBottom + 104.dp, start = 12.dp, end = 12.dp)
                        .pointerInput(picture) {
                            detectTapGestures(
                                onTap = { close() },
                                onDoubleTap = { at ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = DoubleTapZoom
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        offset = clamp((center - at) * (DoubleTapZoom - 1f), DoubleTapZoom)
                                    }
                                },
                            )
                        }
                        .pointerInput(picture) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var pulling = false
                                do {
                                    val event = awaitPointerEvent()
                                    val fingers = event.changes.count { it.pressed }
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (fingers > 1 || scale > 1f) {
                                        scale = (scale * zoom).coerceIn(1f, MaxZoom)
                                        offset = if (scale > 1f) clamp(offset + pan, scale) else Offset.Zero
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (pan != Offset.Zero && (pulling || abs(pan.y) > abs(pan.x))) {
                                        pulling = true
                                        pullDown = (pullDown + pan.y).coerceAtLeast(0f)
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (pulling) {
                                    if (pullDown > size.height * CloseFraction) {
                                        close()
                                    } else {
                                        val from = pullDown
                                        scope.launch { animate(from, 0f, animationSpec = spatial) { value, _ -> pullDown = value } }
                                    }
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y + pullDown
                        },
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = statusTop + 8.dp, start = 8.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .yomuPressable(onClick = close, shape = CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .semantics { contentDescription = "Close picture" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(StackIcons.Close, contentDescription = null, tint = room.ink)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBottom + 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = message.orEmpty(),
                        color = room.body,
                        style = StackType.BodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ViewerAction("Save", StackIcons.Download, primary = true, onClick = onSave, room = room)
                        ViewerAction("Share", StackIcons.Share, primary = false, onClick = onShare, room = room)
                    }
                }
            }
        }
    }
}

/** The viewer's pills: Save in ink, Share outlined, as Stack's primary and secondary buttons. */
@Composable
private fun ViewerAction(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit, room: StackColors) {
    val content = if (primary) room.onInk else room.ink
    Row(
        modifier = Modifier
            .heightIn(min = 52.dp)
            .height(52.dp)
            .clip(CircleShape)
            .yomuPressable(onClick = onClick, shape = CircleShape)
            .background(if (primary) room.ink else Color.Transparent)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.5.dp, room.track), CircleShape))
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content)
        Text(label, style = StackType.Label.copy(fontSize = 15.sp), color = content)
    }
}

private const val DoubleTapZoom = 2.5f
private const val MaxZoom = 5f

// A pull past this share of the picture's height closes the viewer.
private const val CloseFraction = 0.18f
