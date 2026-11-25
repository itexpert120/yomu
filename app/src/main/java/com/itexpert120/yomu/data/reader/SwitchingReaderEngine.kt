package com.itexpert120.yomu.data.reader

import android.content.Context
import com.itexpert120.yomu.core.reader.ReaderEngine
import com.itexpert120.yomu.core.reader.ReaderOpenRequest
import com.itexpert120.yomu.core.reader.ReaderOpenResult
import com.itexpert120.yomu.core.reader.ReaderPublicationCache
import com.itexpert120.yomu.core.reader.ReaderTocItem
import com.itexpert120.yomu.data.reader.foliate.FoliateReaderEngine
import com.itexpert120.yomu.data.reader.readium.ReadiumReaderEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which engine draws books: foliate (the default) or Readium, kept as a fallback while foliate's
 * reader is ported feature by feature. Device-local; switched from About → Diagnostics. Takes
 * effect the next time a book opens.
 */
@Singleton
class ReaderEngineChoice @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("reader_engine", Context.MODE_PRIVATE)

    private val _useReadium = MutableStateFlow(prefs.getBoolean(KEY_USE_READIUM, false))
    val useReadium: StateFlow<Boolean> = _useReadium.asStateFlow()

    fun setUseReadium(value: Boolean) {
        prefs.edit().putBoolean(KEY_USE_READIUM, value).apply()
        _useReadium.value = value
    }

    private companion object {
        const val KEY_USE_READIUM = "use_readium"
    }
}

/** The app's [ReaderEngine]: forwards to the engine [ReaderEngineChoice] picks. */
@Singleton
class SwitchingReaderEngine @Inject constructor(
    private val choice: ReaderEngineChoice,
    private val foliate: FoliateReaderEngine,
    private val readium: ReadiumReaderEngine,
) : ReaderEngine {
    private val current: ReaderEngine get() = if (choice.useReadium.value) readium else foliate

    override suspend fun open(request: ReaderOpenRequest): ReaderOpenResult? = current.open(request)

    override fun prepare(filePath: String, publicationCache: ReaderPublicationCache?) = current.prepare(filePath, publicationCache)

    override fun releasePrepared() {
        readium.releasePrepared()
        foliate.releasePrepared()
    }

    override fun warmUp() = current.warmUp()

    // Both engines key chapters by Readium's TOC.
    override suspend fun tableOfContents(filePath: String): List<ReaderTocItem>? = readium.tableOfContents(filePath)
}
