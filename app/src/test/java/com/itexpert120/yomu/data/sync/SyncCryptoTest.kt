package com.itexpert120.yomu.data.sync

import com.itexpert120.yomu.data.sync.crypto.SyncKeys
import com.itexpert120.yomu.data.sync.crypto.SyncPhrase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class SyncCryptoTest {
    private val codec = SyncPhrase.Codec(File("src/main/assets/bip39_english.txt").readLines().filter { it.isNotBlank() })

    // Official BIP39 test vectors (entropy -> mnemonic).
    private val vectors = listOf(
        "00000000000000000000000000000000" to
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        "7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f7f" to
            "legal winner thank year wave sausage worth useful legal winner thank yellow",
        "80808080808080808080808080808080" to
            "letter advice cage absurd amount doctor acoustic avoid letter advice cage above",
        "ffffffffffffffffffffffffffffffff" to
            "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong",
    )

    @Test
    fun encodesBip39Vectors() {
        vectors.forEach { (hex, words) ->
            assertEquals(words, codec.fromEntropy(hex.hexToBytes()).words.joinToString(" "))
        }
    }

    @Test
    fun parsesBip39VectorsBackToEntropy() {
        vectors.forEach { (hex, words) ->
            val parsed = codec.parse(words) as SyncPhrase.ParseResult.Valid
            assertArrayEquals(hex.hexToBytes(), parsed.phrase.entropy)
        }
    }

    @Test
    fun parseAcceptsCaseSpacingAndFourLetterPrefixes() {
        val parsed = codec.parse("  LEGA winn THANK year  wave sausage worth usef legal winner thank yell ")
        assertTrue(parsed is SyncPhrase.ParseResult.Valid)
    }

    @Test
    fun parseReportsProblems() {
        assertEquals(SyncPhrase.ParseResult.WrongWordCount(11), codec.parse("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo"))
        assertEquals(
            SyncPhrase.ParseResult.UnknownWords(listOf(2)),
            codec.parse("zoo qqqq zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong"),
        )
        // Swapping the last word breaks the checksum.
        assertEquals(SyncPhrase.ParseResult.BadChecksum, codec.parse("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo"))
    }

    @Test
    fun hkdfMatchesRfc5869TestCase1() {
        val okm = SyncKeys.hkdf(
            ikm = ByteArray(22) { 0x0b },
            salt = "000102030405060708090a0b0c".hexToBytes(),
            info = "f0f1f2f3f4f5f6f7f8f9".hexToBytes(),
            length = 42,
        )
        assertArrayEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865".hexToBytes(),
            okm,
        )
    }

    @Test
    fun tokenMatchesServerFormat() {
        val keys = SyncKeys(ByteArray(16) { it.toByte() })
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(keys.token))
    }

    @Test
    fun sealOpenRoundTripsAndBindsServerKey() {
        val keys = SyncKeys(ByteArray(16) { 7 })
        val key = keys.serverKey("progress/abc")
        val blob = keys.seal(key, "hello".toByteArray())
        assertEquals("hello", keys.open(key, blob).decodeToString())
        try {
            keys.open(keys.serverKey("progress/other"), blob)
            fail("A blob must not open under another record's name")
        } catch (_: Exception) {
        }
        try {
            SyncKeys(ByteArray(16) { 8 }).open(key, blob)
            fail("A blob must not open with another phrase's keys")
        } catch (_: Exception) {
        }
    }

    @Test
    fun serverKeysDifferAcrossPhrases() {
        assertNotEquals(SyncKeys(ByteArray(16)).serverKey("x"), SyncKeys(ByteArray(16) { 1 }).serverKey("x"))
    }

    @Test
    fun hlcEncodingSortsLikeComparison() {
        val stamps = listOf(Hlc(5, 0, "b"), Hlc(5, 1, "a"), Hlc(40, 0, "a"), Hlc(1_700_000_000_000, 2, "a"))
        assertEquals(stamps.sorted().map { it.encode() }, stamps.map { it.encode() }.sorted())
        stamps.forEach { assertEquals(it, Hlc.decode(it.encode())) }
    }

    @Test
    fun clockStaysAheadOfObservedRemoteStamps() {
        var wall = 100L
        val clock = HlcClock("me", now = { wall })
        val first = clock.tick()
        clock.observe(Hlc(500, 3, "other"))
        val next = clock.tick()
        assertTrue(next > Hlc(500, 3, "other"))
        assertTrue(next > first)
        wall = 1_000
        assertEquals(Hlc(1_000, 0, "me"), clock.tick())
    }

    private fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
