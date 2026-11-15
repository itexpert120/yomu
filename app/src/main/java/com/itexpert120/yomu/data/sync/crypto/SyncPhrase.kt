package com.itexpert120.yomu.data.sync.crypto

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The 12-word sync phrase: BIP39 encoding of 128 random bits (11 bits per word from the standard
 * 2,048-word English list, plus a 4-bit SHA-256 checksum that catches typos before any network call).
 * Only the encoding is BIP39; keys are derived from the raw entropy by [SyncKeys], not the BIP39 seed.
 */
class SyncPhrase private constructor(val entropy: ByteArray, val words: List<String>) {

    override fun equals(other: Any?): Boolean = other is SyncPhrase && entropy.contentEquals(other.entropy)

    override fun hashCode(): Int = entropy.contentHashCode()

    override fun toString(): String = "SyncPhrase(***)"

    sealed interface ParseResult {
        data class Valid(val phrase: SyncPhrase) : ParseResult
        data class WrongWordCount(val count: Int) : ParseResult

        /** 1-based positions of words that aren't in the list. */
        data class UnknownWords(val positions: List<Int>) : ParseResult
        data object BadChecksum : ParseResult
    }

    class Codec(private val wordlist: List<String>) {
        private val index: Map<String, Int> = wordlist.withIndex().associate { (i, word) -> word to i }

        init {
            require(wordlist.size == WORDLIST_SIZE) { "BIP39 wordlist must have $WORDLIST_SIZE words" }
        }

        fun generate(random: SecureRandom = SecureRandom()): SyncPhrase = fromEntropy(ByteArray(ENTROPY_BYTES).also(random::nextBytes))

        fun fromEntropy(entropy: ByteArray): SyncPhrase {
            require(entropy.size == ENTROPY_BYTES) { "Sync entropy must be $ENTROPY_BYTES bytes" }
            val bits = BooleanArray(ENTROPY_BYTES * 8 + CHECKSUM_BITS)
            entropy.forEachIndexed { byteIndex, byte -> writeBits(bits, byteIndex * 8, byte.toInt() and 0xFF, 8) }
            writeBits(bits, ENTROPY_BYTES * 8, checksum(entropy), CHECKSUM_BITS)
            val words = List(WORD_COUNT) { wordIndex ->
                var value = 0
                for (bit in 0 until BITS_PER_WORD) value = (value shl 1) or if (bits[wordIndex * BITS_PER_WORD + bit]) 1 else 0
                wordlist[value]
            }
            return SyncPhrase(entropy.copyOf(), words)
        }

        /** Accepts any spacing/case, and words abbreviated to their unique first four letters. */
        fun parse(input: String): ParseResult {
            val typed = input.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (typed.size != WORD_COUNT) return ParseResult.WrongWordCount(typed.size)
            val values = typed.map(::lookup)
            val unknown = values.withIndex().filter { it.value == null }.map { it.index + 1 }
            if (unknown.isNotEmpty()) return ParseResult.UnknownWords(unknown)

            val bits = BooleanArray(WORD_COUNT * BITS_PER_WORD)
            values.forEachIndexed { i, value -> writeBits(bits, i * BITS_PER_WORD, value!!, BITS_PER_WORD) }
            val entropy = ByteArray(ENTROPY_BYTES) { byteIndex -> readBits(bits, byteIndex * 8, 8).toByte() }
            if (readBits(bits, ENTROPY_BYTES * 8, CHECKSUM_BITS) != checksum(entropy)) return ParseResult.BadChecksum
            return ParseResult.Valid(fromEntropy(entropy))
        }

        /** Words starting with [prefix], for autocomplete while typing. */
        fun suggestions(prefix: String, limit: Int = 4): List<String> {
            val p = prefix.trim().lowercase()
            if (p.isEmpty()) return emptyList()
            return wordlist.asSequence().filter { it.startsWith(p) }.take(limit).toList()
        }

        private fun lookup(word: String): Int? = index[word] ?: if (word.length >= 4) {
            // Every BIP39 word is unique by its first four letters.
            wordlist.indexOfFirst { it.startsWith(word.take(4)) && it.startsWith(word) }.takeIf { it >= 0 }
        } else {
            null
        }
    }

    companion object {
        const val WORD_COUNT = 12
        private const val WORDLIST_SIZE = 2048
        private const val ENTROPY_BYTES = 16
        private const val CHECKSUM_BITS = 4
        private const val BITS_PER_WORD = 11

        private fun checksum(entropy: ByteArray): Int = (MessageDigest.getInstance("SHA-256").digest(entropy)[0].toInt() and 0xFF) ushr (8 - CHECKSUM_BITS)

        private fun writeBits(bits: BooleanArray, offset: Int, value: Int, count: Int) {
            for (i in 0 until count) bits[offset + i] = (value ushr (count - 1 - i)) and 1 == 1
        }

        private fun readBits(bits: BooleanArray, offset: Int, count: Int): Int {
            var value = 0
            for (i in 0 until count) value = (value shl 1) or if (bits[offset + i]) 1 else 0
            return value
        }
    }
}
