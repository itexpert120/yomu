package com.itexpert120.yomu.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorSurnameTest {
    @Test
    fun surnames() {
        assertEquals("Melville", authorSurname("Herman Melville"))
        assertEquals("Ishibumi", authorSurname("Ichiei Ishibumi and Miyama-Zero"))
        assertEquals("Curry", authorSurname("Charles Madison Curry, Erle Elsworth Clippinger"))
        assertEquals("Natsume", authorSurname("Natsume, Sōseki"))
        assertEquals("W3C®", authorSurname("W3C® (MIT, ERCIM, Keio)"))
        assertEquals("Eliot", authorSurname("T.S. Eliot"))
        assertEquals("Khayat", authorSurname("Pr David Khayat, Nathalie Hutter-Lardeau, Marina Khalil Fayad"))
        assertEquals("singNshong", authorSurname("singNshong"))
        assertEquals("津野海太郎", authorSurname("津野海太郎"))
    }

    @Test
    fun anUnknownAuthorHasNone() {
        assertEquals("", authorSurname("Unknown author"))
        assertEquals("", authorSurname("  "))
    }
}
