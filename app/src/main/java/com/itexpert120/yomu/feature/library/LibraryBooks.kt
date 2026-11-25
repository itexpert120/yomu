package com.itexpert120.yomu.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuProgressBar
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.rememberSelectionMorphShape
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.io.File

/*
 * One selection language for every library view mode: the cover settles slightly smaller under a
 * single primary tint with one check badge, and a card that has a container moves it to
 * secondaryContainer. No borders, no elevation; corners come from YomuShapeTokens.
 */

@Composable
fun GridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val container = rememberCardContainer(selected)
    Column(
        modifier = modifier
            .clip(YomuShapeTokens.tile)
            .drawBehind { drawRect(container.value) }
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { this.selected = selected }
            .padding(YomuTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
    ) {
        SelectableCover(book = book, selected = selected, modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Two title lines, so rows of cards keep one height.
                .height(42.dp)
                .padding(horizontal = YomuTheme.space.xxs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = book.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        YomuProgressBar(progress = book.progress)
    }
}

/** Borderless cover treatment for dense tablet grids. */
@Composable
fun CompactGridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    SelectableCover(
        book = book,
        selected = selected,
        modifier = modifier
            .clip(YomuShapeTokens.cover)
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics {
                contentDescription = book.title
                this.selected = selected
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                        tileMode = TileMode.Clamp,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(YomuTheme.space.xs),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            Text(
                text = book.title,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            YomuProgressBar(progress = book.progress)
        }
    }
}

/** Borderless cover-only treatment; title remains available to accessibility services. */
@Composable
fun CoverOnlyGridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    SelectableCover(
        book = book,
        selected = selected,
        modifier = modifier
            .clip(YomuShapeTokens.cover)
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics {
                contentDescription = book.title
                this.selected = selected
            },
    ) {
        YomuProgressBar(
            progress = book.progress,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(YomuTheme.space.xs),
        )
    }
}

@Composable
fun BookListRow(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val container = rememberCardContainer(selected)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(YomuShapeTokens.tile)
            .drawBehind { drawRect(container.value) }
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { this.selected = selected }
            .padding(YomuTheme.space.sm),
        horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectableCover(book = book, selected = selected, modifier = Modifier.width(56.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
        ) {
            Text(
                text = book.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            YomuProgressBar(
                progress = book.progress,
                modifier = Modifier.padding(top = YomuTheme.space.xxs),
            )
        }
        val percent = (book.progress.coerceIn(0f, 1f) * 100).toInt()
        Text(
            text = "$percent%",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * A cover that carries the selection treatment. [overlay] draws on the cover (title scrim,
 * progress) beneath the selection tint.
 */
@Composable
private fun SelectableCover(
    book: LibraryBook,
    selected: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val scale = rememberCoverScale(selected)
    Box(
        modifier = modifier
            // Read in the draw phase, so settling the cover never recomposes the card.
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(YomuShapeTokens.cover),
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth())
        overlay()
        SelectionMark(selected)
    }
}

@Composable
private fun BoxScope.SelectionMark(selected: Boolean) {
    val animate = yomuAnimationsEnabled()
    AnimatedVisibility(
        visible = selected,
        modifier = Modifier.matchParentSize(),
        enter = if (animate) fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) else EnterTransition.None,
        exit = if (animate) fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) else ExitTransition.None,
    ) {
        // Flip after the first frame so the badge morphs circle → scallop as it appears.
        var settled by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { settled = true }
        val badgeShape = rememberSelectionMorphShape(
            selected = settled,
            unselected = MaterialShapes.Circle,
            selectedPolygon = MaterialShapes.Cookie9Sided,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Scrim over the cover image, so the tint is an alpha of the primary role.
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(YomuTheme.space.xxs)
                    .size(24.dp)
                    .clip(badgeShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = StackIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun BookCoverImage(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(YomuBookCoverAspectRatio)
            .clip(YomuShapeTokens.cover)
            .background(Brush.verticalGradient(book.coverColors)),
    ) {
        // Real extracted cover when available; otherwise the generated gradient + title placeholder.
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(YomuTheme.space.xs),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.42f)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.72f)),
                )
                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = book.shortTitle,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.authorLastName,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Selected covers settle slightly smaller on the fast spatial spring. */
@Composable
private fun rememberCoverScale(selected: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (selected) 0.92f else 1f,
    animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
    label = "coverScale",
)

/** Card container on the tonal ladder; selection moves it to secondaryContainer. */
@Composable
private fun rememberCardContainer(selected: Boolean): State<Color> = animateColorAsState(
    targetValue = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    },
    animationSpec = if (yomuAnimationsEnabled()) MaterialTheme.motionScheme.fastEffectsSpec() else snap(),
    label = "cardContainer",
)
