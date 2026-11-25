package com.itexpert120.yomu.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.itexpert120.yomu.data.readaloud.ReadAloudSettings
import com.itexpert120.yomu.data.readaloud.ReadAloudSettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists read-aloud rate, pitch, and the voice chosen per language (device-local; not synced). */
@Singleton
class ReadAloudSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReadAloudSettingsStore {
    override val settings: Flow<ReadAloudSettings> = dataStore.data.map { prefs ->
        ReadAloudSettings(
            rate = (prefs[KeyRate] ?: 1f).coerceIn(ReadAloudSettings.MIN_RATE, ReadAloudSettings.MAX_RATE),
            pitch = (prefs[KeyPitch] ?: 1f).coerceIn(ReadAloudSettings.MIN_PITCH, ReadAloudSettings.MAX_PITCH),
            voices = decodeVoices(prefs[KeyVoices]),
        )
    }.distinctUntilChanged()

    override suspend fun setRate(rate: Float) {
        dataStore.edit { it[KeyRate] = rate.coerceIn(ReadAloudSettings.MIN_RATE, ReadAloudSettings.MAX_RATE) }
    }

    override suspend fun setPitch(pitch: Float) {
        dataStore.edit { it[KeyPitch] = pitch.coerceIn(ReadAloudSettings.MIN_PITCH, ReadAloudSettings.MAX_PITCH) }
    }

    override suspend fun setVoice(language: String, voiceName: String?) {
        dataStore.edit { prefs ->
            val voices = decodeVoices(prefs[KeyVoices])
            val updated = if (voiceName == null) voices - language else voices + (language to voiceName)
            prefs[KeyVoices] = encodeVoices(updated)
        }
    }

    private companion object {
        val KeyRate = floatPreferencesKey("read_aloud_rate")
        val KeyPitch = floatPreferencesKey("read_aloud_pitch")
        val KeyVoices = stringPreferencesKey("read_aloud_voices")
    }
}

// One "language=voice" per line; voice names are engine ids without line breaks.
internal fun decodeVoices(raw: String?): Map<String, String> = raw.orEmpty()
    .lineSequence()
    .mapNotNull { line ->
        val split = line.indexOf('=')
        if (split <= 0 || split == line.lastIndex) null else line.substring(0, split) to line.substring(split + 1)
    }
    .toMap()

internal fun encodeVoices(voices: Map<String, String>): String = voices.entries
    .sortedBy { it.key }
    .joinToString("\n") { "${it.key}=${it.value}" }
