package com.itexpert120.yomu.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.itexpert120.yomu.core.designsystem.LocalYomuSheetBottomInset
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.web.openWebSearchSheet
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.dictionary.DictionarySense
import com.itexpert120.yomu.data.encyclopedia.WikipediaResult
import java.util.Locale

/** One rendered block in the definitions list. */
private sealed interface LookupBlock {
    data class PartOfSpeech(val group: PosGroup) : LookupBlock
    data class Footer(val sourceUrl: String?, val license: String?) : LookupBlock
}

/** Wiktionary splits a word into one entry per etymology; the sheet merges them per part of speech. */
private data class PosGroup(
    val partOfSpeech: String,
    val senses: List<DictionarySense>,
    val forms: List<String>,
    val synonyms: List<String>,
    val antonyms: List<String>,
)

/** One accent's IPA, e.g. "/skɒf/" + "UK". */
private data class PronunciationLabel(val ipa: String, val accent: String?)

/** Space the lookup sheet needs around its definitions: drag handle, header, bottom inset. */
private val LookupSheetReservedHeight = 220.dp

/** What the look-up sheet can ask for besides closing. */
internal class WordLookupActions(
    val onPronounce: (String) -> Unit = {},
    val onLookUpWord: (String) -> Unit = {},
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onSelectTab: (LookupTab) -> Unit = {},
    val onLookUpInEnglish: () -> Unit = {},
    val onRetryWikipedia: () -> Unit = {},
    val onTranslate: (String) -> Unit = {},
    val onSearchInBook: (String) -> Unit = {},
)

/**
 * The look-up sheet: a word's dictionary definition (or loading / not-found / error states) and,
 * on its own tab, the Wikipedia summary for the selection; a short phrase shows Wikipedia only.
 */
@Composable
internal fun WordLookupSheet(
    state: WordLookupUiState?,
    onDismiss: () -> Unit,
    actions: WordLookupActions,
) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss, scrollable = false) { dismiss ->
        if (state == null) return@YomuBottomSheet
        WordLookupSheetContent(state, dismiss, actions)
    }
}

