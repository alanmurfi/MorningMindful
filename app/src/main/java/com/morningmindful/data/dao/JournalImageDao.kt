package com.morningmindful.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.morningmindful.data.entity.JournalImage
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: JournalImage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<JournalImage>): List<Long>

    @Delete
    suspend fun delete(image: JournalImage)

    @Query("DELETE FROM journal_images WHERE id = :imageId")
    suspend fun deleteById(imageId: Long)

    @Query("DELETE FROM journal_images WHERE entryId = :entryId")
    suspend fun deleteAllForEntry(entryId: Long)

    @Query("SELECT * FROM journal_images WHERE entryId = :entryId ORDER BY position ASC")
    fun getImagesForEntry(entryId: Long): Flow<List<JournalImage>>

    @Query("SELECT * FROM journal_images WHERE entryId = :entryId ORDER BY position ASC")
    suspend fun getImagesForEntrySync(entryId: Long): List<JournalImage>

    @Query("SELECT * FROM journal_images WHERE id = :imageId")
    suspend fun getById(imageId: Long): JournalImage?

    @Query("SELECT COUNT(*) FROM journal_images WHERE entryId = :entryId")
    suspend fun getImageCountForEntry(entryId: Long): Int

    @Query("SELECT * FROM journal_images")
    suspend fun getAllImages(): List<JournalImage>

    @Query("SELECT MAX(position) FROM journal_images WHERE entryId = :entryId")
    suspend fun getMaxPosition(entryId: Long): Int?

    @Query("UPDATE journal_images SET position = :newPosition WHERE id = :imageId")
    suspend fun updatePosition(imageId: Long, newPosition: Int)
}
