package com.itexpert120.yomu.feature.reader

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
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
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuButton
import com.itexpert120.yomu.core.designsystem.YomuButtonEmphasis
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.web.openWebSearchSheet
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.dictionary.DictionarySense

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

/** Bottom sheet showing a word's dictionary definition (or loading / not-found / error states). */
@Composable
internal fun WordLookupSheet(
    state: WordLookupUiState?,
    onDismiss: () -> Unit,
    onPronounce: (String) -> Unit,
    onLookUpWord: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss, scrollable = false) { _ ->
        if (state == null) return@YomuBottomSheet
        val found = state.result as? DictionaryResult.Found
        val pronunciations = remember(found) { found?.pronunciationLabels().orEmpty() }
        // An inflection ("muttered") resolves to its base word ("mutter"), which is what's shown.
        val headword = found?.inflectionOf?.let { found.response.word.ifBlank { null } } ?: state.word

        val lookupMotion = MaterialTheme.motionScheme
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(lookupMotion.defaultSpatialSpec())
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.canGoBack) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = "Back",
                        tint = YomuTheme.colors.textSecondary,
                        onClick = onBack,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = headword,
                        color = YomuTheme.colors.textPrimary,
                        style = MaterialTheme.typography.headlineMediumEmphasized,
                    )
                    found?.inflectionOf?.let { note ->
                        Text(
                            text = "${note.word} · ${note.description}",
                            color = YomuTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (pronunciations.isNotEmpty()) {
                        PronunciationLine(pronunciations, onClick = { onPronounce(headword) })
                    }
                }
                // No audio clips in this API — speak the word with the device voice instead.
                CircleIconButton(
                    icon = Icons.AutoMirrored.Rounded.VolumeUp,
                    description = "Pronounce",
                    tint = YomuTheme.colors.accent,
                    onClick = { onPronounce(headword) },
                )
            }

            // Cross-fade loading → result (or error) while the column's height springs to fit, so
            // the sheet grows smoothly instead of jumping when the definition arrives.
            AnimatedContent(
                targetState = state,
                contentKey = { Triple(it.word, it.loading, it.result?.let { r -> r::class }) },
                transitionSpec = {
                    (fadeIn(lookupMotion.defaultEffectsSpec()) togetherWith fadeOut(lookupMotion.fastEffectsSpec()))
                        .using(SizeTransform(clip = false) { _, _ -> lookupMotion.defaultSpatialSpec() })
                },
                label = "wordLookupBody",
            ) { target ->
                val targetBlocks = remember(target.result) {
                    (target.result as? DictionaryResult.Found)?.toBlocks().orEmpty()
                }
                when {
                    target.loading -> LoadingRow()

                    targetBlocks.isNotEmpty() -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(targetBlocks) { block ->
                            when (block) {
                                is LookupBlock.PartOfSpeech -> PartOfSpeechSection(block.group, onLookUpWord)
                                is LookupBlock.Footer -> LookupFooter(block.sourceUrl, block.license)
                            }
                        }
                    }

                    target.result is DictionaryResult.Error -> LookupMessage(
                        text = "Couldn't reach the dictionary. Check your connection and try again.",
                        word = target.word,
                        onRetry = onRetry,
                    )

                    else -> LookupMessage(
                        text = "No definition found for “${target.word}”.",
                        word = target.word,
                        onRetry = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(modifier = Modifier.size(36.dp))
        Text(
            text = "Looking up…",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.body,
        )
    }
}

@Composable
private fun LookupMessage(text: String, word: String, onRetry: (() -> Unit)?) {
    val activity = LocalActivity.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRetry != null) {
                YomuButton(
                    text = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                    emphasis = YomuButtonEmphasis.Secondary,
                )
            }
            YomuButton(
                text = "Search the web",
                onClick = { activity?.openWebSearchSheet("define $word") },
                modifier = Modifier.weight(1f),
                // With nothing to retry, the web is the way forward, so it gets the primary style.
                emphasis = if (onRetry == null) YomuButtonEmphasis.Primary else YomuButtonEmphasis.Secondary,
            )
        }
    }
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
    val text = remember(items, ipaFont) {
        buildAnnotatedString {
            items.forEachIndexed { index, item ->
                if (index > 0) append("   ")
                withStyle(SpanStyle(fontFamily = ipaFont)) { append(item.ipa) }
                item.accent?.let { accent ->
                    withStyle(SpanStyle(fontSize = 12.sp, letterSpacing = 0.4.sp)) { append("  $accent") }
                }
            }
        }
    }
    Text(
        text = text,
        color = YomuTheme.colors.textSecondary,
        style = MaterialTheme.typography.titleMedium,
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (group.partOfSpeech.isNotBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        text = group.partOfSpeech,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (group.forms.isNotEmpty()) {
                Text(
                    text = group.forms.joinToString(" · "),
                    color = YomuTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
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
            TextButton(
                onClick = { expanded = !expanded },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(if (expanded) "Show fewer" else "Show $hidden more")
            }
        }
        if (group.synonyms.isNotEmpty() || group.antonyms.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
    Text(text = text, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
}

@Composable
private fun SenseItem(number: Int, sense: DictionarySense, onLookUpWord: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "$number",
            color = YomuTheme.colors.accent,
            style = MaterialTheme.typography.titleSmallEmphasized,
            modifier = Modifier.padding(top = 2.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Wiktionary often repeats the tags inline as a "(transitive, …)" prefix already.
            if (sense.tags.isNotEmpty() && !sense.definition.startsWith("(")) {
                Text(
                    text = sense.tags.joinToString(" · "),
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption.copy(fontStyle = FontStyle.Italic),
                )
            }
            TappableText(
                prefix = "",
                text = sense.definition,
                color = YomuTheme.colors.textPrimary,
                italic = false,
                onWord = onLookUpWord,
            )
            // One short example reads well; Wiktionary's long dated citations (quotes) are omitted.
            sense.examples.firstOrNull()?.let { example ->
                TappableText(
                    prefix = "",
                    text = "“$example”",
                    color = YomuTheme.colors.textMuted,
                    italic = true,
                    onWord = onLookUpWord,
                )
            }
            sense.subsenses.take(MAX_SUBSENSES).forEach { sub ->
                TappableText(
                    prefix = "• ",
                    text = sub.definition,
                    color = YomuTheme.colors.textSecondary,
                    italic = false,
                    onWord = onLookUpWord,
                )
            }
        }
    }
}

@Composable
private fun WordChip(word: String, onLookUpWord: (String) -> Unit) {
    AssistChip(
        onClick = { onLookUpWord(word) },
        label = { Text(word) },
        shape = MaterialTheme.shapes.large,
    )
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
        style = YomuTheme.type.body.copy(
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            fontWeight = FontWeight.Normal,
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
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Tap any word in a definition to look it up.",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.caption,
        )
        if (host != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = host,
                    color = if (safeUrl != null) YomuTheme.colors.accent else YomuTheme.colors.textMuted,
                    style = YomuTheme.type.caption,
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
                    Text(text = "· $license", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
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