/** The word lookup sheet's body, separate from its sheet so it can be previewed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordLookupSheetContent(
    state: WordLookupUiState,
    dismiss: () -> Unit,
    actions: WordLookupActions,
) {
    run {
        val found = state.result as? DictionaryResult.Found
        val pronunciations = remember(found) { found?.pronunciationLabels().orEmpty() }
        // An inflection ("muttered") resolves to its base word ("mutter"), which is what's shown.
        val headword = when {
            state.phrase -> state.query
            else -> found?.inflectionOf?.let { found.response.word.ifBlank { null } } ?: state.word
        }
        val language = remember(state.language) { lookupLanguageName(state.language) }

        val lookupMotion = MaterialTheme.motionScheme
        val bottomInset = LocalYomuSheetBottomInset.current
        val stack = YomuTheme.stack
        val activity = LocalActivity.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(lookupMotion.defaultSpatialSpec())
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.sm),
        ) {
            // The one-line header: Back through earlier look-ups, the looked-up word as the title,
            // the web and the close × at the end.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.canGoBack) {
                    LookupIconButton(StackIcons.ArrowBack, "Back", onClick = actions.onBack)
                }
                Text(
                    text = headword,
                    color = stack.ink,
                    style = StackType.Title.copy(fontSize = 30.sp, lineHeight = 34.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (activity != null) {
                    LookupIconButton(
                        StackIcons.TravelExplore,
                        "Search the web for $headword",
                        tint = stack.muted,
                        onClick = { activity.openWebSearchSheet(if (state.phrase) headword else "define $headword") },
                    )
                }
                LookupIconButton(
                    StackIcons.Close,
                    "Close",
                    tint = stack.muted,
                    onClick = dismiss,
                )
            }
            // The language (when it isn't English), the inflection note and IPA under the word;
            // pronounce beside them.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (language != null) {
                        Text(text = language, color = stack.muted, style = StackType.Label)
                    }
                    found?.inflectionOf?.let { note ->
                        Text(
                            text = "${note.word} · ${note.description}",
                            color = stack.muted,
                            style = StackType.BodySmall,
                        )
                    }
                    if (pronunciations.isNotEmpty()) {
                        PronunciationLine(pronunciations, onClick = { actions.onPronounce(headword) })
                    }
                }
                // No audio clips in this API — speak the word with the device voice instead.
                ReaderPillButton(
                    text = "Pronounce",
                    onClick = { actions.onPronounce(headword) },
                    style = ReaderPillStyle.Secondary,
                    icon = StackIcons.VolumeUp,
                    height = 44.dp,
                )
            }
            // Where else the selection can go: the device's translator, and the rest of this book.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
            ) {
                if (state.canTranslate) {
                    ReaderChip(
                        label = "Translate",
                        selected = false,
                        role = Role.Button,
                        onClick = { actions.onTranslate(state.query) },
                    )
                }
                ReaderChip(
                    label = "Search in book",
                    selected = false,
                    role = Role.Button,
                    leadingIcon = StackIcons.Search,
                    onClick = { actions.onSearchInBook(state.query) },
                )
            }
            if (!state.phrase) {
                LookupTabs(selected = state.tab, onSelect = actions.onSelectTab)
            }

            // Cross-fade loading → result (or error) while the column's height springs to fit, so
            // the sheet grows smoothly instead of jumping when the definition arrives.
            AnimatedContent(
                targetState = state,
                contentKey = {
                    if (it.tab == LookupTab.Wikipedia) {
                        listOf(it.tab, it.query, it.wikipedia?.loading, it.wikipedia?.result?.let { r -> r::class })
                    } else {
                        listOf(it.tab, it.word, it.language, it.loading, it.result?.let { r -> r::class })
                    }
                },
                transitionSpec = {
                    (fadeIn(lookupMotion.defaultEffectsSpec()) togetherWith fadeOut(lookupMotion.fastEffectsSpec()))
                        .using(SizeTransform(clip = false) { _, _ -> lookupMotion.defaultSpatialSpec() })
                },
                label = "wordLookupBody",
            ) { target ->
                if (target.tab == LookupTab.Wikipedia) {
                    WikipediaBody(target, actions.onRetryWikipedia)
                    return@AnimatedContent
                }
                val targetBlocks = remember(target.result) {
                    (target.result as? DictionaryResult.Found)?.toBlocks().orEmpty()
                }
                when {
                    target.loading -> ReaderLoadingBlock("Looking up…", horizontalPadding = 0.dp, modifier = Modifier.padding(bottom = bottomInset))

                    targetBlocks.isNotEmpty() -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = readerSheetListMaxHeight(LookupSheetReservedHeight) + bottomInset),
                        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.lg),
                        // Definitions scroll on down behind the gesture bar.
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
                    ) {
                        items(targetBlocks) { block ->
                            when (block) {
                                is LookupBlock.PartOfSpeech -> PartOfSpeechSection(block.group, actions.onLookUpWord)
                                is LookupBlock.Footer -> LookupFooter(block.sourceUrl, block.license)
                            }
                        }
                    }

                    target.result is DictionaryResult.Error -> LookupMessage(
                        icon = StackIcons.CloudOff,
                        title = "Couldn't reach the dictionary",
                        text = "You seem to be offline. Definitions need a connection.",
                        webQuery = "define ${target.word}",
                        primaryLabel = "Try again",
                        onPrimary = actions.onRetry,
                    )

                    // Nothing in the book's language: offer the English dictionary, then the web.
                    target.language != "en" -> LookupMessage(
                        icon = null,
                        title = null,
                        text = "No ${lookupLanguageName(target.language) ?: "entry"} entry for “${target.word}”.",
                        webQuery = "${target.word} meaning",
                        primaryLabel = "Look up in English",
                        onPrimary = actions.onLookUpInEnglish,
                    )

                    else -> LookupMessage(
                        icon = null,
                        title = null,
                        text = "No definition found. It may be a name or a rare spelling.",
                        webQuery = "define ${target.word}",
                    )
                }
            }
        }
    }
}

/** Dictionary · Wikipedia, as Stack's word tabs: the selected one in ink 600 over a 2dp ink line. */
@Composable
private fun LookupTabs(selected: LookupTab, onSelect: (LookupTab) -> Unit) {
    val stack = YomuTheme.stack
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(stack.rule, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        LookupTab.entries.forEach { tab ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(tab) })
                    .drawBehind {
                        if (active) {
                            val line = 2.dp.toPx()
                            drawRoundRect(
                                color = stack.ink,
                                topLeft = Offset(0f, size.height - line),
                                size = Size(size.width, line),
                                cornerRadius = CornerRadius(line / 2),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.label,
                    color = if (active) stack.ink else stack.faint,
                    style = StackType.Body.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The Wikipedia tab: the article's title, description and summary (thumbnail beside it when it
 * has one), "Read on Wikipedia", and the CC BY-SA attribution the text requires.
 */
@Composable
private fun WikipediaBody(state: WordLookupUiState, onRetry: () -> Unit) {
    val wiki = state.wikipedia
    val stack = YomuTheme.stack
    when (val result = wiki?.result) {
        null -> ReaderLoadingBlock("Looking up on Wikipedia…", horizontalPadding = 0.dp, modifier = Modifier.padding(bottom = LocalYomuSheetBottomInset.current))

        WikipediaResult.Error -> LookupMessage(
            icon = StackIcons.CloudOff,
            title = "Couldn't reach Wikipedia",
            text = "You seem to be offline. Wikipedia needs a connection.",
            webQuery = state.query,
            primaryLabel = "Try again",
            onPrimary = onRetry,
        )

        WikipediaResult.NotFound -> LookupMessage(
            icon = null,
            title = null,
            text = "No Wikipedia article for “${state.query}”.",
            webQuery = state.query,
        )

        is WikipediaResult.Found -> {
            val summary = result.summary
            val uriHandler = LocalUriHandler.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = readerSheetListMaxHeight(LookupSheetReservedHeight) + LocalYomuSheetBottomInset.current)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = summary.title, color = stack.ink, style = StackType.Heading)
                        summary.description?.let { Text(text = it, color = stack.muted, style = StackType.BodySmall) }
                    }
                    summary.thumbnail?.let { thumbnail ->
                        AsyncImage(
                            model = thumbnail.bytes,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(stack.raised),
                        )
                    }
                }
                Text(text = summary.extract, color = stack.body, style = StackType.Body)
                Row(
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .yomuPressable(onClick = { runCatching { uriHandler.openUri(summary.pageUrl) } }, pressedScale = 1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(StackIcons.TravelExplore, contentDescription = null, tint = stack.ink, modifier = Modifier.size(20.dp))
                    Text(text = "Read on Wikipedia", color = stack.ink, style = StackType.Label.copy(fontSize = 14.sp))
                }
                Text(
                    text = "From Wikipedia, the free encyclopedia · Text under CC BY-SA 4.0",
                    color = stack.faint,
                    style = StackType.Caption,
                    modifier = Modifier.padding(bottom = YomuTheme.space.xs + LocalYomuSheetBottomInset.current),
                )
            }
        }
    }
}

