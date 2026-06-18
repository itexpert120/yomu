package com.itexpert120.yomu.data.reader.readium

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only place that touches Readium for import: opens an EPUB and pulls metadata + cover.
 * Everything else in the app stays in Yomu-owned types.
 */
@Singleton
class ReadiumMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class ExtractedMetadata(
        val title: String?,
        val author: String?,
        val description: String?,
        val language: String?,
        val publisher: String?,
        val cover: Bitmap?,
    )

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
    )

    /** Returns extracted metadata, or null if the file can't be opened as a publication. */
    suspend fun extract(file: File): ExtractedMetadata? {
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false)).getOrElse { return null }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return null
            }
        return try {
            val metadata = publication.metadata
            ExtractedMetadata(
                title = metadata.title,
                author = metadata.authors.mapNotNull { it.name }.joinToString(", ").ifBlank { null },
                description = metadata.description,
                language = metadata.languages.firstOrNull(),
                publisher = metadata.publishers.mapNotNull { it.name }.firstOrNull(),
                cover = publication.cover(),
            )
        } finally {
            publication.close()
        }
    }
}
