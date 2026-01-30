package com.morningmindful.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/**
 * Unit tests for streak calculation logic.
 *
 * These tests verify the streak calculation algorithms work correctly
 * without needing a database or Android context.
 */
class StreakCalculationTest {

    // Helper to calculate current streak (mirrors JournalRepository logic)
    private fun calculateCurrentStreak(dates: List<LocalDate>, today: LocalDate = LocalDate.now()): Int {
        if (dates.isEmpty()) return 0

        val sortedDates = dates.sortedDescending().distinct()
        val yesterday = today.minusDays(1)

        val firstDate = sortedDates.firstOrNull() ?: return 0
        if (firstDate != today && firstDate != yesterday) {
            return 0
        }

        var streak = 1
        var currentDate = firstDate

        for (i in 1 until sortedDates.size) {
            val nextDate = sortedDates[i]
            if (currentDate.minusDays(1) == nextDate) {
                streak++
                currentDate = nextDate
            } else {
                break
            }
        }

        return streak
    }

    // Helper to calculate longest streak (mirrors JournalRepository logic)
    private fun calculateLongestStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        val sortedDates = dates.sorted().distinct()
        var longestStreak = 1
        var currentStreak = 1

        for (i in 1 until sortedDates.size) {
            if (sortedDates[i - 1].plusDays(1) == sortedDates[i]) {
                currentStreak++
                longestStreak = maxOf(longestStreak, currentStreak)
            } else {
                currentStreak = 1
            }
        }

        return longestStreak
    }

    @Test
    fun `current streak is 0 for empty list`() {
        assertEquals(0, calculateCurrentStreak(emptyList()))
    }

    @Test
    fun `current streak is 1 for single entry today`() {
        val today = LocalDate.now()
        assertEquals(1, calculateCurrentStreak(listOf(today), today))
    }

    @Test
    fun `current streak is 1 for single entry yesterday`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        assertEquals(1, calculateCurrentStreak(listOf(yesterday), today))
    }

    @Test
    fun `current streak is 0 for entry 2 days ago only`() {
        val today = LocalDate.now()
        val twoDaysAgo = today.minusDays(2)
        assertEquals(0, calculateCurrentStreak(listOf(twoDaysAgo), today))
    }

    @Test
    fun `current streak counts consecutive days`() {
        val today = LocalDate.now()
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusDays(2),
            today.minusDays(3)
        )
        assertEquals(4, calculateCurrentStreak(dates, today))
    }

    @Test
    fun `current streak breaks on gap`() {
        val today = LocalDate.now()
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusDays(3), // Gap - skipped day 2
            today.minusDays(4)
        )
        assertEquals(2, calculateCurrentStreak(dates, today))
    }

    @Test
    fun `current streak starting from yesterday`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val dates = listOf(
            yesterday,
            today.minusDays(2),
            today.minusDays(3)
        )
        assertEquals(3, calculateCurrentStreak(dates, today))
    }

    @Test
    fun `longest streak is 0 for empty list`() {
        assertEquals(0, calculateLongestStreak(emptyList()))
    }

    @Test
    fun `longest streak is 1 for single entry`() {
        assertEquals(1, calculateLongestStreak(listOf(LocalDate.now())))
    }

    @Test
    fun `longest streak finds consecutive days`() {
        val today = LocalDate.now()
        val dates = listOf(
            today,
            today.minusDays(1),
            today.minusDays(2)
        )
        assertEquals(3, calculateLongestStreak(dates))
    }

    @Test
    fun `longest streak finds best streak in history`() {
        val today = LocalDate.now()
        val dates = listOf(
            // Current streak of 2
            today,
            today.minusDays(1),
            // Gap
            // Old streak of 5 (longest)
            today.minusDays(10),
            today.minusDays(11),
            today.minusDays(12),
            today.minusDays(13),
            today.minusDays(14)
        )
        assertEquals(5, calculateLongestStreak(dates))
    }

    @Test
    fun `longest streak handles multiple streaks`() {
        val base = LocalDate.of(2024, 1, 1)
        val dates = listOf(
            // Streak 1: 3 days
            base, base.plusDays(1), base.plusDays(2),
            // Gap
            // Streak 2: 5 days (longest)
            base.plusDays(10), base.plusDays(11), base.plusDays(12),
            base.plusDays(13), base.plusDays(14),
            // Gap
            // Streak 3: 2 days
            base.plusDays(20), base.plusDays(21)
        )
        assertEquals(5, calculateLongestStreak(dates))
    }

    @Test
    fun `streak handles duplicate dates`() {
        val today = LocalDate.now()
        val dates = listOf(
            today,
            today, // Duplicate
            today.minusDays(1),
            today.minusDays(1) // Duplicate
        )
        assertEquals(2, calculateCurrentStreak(dates, today))
    }

    @Test
    fun `streak handles unsorted dates`() {
        val today = LocalDate.now()
        val dates = listOf(
            today.minusDays(2),
            today,
            today.minusDays(1),
            today.minusDays(3)
        )
        assertEquals(4, calculateCurrentStreak(dates, today))
    }
}