@Composable
private fun LookupIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = YomuTheme.stack.ink,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = modifier.size(48.dp),
    ) {
        Icon(icon, contentDescription = description)
    }
}

/** Offline or nothing found: what happened, then the one action that can help, and the web. */
@Composable
private fun LookupMessage(
    icon: ImageVector?,
    title: String?,
    text: String,
    webQuery: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
) {
    val stack = YomuTheme.stack
    val activity = LocalActivity.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = YomuTheme.space.xxs, bottom = YomuTheme.space.xs + LocalYomuSheetBottomInset.current),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = stack.muted)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) Text(text = title, color = stack.ink, style = StackType.Heading)
                Text(
                    text = text,
                    color = if (title != null) stack.muted else stack.body,
                    style = if (title != null) StackType.BodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp) else StackType.Body,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
            verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            if (primaryLabel != null && onPrimary != null) {
                ReaderPillButton(text = primaryLabel, onClick = onPrimary)
            }
            if (activity != null) {
                ReaderPillButton(
                    text = "Search the web",
                    onClick = { activity.openWebSearchSheet(webQuery) },
                    style = ReaderPillStyle.Secondary,
                    icon = if (onPrimary == null) StackIcons.TravelExplore else null,
                )
            }
        }
    }
}

/** "French" for "fr", in English like the rest of the app; null for English or an unknown code. */
internal fun lookupLanguageName(code: String): String? {
    if (code.equals("en", ignoreCase = true)) return null
    val name = Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH)
    return name.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
}

