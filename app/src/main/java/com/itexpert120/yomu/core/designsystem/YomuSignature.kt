package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/*
 * The shared Stack vocabulary every screen is built from:
 *
 *  - Corners: one role-based grammar ([YomuShapeTokens]) — cover 4, row/inset 16, card on the desk
 *    24, sheet top 32, pills full.
 *  - Surfaces: the grey desk, sheet-coloured cards (radius 24) on it, raised rows/insets inside a
 *    sheet. No tonal ladder, no borders, no elevation (covers and floating notices carry one static
 *    shadow).
 *  - Ink: primary text and the primary action; colour comes from covers and reading themes only.
 *  - Headers: one section header ([YomuSectionHeader]) and one sheet title row ([YomuSheetHeader]).
 */

/** Width ÷ height of every book cover in the app. */
const val YomuBookCoverAspectRatio: Float = 1f / 1.5f

/** Role-based corner grammar. Prefer these over literal `RoundedCornerShape`s in feature code. */
object YomuShapeTokens {
    /** Book covers, wherever they appear (4dp, with one static shadow). */
    val cover: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.extraSmall

    /** Cards on the desk, contained panes, dialogs (24dp). */
    val hero: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.large

    /** Rows and insets inside a sheet (16dp). */
    val tile: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.medium

    /** Small inline containers: thumbnails, previews inside a row. */
    val inset: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.small

    /** Chips, value readouts, status pills. */
    val pill: Shape = CircleShape
}

/**
 * Kept for source compatibility. Stack has no decorative badge shapes: [YomuShapeBadge] draws a
 * plain circle whatever shape is asked for.
 */
enum class YomuBadgeShape(internal val polygon: RoundedPolygon) {
    Cookie(MaterialShapes.Cookie12Sided),
    Scallop(MaterialShapes.Cookie9Sided),
    Sunny(MaterialShapes.Sunny),
    Clover(MaterialShapes.Clover4Leaf),
    Burst(MaterialShapes.SoftBurst),
}

enum class YomuBadgeTone { Primary, Secondary, Tertiary, Neutral, Error, Solid }

/**
 * An icon in a raised circle (Stack has no badge shapes; [shape] is ignored). [YomuBadgeTone.Solid]
 * is an ink circle, [YomuBadgeTone.Error] tints the icon danger. [content] replaces the icon for
 * custom artwork (e.g. the app logo).
 */
@Composable
fun YomuShapeBadge(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    shape: YomuBadgeShape = YomuBadgeShape.Cookie,
    tone: YomuBadgeTone = YomuBadgeTone.Primary,
    content: (@Composable () -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val (container, onContainer) = when (tone) {
        YomuBadgeTone.Solid -> stack.ink to stack.onInk
        YomuBadgeTone.Error -> stack.raised to stack.danger
        YomuBadgeTone.Neutral -> stack.raised to stack.muted
        else -> stack.raised to stack.ink
    }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            when {
                content != null -> content()
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.44f))
            }
        }
    }
}

/**
 * The one empty / no-results / error state, drawing-free: a large muted symbol (48dp) — or, with
 * [coverOutline], a cover-shaped dashed outline — then the title (20/600), an optional body (15,
 * muted) and the action(s), usually one primary [YomuButton]. [compact] suits sheets and panes.
 * [shape] and [tone] are ignored (Stack has no badges).
 */
