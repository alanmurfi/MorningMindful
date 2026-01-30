package com.morningmindful.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.morningmindful.data.entity.JournalEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface JournalEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntry): Long

    @Update
    suspend fun update(entry: JournalEntry)

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    fun getEntryByDate(date: LocalDate): Flow<JournalEntry?>

    @Query("SELECT * FROM journal_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries ORDER BY date DESC LIMIT :limit")
    fun getRecentEntries(limit: Int): Flow<List<JournalEntry>>

    @Query("SELECT COUNT(*) FROM journal_entries")
    fun getTotalEntryCount(): Flow<Int>

    @Query("SELECT SUM(wordCount) FROM journal_entries")
    fun getTotalWordCount(): Flow<Int?>

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun getEntryById(id: Long): Flow<JournalEntry?>

    @Query("""
        SELECT COUNT(DISTINCT date) FROM journal_entries
        WHERE date >= :startDate AND date <= :endDate
    """)
    fun getStreakCount(startDate: LocalDate, endDate: LocalDate): Flow<Int>

    /**
     * Get all dates with journal entries, ordered descending.
     * Used for calculating streaks.
     */
    @Query("SELECT DISTINCT date FROM journal_entries ORDER BY date DESC")
    suspend fun getAllEntryDates(): List<LocalDate>
}