/** IPA in Cardo: device monospace/UI fonts (e.g. Xiaomi's) often lack IPA glyphs and render tofu. */
@Composable
private fun rememberIpaFontFamily(): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets) { FontFamily(Font("fonts/Cardo-Regular.ttf", assets)) }
}

@Composable
private fun PronunciationLine(items: List<PronunciationLabel>, onClick: () -> Unit) {
    val ipaFont = rememberIpaFontFamily()
    val accentStyle = MaterialTheme.typography.labelMedium.toSpanStyle()
    val text = remember(items, ipaFont, accentStyle) {
        buildAnnotatedString {
            items.forEachIndexed { index, item ->
                if (index > 0) append("   ")
                withStyle(SpanStyle(fontFamily = ipaFont)) { append(item.ipa) }
                item.accent?.let { accent ->
                    withStyle(accentStyle) { append("  $accent") }
                }
            }
        }
    }
    Text(
        text = text,
        color = YomuTheme.stack.muted,
        style = StackType.Body,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.yomuPressable(onClick = onClick, pressedScale = 1f),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartOfSpeechSection(group: PosGroup, onLookUpWord: (String) -> Unit) {
    var expanded by remember(group) { mutableStateOf(false) }
    val visible = if (expanded) group.senses else group.senses.take(COLLAPSED_SENSES)
    val stack = YomuTheme.stack
    Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
        ) {
            if (group.partOfSpeech.isNotBlank()) {
                ReaderSectionLabel(group.partOfSpeech.replaceFirstChar { it.titlecase() })
            }
            if (group.forms.isNotEmpty()) {
                Text(
                    text = group.forms.joinToString(" · "),
                    color = stack.faint,
                    style = StackType.Caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        visible.forEachIndexed { index, sense ->
            SenseItem(number = index + 1, sense = sense, onLookUpWord = onLookUpWord)
        }
        val hidden = group.senses.size - COLLAPSED_SENSES
        if (hidden > 0) {
            ReaderPillButton(
                text = if (expanded) "Show fewer" else "Show $hidden more",
                onClick = { expanded = !expanded },
                style = ReaderPillStyle.Secondary,
                height = 40.dp,
            )
        }
        if (group.synonyms.isNotEmpty() || group.antonyms.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xs),
                verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (group.synonyms.isNotEmpty()) {
                    RelatedLabel("Similar")
                    group.synonyms.take(MAX_RELATED).forEach { WordChip(it, onLookUpWord) }
                }
                if (group.antonyms.isNotEmpty()) {
                    RelatedLabel("Opposite")
                    group.antonyms.take(MAX_RELATED).forEach { WordChip(it, onLookUpWord) }
                }
            }
        }
    }
}

@Composable
private fun RelatedLabel(text: String) {
    ReaderSectionLabel(text)
}

@Composable
private fun SenseItem(number: Int, sense: DictionarySense, onLookUpWord: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.sm)) {
        Text(
            text = "$number",
            color = YomuTheme.stack.faint,
            style = StackType.Body.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.width(16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
            // Wiktionary often repeats the tags inline as a "(transitive, …)" prefix already.
            if (sense.tags.isNotEmpty() && !sense.definition.startsWith("(")) {
                Text(
                    text = sense.tags.joinToString(" · "),
                    color = YomuTheme.stack.muted,
                    style = StackType.Caption.copy(fontStyle = FontStyle.Italic),
                )
            }
            TappableText(
                prefix = "",
                text = sense.definition,
                color = YomuTheme.stack.body,
                italic = false,
                onWord = onLookUpWord,
            )
            // One short example reads well; Wiktionary's long dated citations (quotes) are omitted.
            sense.examples.firstOrNull()?.let { example ->
                TappableText(
                    prefix = "",
                    text = "“$example”",
                    color = YomuTheme.stack.muted,
                    italic = true,
                    onWord = onLookUpWord,
                )
            }
            sense.subsenses.take(MAX_SUBSENSES).forEach { sub ->
                TappableText(
                    prefix = "• ",
                    text = sub.definition,
                    color = YomuTheme.stack.muted,
                    italic = false,
                    onWord = onLookUpWord,
                )
            }
        }
    }
}

