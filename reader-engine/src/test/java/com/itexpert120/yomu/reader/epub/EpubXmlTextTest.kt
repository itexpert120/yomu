package com.itexpert120.yomu.reader.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class EpubXmlTextTest {
    @Test
    fun byteOrderMarkAndLeadingWhitespaceGoBeforeParsing() {
        assertEquals("<?xml version=\"1.0\"?><a/>", EpubBook.withoutLeadingJunk("﻿<?xml version=\"1.0\"?><a/>"))
        assertEquals("<?xml version=\"1.0\"?><a/>", EpubBook.withoutLeadingJunk("\r\n  <?xml version=\"1.0\"?><a/>"))
        assertEquals("<a> x </a>", EpubBook.withoutLeadingJunk("<a> x </a>"))
    }
}
