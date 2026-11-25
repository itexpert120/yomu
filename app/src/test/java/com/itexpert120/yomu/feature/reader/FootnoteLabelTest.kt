package com.itexpert120.yomu.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class FootnoteLabelTest {
    @Test
    fun bracketedNumberBecomesTheLabel() {
        assertEquals(FootnoteText("1", "a.d. 410."), splitFootnoteLabel("[1] a.d. 410."))
        assertEquals(FootnoteText("12", "Ibid."), splitFootnoteLabel("(12) Ibid."))
        assertEquals(FootnoteText("a", "See above."), splitFootnoteLabel("[a]See above."))
    }

    @Test
    fun numberedAndSymbolLabelsAreStripped() {
        assertEquals(FootnoteText("3", "The finished edge."), splitFootnoteLabel("3. The finished edge."))
        assertEquals(FootnoteText(null, "A translator's note."), splitFootnoteLabel("* A translator's note."))
        assertEquals(FootnoteText(null, "Dagger note."), splitFootnoteLabel("† Dagger note."))
    }

    @Test
    fun textThatOnlyLooksNumberedIsKept() {
        assertEquals(FootnoteText(null, "1984 was the year."), splitFootnoteLabel("1984 was the year."))
        assertEquals(FootnoteText(null, "1.5 million copies."), splitFootnoteLabel("1.5 million copies."))
        assertEquals(FootnoteText(null, "[1]"), splitFootnoteLabel("[1]"))
        assertEquals(FootnoteText(null, "Plain note."), splitFootnoteLabel("  Plain note. "))
    }
}
