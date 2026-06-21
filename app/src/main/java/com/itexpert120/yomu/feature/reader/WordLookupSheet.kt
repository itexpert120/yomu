package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.data.dictionary.DictionaryEntry
import com.itexpert120.yomu.data.dictionary.DictionaryPronunciation
import com.itexpert120.yomu.data.dictionary.DictionaryResult
import com.itexpert120.yomu.data.dictionary.DictionarySense

/** One rendered block in the definitions list. */
private sealed interface LookupBlock {
    data class Phonetics(val items: List<DictionaryPronunciation>) : LookupBlock
    data class Entry(val entry: DictionaryEntry) : LookupBlock
    data class Source(val url: String, val license: String?) : LookupBlock
}

/** Bottom sheet showing a word's dictionary definition (or loading / not-found / error states). */
@Composable
internal fun WordLookupSheet(
    state: WordLookupUiState?,
    onDismiss: () -> Unit,
    onPronounce: (String) -> Unit,
    onLookUpWord: (String) -> Unit,
    onBack: () -> Unit,
) {
    YomuBottomSheet(visible = state != null, onDismiss = onDismiss, scrollable = false) { _ ->
        if (state == null) return@YomuBottomSheet
        val found = state.result as? DictionaryResult.Found
        val blocks = remember(found) { found?.toBlocks().orEmpty() }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.canGoBack) {
                    CircleIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        description = "Back",
                        tint = YomuTheme.colors.textSecondary,
                        onClick = onBack,
                    )
                }
                Text(
                    text = state.word,
                    color = YomuTheme.colors.textPrimary,
                    style = YomuTheme.type.display,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // No audio clips in this API — speak the word with the device voice instead.
                CircleIconButton(
                    icon = Icons.Rounded.VolumeUp,
                    description = "Pronounce",
                    tint = YomuTheme.colors.accent,
                    onClick = { onPronounce(state.word) },
                )
            }

            when {
                state.loading -> LoadingRow()

                blocks.isNotEmpty() -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(blocks) { block ->
                        when (block) {
                            is LookupBlock.Phonetics -> PhoneticsRow(block.items)
                            is LookupBlock.Entry -> EntrySection(block.entry, onLookUpWord)
                            is LookupBlock.Source -> SourceLink(block.url, block.license)
                        }
                    }
                }

                state.result is DictionaryResult.Error -> Text(
                    text = "Couldn't reach the dictionary. Check your connection and try again.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body,
                )

                else -> Text(
                    text = "No definition found for “${state.word}”.",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body,
                )
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
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(YomuTheme.colors.surfaceRaised)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(
            color = YomuTheme.colors.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Looking up…",
            color = YomuTheme.colors.textMuted,
            style = YomuTheme.type.body,
        )
    }
}

