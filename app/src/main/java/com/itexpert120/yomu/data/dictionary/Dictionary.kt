package com.itexpert120.yomu.data.dictionary

import kotlinx.serialization.Serializable

/**
 * Dictionary response from freedictionaryapi.com (/api/v1/entries/{lang}/{word}). The top level is a
 * single object: the [word] and one [DictionaryEntry] per part of speech, plus [source] attribution.
 * Unknown keys are ignored by the decoder.
 */
@Serializable
data class DictionaryResponse(
    val word: String = "",
    val entries: List<DictionaryEntry> = emptyList(),
    val source: DictionarySource? = null,
)

@Serializable
data class DictionaryEntry(
    val language: DictionaryLanguage? = null,
    val partOfSpeech: String = "",
    // IPA pronunciations (this API carries no audio clips); [DictionaryPronunciation.tags] name the
    // accent (e.g. "Received Pronunciation").
    val pronunciations: List<DictionaryPronunciation> = emptyList(),
    val forms: List<DictionaryForm> = emptyList(),
    val senses: List<DictionarySense> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
)

@Serializable
data class DictionaryLanguage(val code: String = "", val name: String = "")

@Serializable
data class DictionaryPronunciation(
    val type: String = "",
    val text: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class DictionaryForm(
    val word: String = "",
    val tags: List<String> = emptyList(),
)

@Serializable
data class DictionarySense(
    val definition: String = "",
    val tags: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val quotes: List<DictionaryQuote> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val antonyms: List<String> = emptyList(),
    val translations: List<DictionaryTranslation> = emptyList(),
    // Nested finer-grained senses (recursive).
    val subsenses: List<DictionarySense> = emptyList(),
)

@Serializable
data class DictionaryQuote(val text: String = "", val reference: String = "")

@Serializable
data class DictionaryTranslation(
    val language: DictionaryLanguage? = null,
    val word: String = "",
)

@Serializable
data class DictionarySource(
    val url: String = "",
    val license: DictionaryLicense? = null,
)

@Serializable
data class DictionaryLicense(val name: String = "", val url: String = "")

/** Outcome of a word lookup. */
sealed interface DictionaryResult {
    data class Found(val response: DictionaryResponse) : DictionaryResult
    data object NotFound : DictionaryResult
    data object Error : DictionaryResult
}
