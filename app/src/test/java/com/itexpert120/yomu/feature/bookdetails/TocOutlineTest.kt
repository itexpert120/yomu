package com.itexpert120.yomu.feature.bookdetails

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TocOutlineTest {
    // 0 Foreword, 1 Volume 1 [2 Ch1, 3 Ch2 [4 Section]], 5 Volume 2 [6 Ch3, 7 Ch4], 8 Afterword
    private val outline = TocOutline.of(listOf(0, 0, 1, 1, 2, 0, 1, 1, 0))

    @Test
    fun descendantsRunToTheNextEntryAtTheSameDepthOrShallower() {
        assertArrayEquals(intArrayOf(0, 3, 0, 1, 0, 2, 0, 0, 0), outline.descendants)
        assertTrue(outline.hasChildren(1))
        assertFalse(outline.hasChildren(0))
        assertTrue(outline.isUnder(4, 1))
        assertFalse(outline.isUnder(5, 1))
    }

    @Test
    fun ascendingIsDocumentOrder() {
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8), outline.order(descending = false))
    }

    @Test
    fun descendingReversesSiblingsButKeepsVolumesAboveTheirChapters() {
        assertArrayEquals(intArrayOf(8, 5, 7, 6, 1, 3, 4, 2, 0), outline.order(descending = true))
    }

    @Test
    fun collapsedVolumesHideTheirWholeSubtreeInEitherOrder() {
        assertArrayEquals(intArrayOf(0, 1, 5, 6, 7, 8), outline.visible(outline.order(false), setOf(1)))
        assertArrayEquals(intArrayOf(8, 5, 7, 6, 1, 0), outline.visible(outline.order(true), setOf(1)))
        // A collapsed section inside an open volume.
        assertArrayEquals(intArrayOf(0, 1, 2, 3, 5, 8), outline.visible(outline.order(false), setOf(3, 5)))
        // Collapsed inside collapsed: the outer one wins.
        assertArrayEquals(intArrayOf(0, 1, 5, 6, 7, 8), outline.visible(outline.order(false), setOf(1, 3)))
    }

    @Test
    fun irregularDepthJumpsStillNest() {
        // A volume whose chapters jump straight to depth 2, then a shallower entry closes it.
        val jumpy = TocOutline.of(listOf(0, 2, 2, 1, 0))
        assertArrayEquals(intArrayOf(3, 0, 0, 0, 0), jumpy.descendants)
        assertArrayEquals(intArrayOf(4, 0, 3, 2, 1), jumpy.order(descending = true))
    }

    @Test
    fun aLongSerialStaysLinear() {
        // 1,500 entries: 10 volumes of 149 chapters each, plus front matter.
        val depths = buildList {
            add(0)
            repeat(10) {
                add(0)
                repeat(149) { add(1) }
            }
        }
        val serial = TocOutline.of(depths)
        assertEquals(1_501, serial.size)
        val volumes = depths.indices.filter { serial.hasChildren(it) }
        assertEquals(10, volumes.size)
        assertEquals(11, serial.visible(serial.order(false), volumes.toSet()).size)
        assertEquals(1 + 10 + 149, serial.visible(serial.order(true), volumes.drop(1).toSet()).size)
        // Deep, malformed nesting is no problem for the (non-recursive) walk.
        val deep = TocOutline.of(List(5_000) { it })
        assertEquals(4_999, deep.descendants[0])
        assertEquals(4_999, deep.order(descending = true).last())
    }
}
