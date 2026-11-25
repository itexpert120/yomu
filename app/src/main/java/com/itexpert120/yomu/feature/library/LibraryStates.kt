package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuEmptyState
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.model.ShelfStatus
import com.itexpert120.yomu.data.collections.LibraryTab
import com.itexpert120.yomu.data.collections.LibraryTabKeys
import com.itexpert120.yomu.feature.collections.icon

/** An empty shelf: what lands here, and (unless this is All) a way back to every book. */
@Composable
internal fun EmptyTab(tab: LibraryTab, onShowAll: () -> Unit) {
    val (title, body) = when (tab.status) {
        ShelfStatus.Reading -> "Nothing in progress" to "Books you’ve started reading show up here."
        ShelfStatus.ToRead -> "Nothing waiting" to "Books you haven’t opened yet line up here."
        ShelfStatus.Finished -> "Nothing finished yet" to "Books you finish land here."
        ShelfStatus.OnHold -> "Nothing on hold" to "Set a book to On hold from Organize to park it here."
        ShelfStatus.Dropped -> "Nothing dropped" to "Books you set to Dropped are kept here."
        null -> if (tab.isCollection) {
            "“${tab.label}” is empty" to "Long-press books in the library, then tap Organize to add them."
        } else {
            "No books here" to "Import a book to start your library."
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        YomuEmptyState(
            icon = tab.status?.icon ?: if (tab.isCollection) StackIcons.CollectionsBookmark else StackIcons.AutoStories,
            title = title,
            body = body,
            modifier = Modifier.padding(top = 20.dp),
            actions = if (tab.key != LibraryTabKeys.ALL) {
                { YomuButton(text = "See all books", onClick = onShowAll, emphasis = YomuButtonEmphasis.Secondary) }
            } else {
                null
            },
        )
    }
}

/**
 * The first thing a new reader sees: three dashed cover outlines (the front one ready for a book),
 * what the library is for, and the import action at thumb height.
 */
@Composable
internal fun EmptyLibrary(
    onImport: () -> Unit,
    onHaveSyncWords: (() -> Unit)?,
    gutter: Dp,
) {
    val stack = YomuTheme.stack
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(horizontal = gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(top = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyPileIllustration()
                Text(
                    text = "Your shelves are empty",
                    style = MaterialTheme.typography.titleLarge,
                    color = stack.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .semantics { heading() },
                )
                Text(
                    text = "Add EPUB books from your phone and they’ll stack up here, ready where you left off.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = stack.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                YomuButton(
                    text = "Import EPUB books",
                    onClick = onImport,
                    icon = StackIcons.Add,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                )
                if (onHaveSyncWords != null) {
                    YomuButton(
                        text = "I have sync words",
                        onClick = onHaveSyncWords,
                        emphasis = YomuButtonEmphasis.Secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    )
                }
                Text(
                    text = "No account. Your books stay on this device unless you turn on sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = stack.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Three cover-shaped outlines leaning like the pile: dashed behind, a solid one in front with a +. */
@Composable
private fun EmptyPileIllustration() {
    val stack = YomuTheme.stack
    val dash = stack.faint.copy(alpha = 0.6f)
    Box(
        Modifier
            .size(width = 150.dp, height = 130.dp)
            .clearAndSetSemantics { },
    ) {
        for ((depth, x) in listOf(2 to 56.dp, 1 to 30.dp)) {
            Box(
                Modifier
                    .padding(start = x, top = (depth * 3).dp)
                    .size(64.dp, 96.dp)
                    .graphicsLayer {
                        rotationZ = 4f * depth
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                    .drawBehind {
                        val stroke = 1.5.dp.toPx()
                        drawRoundRect(
                            color = dash,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = size.copy(width = size.width - stroke, height = size.height - stroke),
                            cornerRadius = CornerRadius(4.dp.toPx()),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    },
            )
        }
        Box(
            Modifier
                .padding(start = 4.dp)
                .size(64.dp, 96.dp)
                .clip(RoundedCornerShape(4.dp))
                .drawBehind {
                    drawRect(stack.sheet)
                    val stroke = 1.5.dp.toPx()
                    drawRoundRect(
                        color = stack.ink,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = size.copy(width = size.width - stroke, height = size.height - stroke),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                        style = Stroke(width = stroke),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(StackIcons.Add, contentDescription = null, tint = stack.ink, modifier = Modifier.size(28.dp))
        }
    }
}

/** The shelf while the library loads: quiet tab words and a row of cover blocks, no spinner. */
@Composable
internal fun LibraryShelfSkeleton(gutter: Dp) {
    val block = YomuTheme.stack.raised
    Column(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading your library" }
            .padding(horizontal = gutter),
    ) {
        Row(
            modifier = Modifier.padding(top = 22.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            listOf(28.dp, 58.dp, 52.dp, 60.dp).forEach { SkeletonBlock(block, width = it, height = 14.dp) }
        }
        repeat(2) {
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                listOf(0.8f, 0.7f, 0.85f).forEach { titleFraction ->
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(4.dp))
                                .drawBehind { drawRect(block) },
                        )
                        Box(
                            Modifier
                                .padding(top = 10.dp)
                                .fillMaxWidth(titleFraction)
                                .height(11.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .drawBehind { drawRect(block) },
                        )
                    }
                }
            }
        }
    }
}
