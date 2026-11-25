package com.itexpert120.yomu.data.dictionary

/**
 * What a piece of text is to the dictionary, which has entries for single words only. The one rule
 * shared by the selection menu, the reader's look-up flow, and [DictionaryRepository].
 */
sealed interface LookupTerm {
    /** One word in dictionary form: lower-case, straight apostrophes, no surrounding punctuation. */
    data class Word(val word: String) : LookupTerm {
        /** The word without a possessive "'s" ("dantès's" → "dantès"), which has no entry of its own. */
        val withoutPossessive: String?
            get() = word.removeSuffix("'s").takeIf { it.length < word.length && it.isNotEmpty() }
    }

    /** Several words, trimmed of surrounding punctuation; [firstWord] is the first of them as a word. */
    data class Phrase(val text: String, val firstWord: Word?) : LookupTerm {
        /** Short enough to be a name or a term ("river bank", "Toad Hall"): looked up on Wikipedia, not the web. */
        val isShort: Boolean
            get() = text.length <= MAX_SHORT_PHRASE_CHARS && text.split(WHITESPACE).size <= MAX_SHORT_PHRASE_WORDS
    }

    companion object {
        private const val MAX_SHORT_PHRASE_WORDS = 5
        private const val MAX_SHORT_PHRASE_CHARS = 60

        /** Classifies [raw]; null when it holds nothing to look up. */
        fun of(raw: String?): LookupTerm? {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return null
            val words = text.split(WHITESPACE)
            return if (words.size > 1) {
                Phrase(text.trim { !it.isLetterOrDigit() }, word(words.first()))
            } else {
                word(text)
            }
        }

        private fun word(token: String): Word? = token
            .lowercase()
            // Books use typographic apostrophes ("couldn’t"); normalise them so they aren't dropped.
            .replace('’', '\'')
            .replace('‘', '\'')
            .filter { it.isLetter() || it == '-' || it == '\'' }
            .trim('-', '\'')
            .takeIf { it.isNotBlank() }
            ?.let(::Word)

        private val WHITESPACE = Regex("\\s+")
    }
}