@Composable
fun YomuEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    shape: YomuBadgeShape = YomuBadgeShape.Clover,
    tone: YomuBadgeTone = YomuBadgeTone.Secondary,
    compact: Boolean = false,
    coverOutline: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YomuTheme.space.lg, vertical = if (compact) YomuTheme.space.lg else YomuTheme.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (coverOutline) {
            YomuCoverOutline(Modifier.size(width = if (compact) 48.dp else 56.dp, height = if (compact) 72.dp else 84.dp))
        } else {
            Icon(icon.thin(), contentDescription = null, tint = if (tone == YomuBadgeTone.Error) stack.danger else stack.faint, modifier = Modifier.size(if (compact) 40.dp else 48.dp))
        }
        Text(
            text = title,
            modifier = Modifier.padding(top = YomuTheme.space.md).semantics { heading() },
            style = if (compact) StackType.SheetTitle else StackType.DialogTitle,
            color = stack.ink,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                modifier = Modifier.padding(top = 6.dp).widthIn(max = 360.dp),
                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = stack.muted,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier.padding(top = YomuTheme.space.lg),
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** An empty state's large symbol is drawn at weight 200 where a thin cut exists. */
private fun ImageVector.thin(): ImageVector = when (this) {
    StackIcons.AutoStories -> StackIcons.AutoStoriesThin
    StackIcons.Book -> StackIcons.BookThin
    StackIcons.Bookmarks -> StackIcons.BookmarksThin
    StackIcons.BorderColor -> StackIcons.BorderColorThin
    StackIcons.CloudOff -> StackIcons.CloudOffThin
    StackIcons.CollectionsBookmark -> StackIcons.CollectionsBookmarkThin
    StackIcons.EditOff -> StackIcons.EditOffThin
    StackIcons.HourglassEmpty -> StackIcons.HourglassEmptyThin
    StackIcons.MatchCase -> StackIcons.MatchCaseThin
    StackIcons.NoPhotography -> StackIcons.NoPhotographyThin
    StackIcons.SearchOff -> StackIcons.SearchOffThin
    StackIcons.Toc -> StackIcons.TocThin
    else -> this
}

/** A cover-shaped dashed outline: the "no book here" mark in empty states. */
@Composable
fun YomuCoverOutline(modifier: Modifier = Modifier) {
    val color = YomuTheme.stack.faint.copy(alpha = 0.6f)
    Box(
        modifier.drawBehind {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        },
    )
}

/**
 * The page-level section header: Heading 15/600 in ink (sentence case), an optional muted
 * supporting line and a trailing action. (Inside sheets and dense panes use [YomuSectionLabel].)
 */
@Composable
fun YomuSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = YomuTheme.space.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                color = YomuTheme.stack.ink,
                style = StackType.Heading,
            )
            if (subtitle != null) {
                Text(text = subtitle, color = YomuTheme.stack.muted, style = StackType.Caption)
            }
        }
        trailing?.invoke()
    }
}

/**
 * Card tones. Stack has no tonal ladder: [Group], [Hero] and [HeroAlt] are all the sheet-coloured
 * card on the desk; [Raised] is the raised inset for a card that sits inside a sheet.
 */
enum class YomuCardTone { Group, Raised, Hero, HeroAlt }

/**
 * A card: sheet-coloured, radius 24, no border or elevation — or, with [YomuCardTone.Raised], a
 * raised inset (radius 16) for use inside a sheet.
 */
