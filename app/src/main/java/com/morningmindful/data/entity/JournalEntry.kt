package com.morningmindful.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

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
)

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
