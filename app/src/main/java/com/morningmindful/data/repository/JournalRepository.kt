package com.morningmindful.data.repository

import com.morningmindful.data.dao.JournalEntryDao
import com.morningmindful.data.entity.JournalEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class JournalRepository(private val journalEntryDao: JournalEntryDao) {

    suspend fun insert(entry: JournalEntry): Long {
        return journalEntryDao.insert(entry)
    }

    suspend fun update(entry: JournalEntry) {
        journalEntryDao.update(entry)
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
            calculateStreak(entries.map { it.date })
        }
    }

    private fun calculateStreak(dates: List<LocalDate>): Int {
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
}
