package com.itexpert120.yomu.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackSnapRestTest {
    @Test
    fun `a landing between the rests settles on the nearer one`() {
        assertEquals(0f, stackSnapRest(projected = 20f, low = 0f, high = 150f))
        assertEquals(150f, stackSnapRest(projected = 100f, low = 0f, high = 150f))
        assertEquals(-300f, stackSnapRest(projected = -200f, low = -300f, high = 0f))
        assertEquals(0f, stackSnapRest(projected = -60f, low = -300f, high = 0f))
    }

    @Test
    fun `a landing on or past a rest is left alone`() {
        assertNull(stackSnapRest(projected = 0f, low = 0f, high = 150f))
        assertNull(stackSnapRest(projected = 150f, low = 0f, high = 150f))
        assertNull(stackSnapRest(projected = 400f, low = 0f, high = 150f))
        assertNull(stackSnapRest(projected = -500f, low = -300f, high = 0f))
    }

    @Test
    fun `no range means no snap`() {
        assertNull(stackSnapRest(projected = 0f, low = 0f, high = 0f))
    }
}
