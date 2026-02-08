package com.morningmindful.ui.journal

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for word counting logic.
 *
 * The word counting algorithm supports:
 * - Standard space-separated words
 * - CJK (Chinese, Japanese, Korean) characters - each counts as one word
 */
class WordCountTest {

    /**
     * Count words in text, with support for CJK characters.
     * This mirrors the logic in JournalViewModel.
     */
    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0

        var count = 0
        var inWord = false

        for (char in text) {
            when {
                isCjkCharacter(char) -> {
                    if (inWord) {
                        count++
                        inWord = false
                    }
                    count++
                }
                char.isWhitespace() -> {
                    if (inWord) {
                        count++
                        inWord = false
                    }
                }
                else -> {
                    inWord = true
                }
            }
        }

        if (inWord) count++

        return count
    }

    private fun isCjkCharacter(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x4E00..0x9FFF) ||    // CJK Unified Ideographs
               (codePoint in 0x3400..0x4DBF) ||    // CJK Extension A
               (codePoint in 0x3000..0x303F) ||    // CJK Symbols and Punctuation
               (codePoint in 0x3040..0x309F) ||    // Hiragana
               (codePoint in 0x30A0..0x30FF) ||    // Katakana
               (codePoint in 0xAC00..0xD7AF) ||    // Hangul Syllables (Korean)
               (codePoint in 0x1100..0x11FF)       // Hangul Jamo (Korean)
    }

    // Basic tests

    @Test
    fun `empty string returns 0`() {
        assertEquals(0, countWords(""))
    }

    @Test
    fun `blank string returns 0`() {
        assertEquals(0, countWords("   "))
        assertEquals(0, countWords("\t\n"))
    }

    @Test
    fun `single word returns 1`() {
        assertEquals(1, countWords("hello"))
    }

    @Test
    fun `multiple words separated by space`() {
        assertEquals(3, countWords("hello world today"))
    }

    @Test
    fun `multiple spaces between words`() {
        assertEquals(3, countWords("hello   world    today"))
    }

    @Test
    fun `words with newlines`() {
        assertEquals(3, countWords("hello\nworld\ntoday"))
    }

    @Test
    fun `words with tabs`() {
        assertEquals(3, countWords("hello\tworld\ttoday"))
    }

    @Test
    fun `leading and trailing spaces`() {
        assertEquals(2, countWords("  hello world  "))
    }

    // CJK tests

    @Test
    fun `chinese characters count individually`() {
        assertEquals(2, countWords("你好"))  // 2 characters = 2 words
    }

    @Test
    fun `japanese hiragana counts individually`() {
        assertEquals(4, countWords("こんにちは".substring(0, 4)))  // Each hiragana is a word
    }

    @Test
    fun `korean hangul counts individually`() {
        assertEquals(2, countWords("안녕"))  // 2 characters = 2 words
    }

    @Test
    fun `mixed english and chinese`() {
        // "Hello 世界" = 1 english word + 2 chinese characters = 3
        assertEquals(3, countWords("Hello 世界"))
    }

    @Test
    fun `chinese sentence`() {
        // "今天天气很好" = 6 characters = 6 words
        assertEquals(6, countWords("今天天气很好"))
    }

    // Edge cases

    @Test
    fun `punctuation attached to word`() {
        // "Hello," and "world!" are treated as 2 words (punctuation is part of word)
        assertEquals(2, countWords("Hello, world!"))
    }

    @Test
    fun `numbers count as words`() {
        assertEquals(4, countWords("I have 3 apples"))
    }

    @Test
    fun `hyphenated words count as one`() {
        assertEquals(3, countWords("well-known fact here"))
    }

    @Test
    fun `apostrophe in word`() {
        assertEquals(2, countWords("don't worry"))
    }

    @Test
    fun `long paragraph`() {
        val text = """
            Today I woke up feeling grateful for the little things in life.
            The sun was shining through my window, and I could hear birds singing.
            I decided to make the most of this beautiful day.
        """.trimIndent()

        // 35 words total
        assertEquals(35, countWords(text))
    }

    @Test
    fun `minimum words for journal completion`() {
        // Generate exactly 200 words
        val words = (1..200).joinToString(" ") { "word" }
        assertEquals(200, countWords(words))
    }
}
