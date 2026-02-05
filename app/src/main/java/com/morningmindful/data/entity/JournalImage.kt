package com.morningmindful.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Image attached to a journal entry.
 * Images are stored as files in app's internal storage, this entity stores the path.
 */
@Entity(
    tableName = "journal_images",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["entryId"])]
)
data class JournalImage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** ID of the journal entry this image belongs to */
    val entryId: Long,

    /** Relative path to the image file in app's internal storage */
    val filePath: String,

    /** Original filename (for display purposes) */
    val originalName: String? = null,

    /** MIME type of the image */
    val mimeType: String = "image/jpeg",

    /** File size in bytes */
    val fileSize: Long = 0,

    /** Width in pixels (for layout optimization) */
    val width: Int = 0,

    /** Height in pixels (for layout optimization) */
    val height: Int = 0,

    /** Order/position in the entry (for multiple images) */
    val position: Int = 0,

    /** Timestamp when image was added */
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** Maximum images per entry */
        const val MAX_IMAGES_PER_ENTRY = 10

        /** Maximum file size (5MB) */
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L

        /** Target size for compressed images */
        const val TARGET_WIDTH = 1920
        const val TARGET_HEIGHT = 1920

        /** Compression quality (0-100) */
        const val COMPRESSION_QUALITY = 85
    }
}
