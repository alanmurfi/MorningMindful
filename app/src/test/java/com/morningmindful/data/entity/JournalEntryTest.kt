package com.morningmindful.data.entity

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for JournalEntry validation and sanitization.
 */
class JournalEntryTest {

    @Test
    fun `sanitizeContent trims whitespace`() {
        val input = "  Hello World  "
        val result = JournalEntry.sanitizeContent(input)
        assertEquals("Hello World", result)
    }

    @Test
    fun `sanitizeContent removes null characters`() {
        val input = "Hello\u0000World"
        val result = JournalEntry.sanitizeContent(input)
        assertEquals("HelloWorld", result)
    }

    @Test
    fun `sanitizeContent truncates long content`() {
        val input = "a".repeat(JournalEntry.MAX_CONTENT_LENGTH + 1000)
        val result = JournalEntry.sanitizeContent(input)
        assertEquals(JournalEntry.MAX_CONTENT_LENGTH, result.length)
    }

    @Test
    fun `sanitizeContent preserves normal content`() {
        val input = "This is a normal journal entry with some text."
        val result = JournalEntry.sanitizeContent(input)
        assertEquals(input, result)
    }

    @Test
    fun `sanitizeWordCount returns 0 for negative values`() {
        val result = JournalEntry.sanitizeWordCount(-5)
        assertEquals(0, result)
    }

    @Test
    fun `sanitizeWordCount caps at max value`() {
        val result = JournalEntry.sanitizeWordCount(100_000)
        assertEquals(JournalEntry.MAX_WORD_COUNT, result)
    }

    @Test
    fun `sanitizeWordCount preserves valid values`() {
        val result = JournalEntry.sanitizeWordCount(200)
        assertEquals(200, result)
    }

    @Test
    fun `isValidMood returns true for null`() {
        assertTrue(JournalEntry.isValidMood(null))
    }

    @Test
    fun `isValidMood returns true for valid mood emoji`() {
        assertTrue(JournalEntry.isValidMood("😊"))
        assertTrue(JournalEntry.isValidMood("😔"))
        assertTrue(JournalEntry.isValidMood("🙏"))
    }

    @Test
    fun `isValidMood returns false for invalid mood`() {
        assertFalse(JournalEntry.isValidMood("invalid"))
        assertFalse(JournalEntry.isValidMood("🎉")) // Not in allowed list
    }

    @Test
    fun `Moods ALL contains expected number of moods`() {
        assertEquals(10, Moods.ALL.size)
    }

    @Test
    fun `MAX_CONTENT_LENGTH is reasonable`() {
        // 100KB is reasonable for text
        assertEquals(100_000, JournalEntry.MAX_CONTENT_LENGTH)
    }
}
