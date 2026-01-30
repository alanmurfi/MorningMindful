package com.morningmindful

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.morningmindful.data.AppDatabase
import com.morningmindful.data.dao.JournalEntryDao
import com.morningmindful.data.entity.JournalEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Instrumented tests for the database layer.
 * Tests Room database operations and data persistence.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var journalEntryDao: JournalEntryDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Use in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        journalEntryDao = database.journalEntryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertEntry_returnsId() = runBlocking {
        val entry = JournalEntry(
            date = LocalDate.now(),
            content = "Test journal entry content",
            wordCount = 4
        )

        val id = journalEntryDao.insert(entry)

        assertTrue(id > 0)
    }

    @Test
    fun insertEntry_canRetrieveByDate() = runBlocking {
        val today = LocalDate.now()
        val entry = JournalEntry(
            date = today,
            content = "Test content for today",
            wordCount = 4
        )

        journalEntryDao.insert(entry)
        val retrieved = journalEntryDao.getEntryByDate(today).first()

        assertNotNull(retrieved)
        assertEquals("Test content for today", retrieved?.content)
        assertEquals(4, retrieved?.wordCount)
    }

    @Test
    fun insertEntry_withMood_persistsMood() = runBlocking {
        val today = LocalDate.now()
        val entry = JournalEntry(
            date = today,
            content = "Feeling great today!",
            wordCount = 3,
            mood = "😊"
        )

        journalEntryDao.insert(entry)
        val retrieved = journalEntryDao.getEntryByDate(today).first()

        assertNotNull(retrieved)
        assertEquals("😊", retrieved?.mood)
    }

    @Test
    fun updateEntry_updatesContent() = runBlocking {
        val today = LocalDate.now()
        val entry = JournalEntry(
            date = today,
            content = "Original content",
            wordCount = 2
        )

        val id = journalEntryDao.insert(entry)
        val updatedEntry = entry.copy(
            id = id,
            content = "Updated content",
            wordCount = 2
        )
        journalEntryDao.update(updatedEntry)

        val retrieved = journalEntryDao.getEntryByDate(today).first()
        assertEquals("Updated content", retrieved?.content)
    }

    @Test
    fun deleteEntry_removesFromDatabase() = runBlocking {
        val today = LocalDate.now()
        val entry = JournalEntry(
            date = today,
            content = "To be deleted",
            wordCount = 3
        )

        val id = journalEntryDao.insert(entry)
        journalEntryDao.deleteById(id)

        val retrieved = journalEntryDao.getEntryByDate(today).first()
        assertNull(retrieved)
    }

    @Test
    fun getAllEntries_returnsInDescendingDateOrder() = runBlocking {
        val day1 = LocalDate.of(2024, 1, 1)
        val day2 = LocalDate.of(2024, 1, 2)
        val day3 = LocalDate.of(2024, 1, 3)

        journalEntryDao.insert(JournalEntry(date = day1, content = "Day 1", wordCount = 2))
        journalEntryDao.insert(JournalEntry(date = day3, content = "Day 3", wordCount = 2))
        journalEntryDao.insert(JournalEntry(date = day2, content = "Day 2", wordCount = 2))

        val entries = journalEntryDao.getAllEntries().first()

        assertEquals(3, entries.size)
        assertEquals(day3, entries[0].date)
        assertEquals(day2, entries[1].date)
        assertEquals(day1, entries[2].date)
    }

    @Test
    fun getTotalEntryCount_returnsCorrectCount() = runBlocking {
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 1), content = "Entry 1", wordCount = 2))
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 2), content = "Entry 2", wordCount = 2))
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 3), content = "Entry 3", wordCount = 2))

        val count = journalEntryDao.getTotalEntryCount().first()

        assertEquals(3, count)
    }

    @Test
    fun getTotalWordCount_sumsCorrectly() = runBlocking {
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 1), content = "Entry 1", wordCount = 100))
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 2), content = "Entry 2", wordCount = 200))
        journalEntryDao.insert(JournalEntry(date = LocalDate.of(2024, 1, 3), content = "Entry 3", wordCount = 300))

        val totalWords = journalEntryDao.getTotalWordCount().first()

        assertEquals(600, totalWords)
    }

    @Test
    fun getRecentEntries_limitsCorrectly() = runBlocking {
        for (i in 1..10) {
            journalEntryDao.insert(
                JournalEntry(
                    date = LocalDate.of(2024, 1, i),
                    content = "Entry $i",
                    wordCount = i * 10
                )
            )
        }

        val recentEntries = journalEntryDao.getRecentEntries(5).first()

        assertEquals(5, recentEntries.size)
    }

    @Test
    fun uniqueDateIndex_replacesExistingEntry() = runBlocking {
        val today = LocalDate.now()

        journalEntryDao.insert(JournalEntry(date = today, content = "First entry", wordCount = 2))
        journalEntryDao.insert(JournalEntry(date = today, content = "Second entry", wordCount = 2))

        val entries = journalEntryDao.getAllEntries().first()
        assertEquals(1, entries.size)
        assertEquals("Second entry", entries[0].content)
    }
}
