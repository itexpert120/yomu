package com.itexpert120.yomu.data.sync.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Everything a device derives from the phrase entropy (HKDF-SHA256, one label per purpose):
 * - [token]: sent to Convex on every call; the server stores only its SHA-256 and uses it to find
 *   the group. Proves membership, reveals nothing about the data.
 * - the encryption key: AES-256-GCM for record contents. Never leaves the device.
 * - the naming key: HMAC for record names, so the server can't tell which books you own.
 */
class SyncKeys(entropy: ByteArray) {
    val token: String
    private val encryptionKey: SecretKeySpec
    private val namingKey: SecretKeySpec

    init {
        token = base64Url(hkdf(entropy, SALT, "yomu-sync/token/v1", 32))
        encryptionKey = SecretKeySpec(hkdf(entropy, SALT, "yomu-sync/encrypt/v1", 32), "AES")
        namingKey = SecretKeySpec(hkdf(entropy, SALT, "yomu-sync/name/v1", 32), "HmacSHA256")
    }

    /** The server-side name for logical record [name]: deterministic, unlinkable without the phrase. */
    fun serverKey(name: String): String = base64Url(hmac(namingKey, name.toByteArray(Charsets.UTF_8)))

    /** nonce || ciphertext+tag, base64url. [serverKey] is bound as associated data so blobs can't be swapped. */
    fun seal(serverKey: String, plaintext: ByteArray, random: SecureRandom = SecureRandom()): String {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(serverKey.toByteArray(Charsets.UTF_8))
        return base64Url(nonce + cipher.doFinal(plaintext))
    }

    /** Throws if the blob was tampered with, belongs to another key, or another group. */
    fun open(serverKey: String, blob: String): ByteArray {
        val bytes = Base64.getUrlDecoder().decode(blob)
        require(bytes.size > NONCE_BYTES) { "Sync blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, GCMParameterSpec(TAG_BITS, bytes, 0, NONCE_BYTES))
        cipher.updateAAD(serverKey.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes, NONCE_BYTES, bytes.size - NONCE_BYTES)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private val SALT = "yomu-sync".toByteArray(Charsets.UTF_8)

        /** RFC 5869 HKDF with HMAC-SHA256. */
        internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int): ByteArray = hkdf(ikm, salt, info.toByteArray(Charsets.UTF_8), length)

        internal fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
            val prk = hmac(SecretKeySpec(salt.takeIf { it.isNotEmpty() } ?: ByteArray(32), "HmacSHA256"), ikm)
            val prkKey = SecretKeySpec(prk, "HmacSHA256")
            val out = ByteArray(length)
            var previous = ByteArray(0)
            var written = 0
            var counter = 1
            while (written < length) {
                previous = hmac(prkKey, previous + info + byteArrayOf(counter.toByte()))
                val n = minOf(previous.size, length - written)
                previous.copyInto(out, written, 0, n)
                written += n
                counter++
            }
            return out
        }

        private fun hmac(key: SecretKeySpec, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
            init(key)
            doFinal(data)
        }

        private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
