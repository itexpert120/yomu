package com.itexpert120.yomu.core.model

import kotlinx.serialization.Serializable

/**
 * A user-saved custom reading theme: a named pair of background/text colours (ARGB longs). Applying
 * one sets the reader to [ReaderThemeMode.Custom] with these colours. Stored app-globally.
 */
@Serializable
data class CustomReaderTheme(
    val id: String,
    val name: String,
    val background: Long,
    val text: Long,
)