@Composable
private fun WordChip(word: String, onLookUpWord: (String) -> Unit) {
    val stack = YomuTheme.stack
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(stack.raised)
            .yomuPressable(onClick = { onLookUpWord(word) }, shape = CircleShape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = word, color = stack.body, style = StackType.Label)
    }
}

/**
 * Renders [text] with content words tappable + underlined (each looks itself up). Common function
 * words (articles, prepositions, pronouns, helping verbs — see [STOPWORDS]) stay plain text so the
 * passage isn't a wall of underlines. The underline keeps the theme text colour.
 */
@Composable
private fun TappableText(
    prefix: String,
    text: String,
    color: Color,
    italic: Boolean,
    onWord: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // No underline: a definition full of underlines reads as noise. The footer hint covers discovery.
    val linkStyles = remember(color) { TextLinkStyles(style = SpanStyle(color = color)) }
    val annotated = remember(prefix, text, linkStyles) {
        buildAnnotatedString {
            if (prefix.isNotEmpty()) append(prefix)
            // Split into word / non-word runs so punctuation and spacing are preserved verbatim.
            Regex("[\\p{L}][\\p{L}'-]*|[^\\p{L}]+").findAll(text).forEach { match ->
                val token = match.value
                val isWord = token.firstOrNull()?.isLetter() == true
                if (isWord && token.lowercase() !in STOPWORDS) {
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = token,
                            styles = linkStyles,
                            linkInteractionListener = LinkInteractionListener { onWord(token) },
                        ),
                    ) { append(token) }
                } else {
                    append(token)
                }
            }
        }
    }
    Text(
        text = annotated,
        color = color,
        style = StackType.Body.copy(
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        ),
        modifier = modifier,
    )
}

/** Common function words left un-underlined / not looked up (articles, prepositions, pronouns,
 *  helping verbs, conjunctions, and a few high-frequency adverbs/determiners). */
private val STOPWORDS: Set<String> = setOf(
    "a", "an", "the",
    "and", "or", "but", "nor", "so", "yet", "if", "then", "than", "as", "because",
    "although", "though", "while", "whereas", "since", "until", "unless", "whether",
    "of", "to", "from", "in", "on", "at", "by", "with", "about", "into", "onto", "upon",
    "for", "over", "under", "above", "below", "between", "among", "through", "during",
    "before", "after", "without", "within", "against", "around", "off", "out", "up", "down",
    "is", "am", "are", "was", "were", "be", "been", "being",
    "do", "does", "did", "have", "has", "had",
    "will", "would", "shall", "should", "can", "could", "may", "might", "must", "ought",
    "i", "you", "he", "she", "it", "we", "they",
    "me", "him", "her", "us", "them",
    "my", "your", "his", "its", "our", "their",
    "mine", "yours", "hers", "ours", "theirs",
    "this", "that", "these", "those",
    "who", "whom", "whose", "which", "what",
    "not", "no", "nor", "yes",
    "there", "here", "very", "too", "also", "just", "only", "even", "more", "most",
    "some", "any", "all", "each", "every", "both", "few", "many", "much", "such",
    "own", "same", "other", "another", "when", "where", "why", "how",
)

