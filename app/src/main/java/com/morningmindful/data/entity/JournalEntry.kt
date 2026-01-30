package com.morningmindful.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * Journal entry entity with validation constraints.
 */
@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["date"], unique = true)]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val date: LocalDate,

    val content: String,

    val wordCount: Int,

    val mood: String? = null,  // Emoji mood indicator

    val createdAt: Long = System.currentTimeMillis(),

    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Maximum content length (100KB - reasonable for text) */
        const val MAX_CONTENT_LENGTH = 100_000

        /** Maximum word count (prevents integer overflow issues) */
        const val MAX_WORD_COUNT = 50_000

        /**
         * Validates and sanitizes content before saving.
         * - Trims whitespace
         * - Truncates if too long
         * - Removes null characters
         */
        fun sanitizeContent(content: String): String {
            return content
                .trim()
                .take(MAX_CONTENT_LENGTH)
                .replace("\u0000", "") // Remove null chars
        }

        /**
         * Validates word count is within bounds.
         */
        fun sanitizeWordCount(count: Int): Int {
            return count.coerceIn(0, MAX_WORD_COUNT)
        }

        /**
         * Validates mood is one of the allowed values.
         */
        fun isValidMood(mood: String?): Boolean {
            if (mood == null) return true
            return Moods.ALL.any { it.first == mood }
        }
    }
}

/**
 * Available moods for journal entries
 */
object Moods {
    val GREAT = "😊" to "Great"
    val GOOD = "🙂" to "Good"
    val OKAY = "😐" to "Okay"
    val SAD = "😔" to "Sad"
    val STRESSED = "😰" to "Stressed"
    val ANGRY = "😠" to "Angry"
    val TIRED = "😴" to "Tired"
    val EXCITED = "🤩" to "Excited"
    val GRATEFUL = "🙏" to "Grateful"
    val ANXIOUS = "😟" to "Anxious"

    val ALL = listOf(GREAT, GOOD, OKAY, SAD, STRESSED, ANGRY, TIRED, EXCITED, GRATEFUL, ANXIOUS)
}
