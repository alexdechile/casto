package com.example.casto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpRangeTest {

    @Test
    fun `missing header means full content`() {
        assertNull(HttpRange.parse(null, 1000))
    }

    @Test
    fun `parses closed range`() {
        val range = HttpRange.parse("bytes=0-99", 1000)!!

        assertEquals(0L, range.start)
        assertEquals(99L, range.end)
        assertEquals(100L, range.length)
    }

    @Test
    fun `open ended range goes to the last byte`() {
        val range = HttpRange.parse("bytes=500-", 1000)!!

        assertEquals(500L, range.start)
        assertEquals(999L, range.end)
    }

    @Test
    fun `end past total is clamped`() {
        val range = HttpRange.parse("bytes=900-5000", 1000)!!

        assertEquals(900L, range.start)
        assertEquals(999L, range.end)
    }

    @Test
    fun `suffix range returns the last bytes`() {
        val range = HttpRange.parse("bytes=-100", 1000)!!

        assertEquals(900L, range.start)
        assertEquals(999L, range.end)
    }

    @Test
    fun `single byte range`() {
        val range = HttpRange.parse("bytes=42-42", 1000)!!

        assertEquals(42L, range.start)
        assertEquals(42L, range.end)
        assertEquals(1L, range.length)
    }

    @Test
    fun `start past total is not satisfiable`() {
        assertNull(HttpRange.parse("bytes=1000-", 1000))
    }

    @Test
    fun `malformed header is ignored`() {
        assertNull(HttpRange.parse("bytes=abc-def", 1000))
    }

    @Test
    fun `unrelated header is ignored`() {
        assertNull(HttpRange.parse("items=0-99", 1000))
    }

    @Test
    fun `empty video is not satisfiable`() {
        assertNull(HttpRange.parse("bytes=0-", 0))
    }
}
