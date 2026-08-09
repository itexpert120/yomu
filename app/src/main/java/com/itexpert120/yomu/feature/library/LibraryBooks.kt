package com.itexpert120.yomu.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.yomuPressable
import java.io.File

@Composable
fun GridBookCard(
    book: LibraryBook,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Card(
        modifier = modifier.yomuPressable(onClick = onClick, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                BookCoverImage(
                    book = book,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionMarker(selected)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                LibraryProgressIndicator(
                    progress = book.progress,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
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
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .clip(shape)
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { contentDescription = book.title },
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                        tileMode = TileMode.Clamp,
                    ),
                ),
        )
        Text(
            text = book.title,
            color = Color.White,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp),
        ) {
            LibraryProgressIndicator(book.progress, Modifier.fillMaxSize())
        }
        SelectionMarker(selected)
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
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = modifier
            .clip(shape)
            .yomuPressable(onClick = onClick, onLongClick = onLongPress)
            .semantics { contentDescription = book.title },
    ) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(4.dp),
        ) {
            LibraryProgressIndicator(book.progress, Modifier.fillMaxSize())
        }
        SelectionMarker(selected)
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onClick, onLongClick = onLongPress),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                BookCoverImage(
                    book = book,
                    modifier = Modifier.width(56.dp),
                )
            },
            headlineContent = {
                Text(
                    text = book.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = book.author,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LibraryProgressIndicator(
                        progress = book.progress,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            trailingContent = {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val percent = (book.progress.coerceIn(0f, 1f) * 100).toInt()
                    Text(
                        text = "$percent%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }
}

@Composable
internal fun BoxScope.SelectionMarker(selected: Boolean) {
    if (!selected) return
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            .border(2.dp, MaterialTheme.colorScheme.primary, shape),
    )
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
fun ImportEmptyCard(onImport: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .yomuPressable(onClick = onImport),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "Import EPUB",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
fun GroupSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
internal fun BookCoverImage(
    book: LibraryBook,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f / 1.55f)
            .clip(MaterialTheme.shapes.medium)
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
                    .padding(10.dp),
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

/** Shows a muted track for untouched books so every card exposes the same progress affordance. */
@Composable
internal fun LibraryProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { fraction },
        color = if (fraction > 0f) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
        },
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        drawStopIndicator = {},
        modifier = modifier,
    )
}

@Composable
fun ProgressLine(progress: Float) {
    LibraryProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
}
