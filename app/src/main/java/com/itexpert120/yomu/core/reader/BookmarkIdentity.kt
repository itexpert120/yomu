package com.itexpert120.yomu.core.reader

import kotlin.math.abs

/** Shared by the durable toggle and its live indicator, including legacy resource-only anchors. */
object BookmarkIdentity {
    fun samePosition(
        href: String?,
        locator: String,
        progression: Double?,
        otherHref: String?,
        otherLocator: String,
        otherProgression: Double?,
    ): Boolean {
        if (href != otherHref) {
            if (href?.substringBefore('#') != otherHref?.substringBefore('#')) return false
            if (href?.contains('#') == true && otherHref?.contains('#') == true) return false
        }
        return if (progression != null && otherProgression != null) {
            abs(progression - otherProgression) < 0.01
        } else {
            locator == otherLocator
        }
    }
}
