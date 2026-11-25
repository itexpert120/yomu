package com.itexpert120.yomu.core.designsystem

/**
 * What a segmented choice shows while its owner catches up. A tap moves the chip at once
 * ([shown]) and is handed to the owner a moment later ([takePending]), so the glide starts on the
 * tap's own frame and whatever the owner does with the choice (re-sorting a library, restyling a
 * page) lands once the chip has nearly arrived. Rapid taps coalesce into the last one. Until the
 * owner reports the delivered choice back ([external]), older values it reports are ignored, so the
 * chip never jumps back to a choice the user already left; if the owner never confirms (it refused
 * the choice), [giveUp] returns to what the owner says.
 */
internal class SegmentSelection(initial: Int) {
    var shown: Int = initial
        private set

    private var pending: Int? = null
    private var awaiting: Int? = null
    private var reported: Int = initial

    /** A tap on [index]. Returns false when it is already the shown choice. */
    fun tap(index: Int): Boolean {
        if (index == shown) return false
        shown = index
        pending = index
        return true
    }

    /** The tapped choice to hand to the owner now, if any; it is then awaited back. */
    fun takePending(): Int? {
        val choice = pending ?: return null
        pending = null
        awaiting = choice
        return choice
    }

    /** The owner's selection, [index], as it reports it. */
    fun external(index: Int) {
        reported = index
        when {
            pending != null -> Unit
            awaiting != null -> if (index == awaiting) awaiting = null
            else -> shown = index
        }
    }

    /** Stops waiting for the owner to confirm and shows what it last reported. */
    fun giveUp() {
        awaiting = null
        if (pending == null) shown = reported
    }

    val isAwaiting: Boolean get() = awaiting != null
}
