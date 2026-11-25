package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/*
 * The Yomu signature: the handful of decisions that make every screen read as the same app.
 *
 *  - Corners: one role-based grammar ([YomuShapeTokens]) — heroes and panes are extra-large, grouped
 *    content is segmented (large outer corners, tight seams), book covers always share one radius.
 *  - Surfaces: a tonal ladder ([YomuCardTone]) with no borders or shadows — page → group → raised →
 *    hero container.
 *  - Shapes: one small family of Material shape-library badges ([YomuBadgeShape]) marks hero moments
 *    and every empty/error state. Never behind running text.
 *  - Headers: one section header ([YomuSectionHeader]) and one sheet header ([YomuSheetHeader]).
 */

/** Width ÷ height of every book cover in the app. */
const val YomuBookCoverAspectRatio: Float = 1f / 1.5f

/** Role-based corner grammar. Prefer these over literal `RoundedCornerShape`s in feature code. */
object YomuShapeTokens {
    /** Book covers, wherever they appear. */
    val cover: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.medium

    /** Hero cards, contained panes, dialogs. */
    val hero: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.extraLarge

    /** Standalone tiles and cards that sit in a grid. */
    val tile: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.large

    /** Small inline containers: tags, thumbnails, previews inside a row. */
    val inset: Shape
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.shapes.small

    /** Chips, value readouts, status pills. */
    val pill: Shape = androidx.compose.foundation.shape.CircleShape
}

/** The decorative shapes Yomu uses for badges. Kept deliberately small so they stay recognisable. */
enum class YomuBadgeShape(internal val polygon: RoundedPolygon) {
    /** The primary Yomu mark: hero moments (library, about, book cover backdrop). */
    Cookie(MaterialShapes.Cookie12Sided),

    /** Softer scallop for secondary heroes and empty states. */
    Scallop(MaterialShapes.Cookie9Sided),

    /** Time and activity. */
    Sunny(MaterialShapes.Sunny),

    /** Calm, four-lobed: neutral information and "nothing here yet". */
    Clover(MaterialShapes.Clover4Leaf),

    /** Problems and warnings. */
    Burst(MaterialShapes.SoftBurst),
}

enum class YomuBadgeTone { Primary, Secondary, Tertiary, Neutral, Error, Solid }

@Composable
private fun YomuBadgeTone.colors(): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
    when (this@colors) {
        YomuBadgeTone.Primary -> primaryContainer to onPrimaryContainer
        YomuBadgeTone.Secondary -> secondaryContainer to onSecondaryContainer
        YomuBadgeTone.Tertiary -> tertiaryContainer to onTertiaryContainer
        YomuBadgeTone.Neutral -> surfaceContainerHighest to onSurfaceVariant
        YomuBadgeTone.Error -> errorContainer to onErrorContainer
        YomuBadgeTone.Solid -> primary to onPrimary
    }
}

/**
 * An icon set in one of the Yomu badge shapes. The shared mark for hero moments and empty/error
 * states; [content] replaces the icon for custom artwork (e.g. the app logo).
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
    val (container, onContainer) = tone.colors()
    Surface(
        modifier = modifier.size(size),
        shape = shape.polygon.toShape(),
        color = container,
        contentColor = onContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                content != null -> content()
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.44f))
            }
        }
    }
}

/**
 * The one empty / error / "nothing here" state: a shape badge, an emphasized title, optional
 * supporting text and actions. [compact] suits sheets and panes; the default suits whole screens.
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
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = YomuTheme.space.lg, vertical = if (compact) YomuTheme.space.lg else YomuTheme.space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        YomuShapeBadge(icon = icon, size = if (compact) 64.dp else 96.dp, shape = shape, tone = tone)
        Text(
            text = title,
            modifier = Modifier.padding(top = YomuTheme.space.xs).semantics { heading() },
            style = if (compact) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Text(
                text = body,
                modifier = Modifier.widthIn(max = 360.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Row(
                modifier = Modifier.padding(top = YomuTheme.space.xs),
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/**
 * The single page-level section header: emphasized, primary-coloured title with optional supporting
 * line and a trailing action. (Inside sheets and dense panes use the smaller [YomuSectionLabel].)
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
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Steps of the tonal surface ladder. Cards never use borders or shadows to separate themselves. */
enum class YomuCardTone { Group, Raised, Hero, HeroAlt }

