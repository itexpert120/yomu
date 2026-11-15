package com.itexpert120.yomu.data.sync

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the phrase entropy, encrypted with a non-exportable Android Keystore key. If the app data is
 * restored onto another device the Keystore key doesn't come along, so the stored secret simply
 * fails to open and sync reads as off there until the phrase is entered again.
 */
@Singleton
internal class SyncSecretStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val present: Flow<Boolean> = dataStore.data.map { it[KeySecret] != null }

    suspend fun read(): ByteArray? {
        val stored = dataStore.data.first()[KeySecret] ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
        }.getOrElse {
            clear()
            null
        }
    }

    suspend fun write(entropy: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(entropy)
        dataStore.edit { it[KeySecret] = Base64.encodeToString(sealed, Base64.NO_WRAP) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KeySecret) }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "yomu_sync_phrase"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        val KeySecret = stringPreferencesKey("sync_phrase_sealed")
    }
}