@Composable
private fun LookupFooter(url: String?, license: String?) {
    val handler = androidx.compose.ui.platform.LocalUriHandler.current
    val safeUrl = remember(url) { url?.let(::trustedSourceUrl) }
    // Show the host (e.g. "en.wiktionary.org") as the tappable link, plus the licence.
    val host = remember(url) {
        url?.substringAfter("://", url)?.substringBefore("/")?.ifBlank { url }
    }
    Column(
        modifier = Modifier.padding(top = YomuTheme.space.xxs, bottom = YomuTheme.space.xs),
        verticalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs),
    ) {
        Text(
            text = "Tap any word in a definition to look it up.",
            color = YomuTheme.stack.faint,
            style = StackType.Caption,
        )
        if (host != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(YomuTheme.space.xxs)) {
                Text(
                    text = host,
                    color = if (safeUrl != null) {
                        YomuTheme.stack.ink
                    } else {
                        YomuTheme.stack.faint
                    },
                    style = StackType.Caption,
                    modifier = if (safeUrl != null) {
                        Modifier.yomuPressable(
                            onClick = { runCatching { handler.openUri(safeUrl) } },
                            pressedScale = 1f,
                        )
                    } else {
                        Modifier
                    },
                )
                if (!license.isNullOrBlank()) {
                    Text(
                        text = "· $license",
                        color = YomuTheme.stack.faint,
                        style = StackType.Caption,
                    )
                }
            }
        }
    }
}

private fun trustedSourceUrl(raw: String): String? {
    val uri = runCatching { android.net.Uri.parse(raw) }.getOrNull() ?: return null
    if (uri.scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    return raw.takeIf {
        host == "dictionaryapi.dev" || host == "wiktionary.org" || host.endsWith(".wiktionary.org")
    }
}

private fun DictionaryResult.Found.toBlocks(): List<LookupBlock> = buildList {
    response.entries
        .groupBy { it.partOfSpeech.trim().lowercase() }
        .map { (pos, entries) ->
            PosGroup(
                partOfSpeech = pos,
                senses = entries.flatMap { it.senses }.filter { it.definition.isNotBlank() },
                forms = entries.flatMap { e -> e.forms.map { it.word } }
                    .filter { !it.equals(response.word, ignoreCase = true) }
                    .distinct()
                    .take(MAX_FORMS),
                synonyms = entries.flatMap { e -> e.synonyms + e.senses.flatMap { it.synonyms } }.distinct(),
                antonyms = entries.flatMap { e -> e.antonyms + e.senses.flatMap { it.antonyms } }.distinct(),
            )
        }
        .filter { it.senses.isNotEmpty() }
        // The inflected word's own part of speech ("muttered" → verb) leads; letter/symbol entries
        // ("a" the character) are rarely what a reader means, so they go last.
        .sortedBy {
            when {
                it.partOfSpeech == inflectionOf?.partOfSpeech?.lowercase() -> 0
                it.partOfSpeech in LOW_PRIORITY_POS -> 2
                else -> 1
            }
        }
        .forEach { add(LookupBlock.PartOfSpeech(it)) }
    add(LookupBlock.Footer(response.source?.url?.takeIf { it.isNotBlank() }, response.source?.license?.name))
}

/** Up to one UK and one US IPA (falling back to the first one listed), deduped. */
private fun DictionaryResult.Found.pronunciationLabels(): List<PronunciationLabel> {
    fun accentOf(tags: List<String>): String? = when {
        tags.any { "American" in it || it == "US" } -> "US"
        tags.any { "Received" in it || "British" in it || it == "UK" } -> "UK"
        else -> null
    }
    val labelled = response.entries
        .flatMap { it.pronunciations }
        .filter { it.text.isNotBlank() }
        .map { PronunciationLabel(it.text, accentOf(it.tags)) }
    val picks = listOfNotNull(
        labelled.firstOrNull { it.accent == "UK" },
        labelled.firstOrNull { it.accent == "US" },
    ).ifEmpty { labelled.take(1) }
    return picks.distinctBy { it.ipa }
}

private const val COLLAPSED_SENSES = 3
private const val MAX_SUBSENSES = 2
private const val MAX_FORMS = 4
private const val MAX_RELATED = 6
private val LOW_PRIORITY_POS = setOf("character", "symbol", "letter", "number", "punctuation")
