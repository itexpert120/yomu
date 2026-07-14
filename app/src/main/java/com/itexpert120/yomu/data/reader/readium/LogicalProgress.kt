package com.itexpert120.yomu.data.reader.readium

internal data class SectionBoundary(
    val resourceIndex: Int,
    val startProgression: Double,
)

internal data class LogicalProgress(
    val bookProgress: Double,
    val sectionIndex: Int,
    val sectionProgress: Double?,
    val completed: Boolean,
)

internal data class WeightedProgressTarget(
    val resourceIndex: Int,
    val resourceProgress: Double,
)

internal fun weightedProgressTarget(
    totalProgression: Double,
    resourceWeights: List<Int>,
): WeightedProgressTarget? {
    if (resourceWeights.isEmpty()) return null
    val weights = resourceWeights.map { it.coerceAtLeast(1).toDouble() }
    if (totalProgression >= 1.0) {
        return WeightedProgressTarget(weights.lastIndex, 1.0)
    }
    val coordinate = totalProgression.coerceIn(0.0, 1.0) * weights.sum()
    var prefix = 0.0
    weights.forEachIndexed { index, weight ->
        val end = prefix + weight
        if (coordinate < end || index == weights.lastIndex) {
            return WeightedProgressTarget(
                resourceIndex = index,
                resourceProgress = ((coordinate - prefix) / weight).coerceIn(0.0, 1.0),
            )
        }
        prefix = end
    }
    return null
}

internal fun resolveResourceProgress(
    locatorProgress: Double?,
    visualPageProgress: Double?,
    measuredScrollProgress: Double? = null,
    useVisualPageProgress: Boolean,
): Double = if (useVisualPageProgress) {
    visualPageProgress ?: locatorProgress ?: 0.0
} else {
    measuredScrollProgress ?: locatorProgress ?: 0.0
}

internal fun completedSectionOnSequentialCrossing(
    pendingChapterId: String?,
    currentChapterId: String?,
    previousHref: String?,
    currentHref: String,
): String? = pendingChapterId?.takeIf {
    it != currentChapterId && previousHref != null && previousHref != currentHref
}

/** Pure position-weighted progress calculation shared by the Readium adapter and unit tests. */
internal fun calculateLogicalProgress(
    resourceIndex: Int,
    resourceProgress: Double,
    locatorProgress: Double,
    resourceWeights: List<Int>,
    sections: List<SectionBoundary>,
): LogicalProgress {
    if (resourceIndex !in resourceWeights.indices || resourceWeights.isEmpty()) {
        return LogicalProgress(0.0, -1, null, false)
    }
    val weights = resourceWeights.map { it.coerceAtLeast(1) }
    val prefix = IntArray(weights.size + 1)
    weights.forEachIndexed { i, weight -> prefix[i + 1] = prefix[i] + weight }
    val totalWeight = prefix.last().coerceAtLeast(1).toDouble()
    fun coordinate(index: Int, progression: Double): Double = prefix[index] + progression.coerceIn(0.0, 1.0) * weights[index]

    val coordinate = coordinate(resourceIndex, resourceProgress)
    val starts = sections.map { coordinate(it.resourceIndex, it.startProgression) }
    // The inclusive final visual page can complete this section, but it still belongs to the current
    // resource until navigation actually crosses the boundary.
    val sectionIndex = sections.indexOfLast { section ->
        section.resourceIndex < resourceIndex ||
            (
                section.resourceIndex == resourceIndex &&
                    section.startProgression <= locatorProgress + 1e-6
                )
    }
    val sectionProgress = sections.getOrNull(sectionIndex)?.let {
        val start = starts[sectionIndex]
        val end = starts.getOrNull(sectionIndex + 1) ?: totalWeight
        if (end <= start) {
            0.0
        } else {
            val raw = ((coordinate - start) / (end - start)).coerceIn(0.0, 1.0)
            val nextSection = sections.getOrNull(sectionIndex + 1)
            val finalResourceIndex = (nextSection?.resourceIndex?.minus(1) ?: weights.lastIndex)
            val reachedActualEnd =
                resourceIndex == finalResourceIndex && resourceProgress >= 0.999
            // Position weights can round a section to 100% while the locator is still in an earlier
            // resource. Reserve 100% for the actual final resource/page of the logical section.
            if (reachedActualEnd) 1.0 else raw.coerceAtMost(0.998)
        }
    }
    return LogicalProgress(
        bookProgress = (coordinate / totalWeight).coerceIn(0.0, 1.0),
        sectionIndex = sectionIndex,
        sectionProgress = sectionProgress,
        completed = resourceIndex == weights.lastIndex && resourceProgress >= 0.999,
    )
}
