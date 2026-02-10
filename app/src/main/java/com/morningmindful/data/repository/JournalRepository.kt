package com.morningmindful.data.repository

import com.morningmindful.data.dao.JournalEntryDao
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.util.PerformanceTraces
import com.morningmindful.util.addMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class JournalRepository(private val journalEntryDao: JournalEntryDao) {

    suspend fun insert(entry: JournalEntry): Long {
        return PerformanceTraces.traceSuspend("save_journal_entry") { trace ->
            trace.addMetric("word_count", entry.wordCount.toLong())
            journalEntryDao.insert(entry)
        }
    }

    suspend fun update(entry: JournalEntry) {
        PerformanceTraces.traceSuspend("update_journal_entry") { trace ->
            trace.addMetric("word_count", entry.wordCount.toLong())
            journalEntryDao.update(entry)
        }
    }

    fun getTodayEntry(): Flow<JournalEntry?> {
        return journalEntryDao.getEntryByDate(LocalDate.now())
    }

    fun getEntryByDate(date: LocalDate): Flow<JournalEntry?> {
        return journalEntryDao.getEntryByDate(date)
    }

    fun getAllEntries(): Flow<List<JournalEntry>> {
        return journalEntryDao.getAllEntries()
    }

    fun getRecentEntries(limit: Int = 30): Flow<List<JournalEntry>> {
        return journalEntryDao.getRecentEntries(limit)
    }

    fun getTotalEntryCount(): Flow<Int> {
        return journalEntryDao.getTotalEntryCount()
    }

    fun getTotalWordCount(): Flow<Int?> {
        return journalEntryDao.getTotalWordCount()
    }

    suspend fun deleteById(id: Long) {
        journalEntryDao.deleteById(id)
    }

    fun getEntryById(id: Long): Flow<JournalEntry?> {
        return journalEntryDao.getEntryById(id)
    }

    /**
     * Calculate current streak (consecutive days of journaling ending today or yesterday).
     */
    fun getCurrentStreak(): Flow<Int> {
        return journalEntryDao.getAllEntries().map { entries ->
            calculateCurrentStreak(entries.map { it.date })
        }
    }

    /**
     * Calculate longest streak ever achieved.
     */
    fun getLongestStreak(): Flow<Int> {
        return journalEntryDao.getAllEntries().map { entries ->
            calculateLongestStreak(entries.map { it.date })
        }
    }

    /**
     * Get streak statistics as a data class.
     */
    fun getStreakStats(): Flow<StreakStats> {
        return journalEntryDao.getAllEntries().map { entries ->
            val dates = entries.map { it.date }
            StreakStats(
                currentStreak = calculateCurrentStreak(dates),
                longestStreak = calculateLongestStreak(dates),
                totalEntries = entries.size,
                totalWords = entries.sumOf { it.wordCount }
            )
        }
    }

    private fun calculateCurrentStreak(dates: List<LocalDate>): Int {
        if (dates.isEmpty()) return 0

        val sortedDates = dates.sortedDescending().distinct()
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)

        // Streak must start from today or yesterday
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

    /**
     * Get all entry dates for duplicate checking during import.
     */
    suspend fun getAllEntryDates(): Set<LocalDate> {
        return journalEntryDao.getAllEntriesOnce().map { it.date }.toSet()
    }

    /**
     * Get all entries for export (non-Flow, one-time).
     */
    suspend fun getAllEntriesForExport(): List<JournalEntry> {
        return journalEntryDao.getAllEntriesOnce()
    }

    /**
     * Insert multiple entries (for import).
     */
    suspend fun insertAll(entries: List<JournalEntry>) {
        entries.forEach { entry ->
            journalEntryDao.insert(entry)
        }
    }
}

/**
 * Data class containing streak statistics.
 */
data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalEntries: Int,
    val totalWords: Int
)