@Composable
fun YomuCard(
    modifier: Modifier = Modifier,
    tone: YomuCardTone = YomuCardTone.Group,
    shape: Shape = if (tone == YomuCardTone.Raised) YomuShapeTokens.tile else YomuShapeTokens.hero,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(YomuTheme.space.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val stack = YomuTheme.stack
    val container = if (tone == YomuCardTone.Raised) stack.raised else stack.sheet
    CompositionLocalProvider(LocalContentColor provides stack.ink) {
        Column(
            modifier = modifier
                .clip(shape)
                .then(if (onClick != null) Modifier.yomuPressable(onClick = onClick, shape = shape) else Modifier)
                .background(container)
                .then(if (stack.flat && tone != YomuCardTone.Raised) Modifier.border(1.dp, stack.rule, shape) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

enum class YomuNoticeTone { Neutral, Accent, Error, Floating }

/**
 * A notice. [YomuNoticeTone.Floating] is Stack's snackbar replacement: an ink card (radius 16, soft
 * static shadow) with on-ink text and the optional action as an on-ink 600 label on the right; it
 * carries no icon. The other tones are inline strips on the raised colour ([YomuNoticeTone.Error]
 * tints the icon danger). [loading] adds the 2dp indeterminate line along the bottom; [leading]
 * overrides the icon.
 */
@Composable
fun YomuNotice(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: YomuNoticeTone = YomuNoticeTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    loading: Boolean = false,
) {
    val stack = YomuTheme.stack
    val floating = tone == YomuNoticeTone.Floating
    val container = if (floating) stack.ink else stack.raised
    val onContainer = if (floating) stack.onInk else stack.ink
    val iconTint = when (tone) {
        YomuNoticeTone.Floating -> stack.onInk
        YomuNoticeTone.Error -> stack.danger
        else -> stack.muted
    }
    // A floating notice sits about 16dp above the bottom edge: concentric with the display's corner.
    val floatingCorner = yomuConcentricCorner(gap = 16.dp, minimum = 16.dp)
    val shape = if (tone == YomuNoticeTone.Floating) RoundedCornerShape(floatingCorner) else YomuShapeTokens.tile
    Column(
        modifier = modifier
            .then(if (floating) Modifier.dropShadow(shape, NoticeShadow) else Modifier)
            .clip(shape)
            .background(container)
            .semantics { if (floating) liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 52.dp)
                .padding(
                    start = YomuTheme.space.md,
                    end = if (actionLabel != null) 6.dp else YomuTheme.space.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            CompositionLocalProvider(LocalContentColor provides iconTint) {
                when {
                    leading != null -> leading()
                    icon != null && !floating -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                style = if (floating) StackType.Notice else MaterialTheme.typography.bodyMedium,
                color = onContainer,
            )
            if (actionLabel != null && onAction != null) {
                YomuPill(
                    onClick = onAction,
                    container = Color.Transparent,
                    height = 44.dp,
                    horizontalPadding = 14.dp,
                ) {
                    Text(actionLabel, style = StackType.Notice.copy(fontWeight = FontWeight.SemiBold), color = onContainer)
                }
            }
        }
        if (loading) YomuLoadingLine(color = onContainer)
    }
}

private val NoticeShadow = Shadow(radius = 24.dp, spread = (-10).dp, offset = DpOffset(0.dp, 8.dp), color = Color(0xFF16191C).copy(alpha = 0.45f))

/**
 * The one confirmation / message dialog: a centred sheet-coloured card (radius 24), title 20/600,
 * body 15, actions right-aligned — the dismiss as an ink text button, the confirm as an ink pill
 * (a danger pill when [destructive]). The screen behind dims to 80%. Stack dialogs carry no icon;
 * [icon] is kept for source compatibility and ignored.
 */
@Composable
fun YomuDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector? = null,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val backPreview = rememberYomuBackPreviewState()
    // The platform's default dialog width leaves ~54dp sides on a phone; Stack's card sits 24dp in.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        YomuDialogWindowDim()
        // Registered from inside the dialog so it uses the dialog window's back dispatcher.
        YomuBackPreviewHandler(backPreview, onBack = onDismiss)
        YomuDialogCard(
            modifier = modifier.padding(horizontal = YomuTheme.space.lg).yomuBackPreview(backPreview),
            title = title,
            text = text,
            confirmLabel = confirmLabel,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            dismissLabel = dismissLabel,
            destructive = destructive,
            confirmEnabled = confirmEnabled,
            content = content,
        )
    }
}

/** The dialog's card on its own (no window), for previews and the dev gallery. */
@Composable
fun YomuDialogCard(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismissLabel: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .clip(YomuShapeTokens.hero)
            .background(stack.sheet)
            .then(if (stack.flat) Modifier.border(1.dp, stack.rule, YomuShapeTokens.hero) else Modifier)
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
    ) {
        Text(
            text = title,
            style = StackType.DialogTitle,
            color = stack.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (text != null || content != null) {
            Column(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
            ) {
                if (text != null) Text(text, style = MaterialTheme.typography.bodyLarge, color = stack.body)
                CompositionLocalProvider(LocalContentColor provides stack.body) { content?.invoke(this) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dismissLabel != null) {
                YomuButton(dismissLabel, onClick = onDismiss, emphasis = YomuButtonEmphasis.Ghost, size = YomuButtonSize.Small)
            }
            YomuButton(
                confirmLabel,
                onClick = onConfirm,
                enabled = confirmEnabled,
                emphasis = if (destructive) YomuButtonEmphasis.DangerFilled else YomuButtonEmphasis.Primary,
                size = YomuButtonSize.Small,
            )
        }
    }
}

/**
 * How the enclosing [YomuBottomSheet] is presented; lets shared headers adapt. [Side] is the side
 * sheet it becomes from medium widths; [Dialog] is kept for centred dialog-like surfaces.
 */
enum class YomuSheetPresentation { Sheet, Dialog, Side }

val LocalYomuSheetPresentation = staticCompositionLocalOf { YomuSheetPresentation.Sheet }

/** Lets a row reach [amount] past its parent's padding on both sides (for edge-aligned icon buttons). */
internal fun Modifier.bleedHorizontally(amount: Dp): Modifier = layout { measurable, constraints ->
    val extra = amount.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra * 2,
            maxWidth = constraints.maxWidth + extra * 2,
        ),
    )
    layout(placeable.width - extra * 2, placeable.height) { placeable.place(-extra, 0) }
}
