package com.itexpert120.yomu.feature.bookdetails

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBookCoverAspectRatio
import com.itexpert120.yomu.core.designsystem.YomuShapeTokens
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.stackCoverShadow
import com.itexpert120.yomu.core.designsystem.yomuAnimationsEnabled
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.model.ShelfStatus
import java.io.File

/** Primary action height (spec: ink pill, 56 tall). */
private val PrimaryButtonHeight = 56.dp

/** Collapsed description lines before "More". */
private const val DescriptionLines = 4

/**
 * The book on its sheet: the cover at its own size beside the title, author and how far in you
 * are (one big figure), then Read / Organize, the tags and the description. [stacked] puts the
 * cover above the text, for narrow panes and large text.
 */
@Composable
internal fun BookSheetHeader(
    book: BookDetailsUi,
    toc: TocUiState,
    gutter: Dp,
    coverWidth: Dp,
    stacked: Boolean,
    onCoverClick: () -> Unit,
    onRead: () -> Unit,
    onOrganize: () -> Unit,
    modifier: Modifier = Modifier,
    identityModifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = gutter)) {
        if (stacked) {
            Column(
                modifier = Modifier.fillMaxWidth().then(identityModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DetailCover(book, onClick = onCoverClick, modifier = Modifier.width(coverWidth))
                Spacer(Modifier.height(20.dp))
                BookTitleBlock(book, centered = true)
                Spacer(Modifier.height(16.dp))
                ProgressFigure(book, toc, centered = true)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().then(identityModifier)) {
                DetailCover(book, onClick = onCoverClick, modifier = Modifier.width(coverWidth))
                // The figure sits at the foot of the cover; a long title pushes it down instead.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 20.dp, top = 4.dp)
                        .heightIn(min = coverWidth / YomuBookCoverAspectRatio - 4.dp),
                ) {
                    BookTitleBlock(book, centered = false)
                    Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                    ProgressFigure(book, toc, centered = false)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        BookActions(book = book, onRead = onRead, onOrganize = onOrganize)
        BookTags(book, modifier = Modifier.padding(top = 14.dp))
        book.description?.takeIf { it.isNotBlank() }?.let { description ->
            BookDescription(description, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun BookTitleBlock(book: BookDetailsUi, centered: Boolean) {
    val stack = YomuTheme.stack
    val align = if (centered) TextAlign.Center else TextAlign.Start
    Text(
        text = book.title,
        style = StackType.Title,
        color = stack.ink,
        textAlign = align,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.semantics { heading() },
    )
    if (book.author.isNotBlank()) {
        Text(
            text = book.author,
            style = StackType.Body,
            color = stack.muted,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** One figure for the book: how far in, with where that is underneath. */
@Composable
private fun ProgressFigure(book: BookDetailsUi, toc: TocUiState, centered: Boolean) {
    val stack = YomuTheme.stack
    val percent = (book.progress.coerceIn(0f, 1f) * 100).toInt()
    val caption = when {
        book.progress >= 1f -> "Finished"
        book.progress <= 0f -> "Not started"
        toc.currentNumber != null && toc.flat -> "Chapter ${toc.currentNumber} of ${toc.readCount + toc.unreadCount}"
        toc.currentTitle != null -> toc.currentTitle
        else -> "Read so far"
    }
    Column(
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(text = "$percent%", style = StackType.Figure, color = stack.ink, maxLines = 1)
        Text(
            text = caption,
            style = StackType.Label.copy(fontWeight = FontWeight.Normal),
            color = stack.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Read / Resume as the ink pill, Organize as the round secondary button beside it. */
@Composable
internal fun BookActions(
    book: BookDetailsUi,
    onRead: () -> Unit,
    onOrganize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = readLabel(book)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InkPillButton(
            text = label,
            onClick = onRead,
            modifier = Modifier
                .weight(1f)
                .semantics { testTagsAsResourceId = true }
                .testTag("book-details-read"),
        )
        RoundOutlinedButton(
            contentDescription = "Organize: status and collections",
            onClick = onOrganize,
        )
    }
}

internal fun readLabel(book: BookDetailsUi): String = when {
    book.canResume -> "Resume"
    book.progress >= 1f -> "Read again"
    else -> "Start reading"
}

@Composable
internal fun InkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = PrimaryButtonHeight,
    showPlay: Boolean = true,
) {
    val stack = YomuTheme.stack
    Row(
        modifier = modifier
            .height(height)
            .yomuPressable(onClick = onClick, enabled = enabled, shape = CircleShape)
            .clip(CircleShape)
            .background(if (enabled) stack.ink else stack.track)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPlay) {
            Icon(
                StackIcons.PlayArrowFilled,
                contentDescription = null,
                tint = stack.onInk,
                modifier = Modifier.padding(end = 8.dp).size(24.dp),
            )
        }
        Text(
            text = text,
            style = StackType.Label.copy(fontSize = 16.sp, lineHeight = 20.sp),
            color = if (enabled) stack.onInk else stack.faint,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoundOutlinedButton(contentDescription: String, onClick: () -> Unit) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .size(PrimaryButtonHeight)
            .yomuPressable(onClick = onClick, shape = CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, stack.track, CircleShape)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(StackIcons.Add, contentDescription = null, tint = stack.ink)
    }
}

/** Status first (with its dot), then series, reading time and collections, wrapping as whole tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookTags(book: BookDetailsUi, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusTag(book.shelfStatus)
        book.series?.let { DetailsTag(it) }
        book.readingTime?.let { DetailsTag("$it read") }
        book.collectionNames.forEach { DetailsTag(it) }
    }
}

@Composable
private fun StatusTag(status: ShelfStatus) {
    val stack = YomuTheme.stack
    val active = status == ShelfStatus.Reading || status == ShelfStatus.Finished
    TagSurface {
        Box(
            Modifier
                .padding(end = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(if (active) stack.ink else stack.faint),
        )
        Text(text = status.label, style = TagStyle, color = stack.body, maxLines = 1)
    }
}

@Composable
private fun DetailsTag(text: String) {
    TagSurface {
        Text(text = text, style = TagStyle, color = YomuTheme.stack.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val TagStyle = StackType.Label.copy(fontSize = 12.5.sp, lineHeight = 16.sp)

@Composable
private fun TagSurface(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(YomuTheme.stack.raised)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun BookDescription(description: String, modifier: Modifier = Modifier) {
    val stack = YomuTheme.stack
    var expanded by remember(description) { mutableStateOf(false) }
    var expandable by remember(description) { mutableStateOf(false) }
    val animate = yomuAnimationsEnabled()
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = description,
            style = StackType.BodySmall,
            color = stack.body,
            maxLines = if (expanded) Int.MAX_VALUE else DescriptionLines,
            overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) expandable = it.hasVisualOverflow },
            modifier = Modifier.animateContentSize(if (animate) spec else snap(), alignment = Alignment.TopStart),
        )
        if (expandable || expanded) {
            Box(
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .yomuPressable(onClick = { expanded = !expanded }, role = Role.Button),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (expanded) "Less" else "More",
                    style = StackType.Label.copy(fontSize = 14.5.sp),
                    color = stack.ink,
                )
            }
        }
    }
}

/**
 * The cover at its own size with Stack's static shadow. A book without a cover image gets a
 * generated one: its own colours with the title on it.
 */
@Composable
internal fun DetailCover(
    book: BookDetailsUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickable = book.coverImagePath != null
    Box(
        modifier = modifier
            .aspectRatio(YomuBookCoverAspectRatio)
            .then(if (clickable) Modifier.yomuPressable(onClick = onClick) else Modifier)
            .stackCoverShadow(YomuShapeTokens.cover)
            .clip(YomuShapeTokens.cover)
            .background(YomuTheme.stack.raised)
            .then(if (clickable) Modifier.semantics { contentDescription = "View cover" } else Modifier),
    ) {
        if (book.coverImagePath != null) {
            AsyncImage(
                model = File(book.coverImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(book)
        }
    }
}

@Composable
private fun GeneratedCover(book: BookDetailsUi) {
    val colors = book.coverColors.ifEmpty { listOf(Color(0xFF23282D), Color(0xFF23282D)) }
    val backdrop = colors.last()
    val text = if (backdrop.luminance() > 0.5f) Color(0xFF16191C) else Color(0xFFEEF1F3)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(if (colors.size == 1) colors + colors else colors))
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Generated cover" },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = book.title,
            style = StackType.Heading.copy(fontSize = 17.sp, lineHeight = 19.sp),
            color = text,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.author.isNotBlank()) {
            Text(
                text = book.author,
                style = StackType.Caption.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = text.copy(alpha = 0.72f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Skeleton of the header while the book loads (usually skipped: the page opens from memory). */
@Composable
internal fun BookSheetSkeleton(gutter: Dp, coverWidth: Dp, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = gutter)) {
        Row {
            SkeletonBlock(Modifier.width(coverWidth).aspectRatio(YomuBookCoverAspectRatio), RoundedCornerShape(4.dp))
            Column(Modifier.padding(start = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(Modifier.width(170.dp).height(20.dp))
                SkeletonBlock(Modifier.width(120.dp).height(20.dp))
                SkeletonBlock(Modifier.width(96.dp).height(13.dp))
                Spacer(Modifier.height(36.dp))
                SkeletonBlock(Modifier.width(84.dp).height(36.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.weight(1f).height(PrimaryButtonHeight), CircleShape)
            SkeletonBlock(Modifier.size(PrimaryButtonHeight), CircleShape)
        }
        Spacer(Modifier.height(30.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.92f).height(12.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(12.dp))
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp)) {
    Box(modifier.clip(shape).background(YomuTheme.stack.raised))
}

/** Stack's empty-state line drawing: one large, light, muted symbol. */
@Composable
internal fun QuietSymbol(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(icon, contentDescription = null, tint = YomuTheme.stack.faint, modifier = Modifier.size(44.dp))
}

/** True when the cover and title should stack: [textWidth] (beside the cover) is too narrow, or text is large. */
@Composable
internal fun headerStacked(textWidth: Dp): Boolean = textWidth < 150.dp || LocalDensity.current.fontScale > 1.3f
