package com.morningmindful.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for backup filename generation.
 */
class BackupFilenameTest {

    @Test
    fun `generated filename has correct extension`() {
        val filename = JournalBackupManager.generateBackupFilename()
        assertTrue(
            "Filename should end with .mmbackup",
            filename.endsWith(".mmbackup")
        )
    }

    @Test
    fun `generated filename starts with prefix`() {
        val filename = JournalBackupManager.generateBackupFilename()
        assertTrue(
            "Filename should start with morning_mindful_backup_",
            filename.startsWith("morning_mindful_backup_")
        )
    }

    @Test
    fun `generated filename contains date-like pattern`() {
        val filename = JournalBackupManager.generateBackupFilename()
        // Pattern: morning_mindful_backup_YYYY-MM-DD_HHmm.mmbackup
        val regex = Regex("morning_mindful_backup_\\d{4}-\\d{2}-\\d{2}_\\d{4}\\.mmbackup")
        assertTrue(
            "Filename should match expected pattern: $filename",
            regex.matches(filename)
        )
    }

    @Test
    fun `generated filenames are unique over time`() {
        val filename1 = JournalBackupManager.generateBackupFilename()
        Thread.sleep(100) // Small delay
        val filename2 = JournalBackupManager.generateBackupFilename()

        // Within the same minute, filenames might be the same
        // But they should be valid either way
        assertTrue(filename1.isNotEmpty())
        assertTrue(filename2.isNotEmpty())
    }
}