/**
 * A contained surface on the tonal ladder: [YomuCardTone.Group] (the same tone as segmented rows),
 * [YomuCardTone.Raised] for cards on top of a group or sheet, and the two hero containers.
 */
@Composable
fun YomuCard(
    modifier: Modifier = Modifier,
    tone: YomuCardTone = YomuCardTone.Group,
    shape: Shape = if (tone == YomuCardTone.Group || tone == YomuCardTone.Raised) YomuShapeTokens.tile else YomuShapeTokens.hero,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(YomuTheme.space.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer) = when (tone) {
        YomuCardTone.Group -> scheme.surfaceContainer to scheme.onSurface
        YomuCardTone.Raised -> scheme.surfaceContainerHigh to scheme.onSurface
        YomuCardTone.Hero -> scheme.primaryContainer to scheme.onPrimaryContainer
        YomuCardTone.HeroAlt -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = container, contentColor = onContainer, content = body)
    } else {
        Surface(modifier = modifier, shape = shape, color = container, contentColor = onContainer, content = body)
    }
}

enum class YomuNoticeTone { Neutral, Accent, Error, Floating }

/**
 * A tonal message strip: inline information, warnings, and (with [YomuNoticeTone.Floating]) the
 * transient notices that replace stock snackbars. [leading] overrides the icon, e.g. with a
 * [YomuLoadingIndicator] while work is running.
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
) {
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer, actionColor) = when (tone) {
        YomuNoticeTone.Neutral -> Triple(scheme.surfaceContainerHigh, scheme.onSurface, scheme.primary)
        YomuNoticeTone.Accent -> Triple(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.onSecondaryContainer)
        YomuNoticeTone.Error -> Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.onErrorContainer)
        YomuNoticeTone.Floating -> Triple(scheme.inverseSurface, scheme.inverseOnSurface, scheme.inversePrimary)
    }
    Surface(
        modifier = modifier,
        shape = YomuShapeTokens.tile,
        color = container,
        contentColor = onContainer,
        shadowElevation = if (tone == YomuNoticeTone.Floating) 6.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(
                start = YomuTheme.space.md,
                end = if (actionLabel != null) YomuTheme.space.xxs else YomuTheme.space.md,
                top = YomuTheme.space.xxs,
                bottom = YomuTheme.space.xxs,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            when {
                leading != null -> leading()
                icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f).padding(vertical = YomuTheme.space.sm),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                ) { Text(actionLabel, style = MaterialTheme.typography.labelLargeEmphasized) }
            }
        }
    }
}

/**
 * The one confirmation / message dialog: optional badge icon, emphasized title, and press-morphing
 * buttons. [destructive] paints the confirm action in the error role.
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
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.yomuBackPreview(backPreview),
        shape = YomuShapeTokens.hero,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        icon = icon?.let {
            {
                YomuShapeBadge(
                    icon = it,
                    size = 56.dp,
                    shape = if (destructive) YomuBadgeShape.Burst else YomuBadgeShape.Scallop,
                    tone = if (destructive) YomuBadgeTone.Error else YomuBadgeTone.Secondary,
                )
            }
        },
        title = {
            // Registered from inside the dialog so it uses the dialog window's back dispatcher.
            YomuBackPreviewHandler(backPreview, onBack = onDismiss)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                textAlign = if (icon != null) TextAlign.Center else TextAlign.Start,
            )
        },
        text = if (text != null || content != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
                    if (text != null) Text(text, style = MaterialTheme.typography.bodyMedium)
                    content?.invoke(this)
                }
            }
        } else {
            null
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                shapes = ButtonDefaults.shapes(),
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(label) } }
        },
    )
}

/** How the enclosing [YomuBottomSheet] is presented; lets shared headers adapt. */
enum class YomuSheetPresentation { Sheet, Dialog }

val LocalYomuSheetPresentation = staticCompositionLocalOf { YomuSheetPresentation.Sheet }

/**
 * The shared header for every [YomuBottomSheet]: emphasized title, optional supporting line and
 * trailing actions. When the sheet is presented as a tablet dialog (no drag handle) and [onClose]
 * is given, a close button appears so there is always a visible way out.
 */
@Composable
fun YomuSheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = YomuTheme.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke(this)
        if (onClose != null && LocalYomuSheetPresentation.current == YomuSheetPresentation.Dialog) {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(StackIcons.Close, contentDescription = "Close")
            }
        }
    }
}