@Composable
private fun PhoneticsRow(items: List<DictionaryPronunciation>) {
    // A single row of pronunciation chips that scrolls horizontally — keeps it to one tidy line
    // instead of wrapping into a cluttered block when a word has many accent variants.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { pron ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(YomuTheme.radius.pill))
                    .background(YomuTheme.colors.surfaceRaised)
                    .border(1.dp, YomuTheme.colors.border, RoundedCornerShape(YomuTheme.radius.pill))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = pron.text,
                    color = YomuTheme.colors.textSecondary,
                    style = YomuTheme.type.mono,
                    maxLines = 1,
                )
                pron.tags.firstOrNull()?.let { tag ->
                    Text(
                        text = tag,
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntrySection(entry: DictionaryEntry, onLookUpWord: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entry.partOfSpeech.isNotBlank()) {
            Text(
                text = entry.partOfSpeech,
                color = YomuTheme.colors.accent,
                style = YomuTheme.type.caption.copy(fontStyle = FontStyle.Italic),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (entry.forms.isNotEmpty()) {
            RelatedRow("Forms", entry.forms.map { it.word }.distinct(), onLookUpWord)
        }
        entry.senses.forEachIndexed { index, sense ->
            SenseItem(label = "${index + 1}", sense = sense, onLookUpWord = onLookUpWord)
        }
        if (entry.synonyms.isNotEmpty()) RelatedRow("Synonyms", entry.synonyms, onLookUpWord)
        if (entry.antonyms.isNotEmpty()) RelatedRow("Antonyms", entry.antonyms, onLookUpWord)
    }
}

@Composable
private fun SenseItem(label: String, sense: DictionarySense, onLookUpWord: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sense.tags.isNotEmpty()) {
            Text(
                text = sense.tags.joinToString(" · "),
                color = YomuTheme.colors.textMuted,
                style = YomuTheme.type.caption,
            )
        }
        TappableText(
            prefix = "$label. ",
            text = sense.definition,
            color = YomuTheme.colors.textPrimary,
            italic = false,
            onWord = onLookUpWord,
        )
        sense.examples.forEach { example ->
            TappableText(
                prefix = "",
                text = "“$example”",
                color = YomuTheme.colors.textMuted,
                italic = true,
                onWord = onLookUpWord,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        sense.quotes.forEach { quote ->
            Column(
                modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = "“${quote.text}”",
                    color = YomuTheme.colors.textMuted,
                    style = YomuTheme.type.body.copy(fontStyle = FontStyle.Italic),
                )
                if (quote.reference.isNotBlank()) {
                    Text(
                        text = quote.reference,
                        color = YomuTheme.colors.textMuted,
                        style = YomuTheme.type.caption,
                    )
                }
            }
        }
        if (sense.synonyms.isNotEmpty()) RelatedRow("Synonyms", sense.synonyms, onLookUpWord)
        if (sense.antonyms.isNotEmpty()) RelatedRow("Antonyms", sense.antonyms, onLookUpWord)
        // Nested senses, indented and sub-numbered (1.1, 1.2, …).
        sense.subsenses.forEachIndexed { subIndex, sub ->
            Column(modifier = Modifier.padding(start = 14.dp)) {
                SenseItem(label = "$label.${subIndex + 1}", sense = sub, onLookUpWord = onLookUpWord)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedRow(label: String, words: List<String>, onLookUpWord: (String) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Text(text = label, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            words.forEach { word -> WordChip(word, onLookUpWord) }
        }
    }
}

@Composable
private fun WordChip(word: String, onLookUpWord: (String) -> Unit) {
    Text(
        text = word,
        color = YomuTheme.colors.accent,
        style = YomuTheme.type.caption,
        modifier = Modifier
            .clip(RoundedCornerShape(YomuTheme.radius.pill))
            .background(YomuTheme.colors.accentSoft)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onLookUpWord(word) },
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
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
    val linkStyles = remember(color) {
        TextLinkStyles(style = SpanStyle(color = color, textDecoration = TextDecoration.Underline))
    }
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
private fun SourceLink(url: String, license: String?) {
    val handler = androidx.compose.ui.platform.LocalUriHandler.current
    // Show the host (e.g. "en.wiktionary.org") as the tappable link, plus the licence underneath.
    val host = remember(url) {
        url.substringAfter("://", url).substringBefore("/").ifBlank { url }
    }
    Column(
        modifier = Modifier.padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = "Source", color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        Text(
            text = host,
            color = YomuTheme.colors.accent,
            style = YomuTheme.type.body,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { runCatching { handler.openUri(url) } },
            ),
        )
        if (!license.isNullOrBlank()) {
            Text(text = license, color = YomuTheme.colors.textMuted, style = YomuTheme.type.caption)
        }
    }
}

private fun DictionaryResult.Found.toBlocks(): List<LookupBlock> = buildList {
    // Pronunciations: gather across entries, dedupe by IPA text.
    val pronunciations = response.entries
        .flatMap { it.pronunciations }
        .filter { it.text.isNotBlank() }
        .distinctBy { it.text }
    if (pronunciations.isNotEmpty()) add(LookupBlock.Phonetics(pronunciations))

    response.entries.forEach { entry -> add(LookupBlock.Entry(entry)) }

    response.source?.let { src ->
        if (src.url.isNotBlank()) add(LookupBlock.Source(src.url, src.license?.name))
    }
}
