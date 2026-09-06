package com.itexpert120.yomu.core.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkIdentityTest {
    @Test fun unknownPositionUsesExactLocatorOnEitherSide() {
        assertTrue(BookmarkIdentity.samePosition("c", "position", null, "c", "position", 0.4))
        assertTrue(BookmarkIdentity.samePosition("c", "position", 0.4, "c", "position", null))
        assertFalse(BookmarkIdentity.samePosition("c", "one", null, "c", "two", 0.4))
    }

    @Test fun distinctLogicalSectionsStayDistinctAndLegacyPositionsStillMatch() {
        assertFalse(BookmarkIdentity.samePosition("c#a", "one", 0.4, "c#b", "two", 0.401))
        assertTrue(BookmarkIdentity.samePosition("c", "one", 0.4, "c#a", "two", 0.401))
    }
}
