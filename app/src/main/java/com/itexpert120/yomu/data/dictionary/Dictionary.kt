package com.itexpert120.yomu.data.dictionary

import kotlinx.serialization.Serializable

/** One dictionary entry as returned by the Free Dictionary API (dictionaryapi.dev). */
@Serializable
data class DictionaryEntry(
    val word: String = "",
    val phonetic: String? = null,
    val meanings: List<DictionaryMeaning> = emptyList(),
)

@Serializable
data class DictionaryMeaning(
    val partOfSpeech: String = "",
    val definitions: List<DictionaryDefinition> = emptyList(),
)

@Serializable
data class DictionaryDefinition(
    val definition: String = "",
    val example: String? = null,
)

/** Outcome of a word lookup. */
sealed interface DictionaryResult {
    data class Found(val entries: List<DictionaryEntry>) : DictionaryResult
    data object NotFound : DictionaryResult
    data object Error : DictionaryResult
}
