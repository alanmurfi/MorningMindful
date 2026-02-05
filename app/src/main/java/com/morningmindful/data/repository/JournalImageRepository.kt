package com.morningmindful.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.morningmindful.data.dao.JournalImageDao
import com.morningmindful.data.entity.JournalImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalImageRepository @Inject constructor(
    private val journalImageDao: JournalImageDao,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "JournalImageRepository"
        private const val IMAGES_DIR = "journal_images"
    }

    /**
     * Get the directory where images are stored
     */
    private fun getImagesDir(): File {
        val dir = File(context.filesDir, IMAGES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get images for a specific journal entry
     */
    fun getImagesForEntry(entryId: Long): Flow<List<JournalImage>> {
        return journalImageDao.getImagesForEntry(entryId)
    }

    /**
     * Get images for a specific journal entry (sync)
     */
    suspend fun getImagesForEntrySync(entryId: Long): List<JournalImage> {
        return journalImageDao.getImagesForEntrySync(entryId)
    }

    /**
     * Get all images (for backup)
     */
    suspend fun getAllImages(): List<JournalImage> {
        return journalImageDao.getAllImages()
    }

    /**
     * Add an image from a content URI to a journal entry
     */
    suspend fun addImageFromUri(entryId: Long, uri: Uri): JournalImage? = withContext(Dispatchers.IO) {
        try {
            // Check image count limit
            val currentCount = journalImageDao.getImageCountForEntry(entryId)
            if (currentCount >= JournalImage.MAX_IMAGES_PER_ENTRY) {
                Log.w(TAG, "Maximum images per entry reached")
                return@withContext null
            }

            // Generate unique filename
            val filename = "${UUID.randomUUID()}.jpg"
            val outputFile = File(getImagesDir(), filename)

            // Load and compress image
            val (bitmap, originalWidth, originalHeight) = loadAndProcessBitmap(uri)
            if (bitmap == null) {
                Log.e(TAG, "Failed to load bitmap from URI")
                return@withContext null
            }

            // Save compressed image
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JournalImage.COMPRESSION_QUALITY, out)
            }
            bitmap.recycle()

            val fileSize = outputFile.length()
            if (fileSize > JournalImage.MAX_FILE_SIZE) {
                Log.w(TAG, "Compressed image still too large: $fileSize bytes")
                outputFile.delete()
                return@withContext null
            }

            // Get next position
            val maxPosition = journalImageDao.getMaxPosition(entryId) ?: -1
            val position = maxPosition + 1

            // Create and insert image entity
            val image = JournalImage(
                entryId = entryId,
                filePath = filename,
                originalName = getOriginalFilename(uri),
                mimeType = "image/jpeg",
                fileSize = fileSize,
                width = bitmap.width,
                height = bitmap.height,
                position = position
            )

            val id = journalImageDao.insert(image)
            Log.d(TAG, "Added image: $filename for entry $entryId")

            return@withContext image.copy(id = id)

        } catch (e: Exception) {
            Log.e(TAG, "Error adding image", e)
            return@withContext null
        }
    }

    /**
     * Load bitmap from URI, resize if needed, and fix rotation
     */
    private fun loadAndProcessBitmap(uri: Uri): Triple<Bitmap?, Int, Int> {
        try {
            // First, get image dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            // Calculate sample size for efficient loading
            val sampleSize = calculateSampleSize(
                originalWidth, originalHeight,
                JournalImage.TARGET_WIDTH, JournalImage.TARGET_HEIGHT
            )

            // Load sampled bitmap
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, loadOptions)
            } ?: return Triple(null, 0, 0)

            // Fix rotation based on EXIF data
            bitmap = fixRotation(uri, bitmap)

            // Scale down if still too large
            bitmap = scaleIfNeeded(bitmap)

            return Triple(bitmap, originalWidth, originalHeight)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap", e)
            return Triple(null, 0, 0)
        }
    }

    /**
     * Calculate optimal sample size for loading large images
     */
    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        if (height > targetHeight || width > targetWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / sampleSize >= targetHeight && halfWidth / sampleSize >= targetWidth) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    /**
     * Fix image rotation based on EXIF data
     */
    private fun fixRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap
            val exif = ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation == 0f) return bitmap

            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            return rotated

        } catch (e: Exception) {
            Log.w(TAG, "Error fixing rotation", e)
            return bitmap
        }
    }

    /**
     * Scale bitmap if it exceeds target dimensions
     */
    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= JournalImage.TARGET_WIDTH && bitmap.height <= JournalImage.TARGET_HEIGHT) {
            return bitmap
        }

        val scale = minOf(
            JournalImage.TARGET_WIDTH.toFloat() / bitmap.width,
            JournalImage.TARGET_HEIGHT.toFloat() / bitmap.height
        )

        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    /**
     * Get original filename from URI
     */
    private fun getOriginalFilename(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete an image by ID
     */
    suspend fun deleteImage(imageId: Long) = withContext(Dispatchers.IO) {
        try {
            val image = journalImageDao.getById(imageId) ?: return@withContext

            // Delete file
            val file = File(getImagesDir(), image.filePath)
            if (file.exists()) {
                file.delete()
            }

            // Delete from database
            journalImageDao.deleteById(imageId)
            Log.d(TAG, "Deleted image: ${image.filePath}")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image", e)
        }
    }

    /**
     * Delete all images for an entry
     */
    suspend fun deleteAllForEntry(entryId: Long) = withContext(Dispatchers.IO) {
        try {
            val images = journalImageDao.getImagesForEntrySync(entryId)
            images.forEach { image ->
                val file = File(getImagesDir(), image.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            journalImageDao.deleteAllForEntry(entryId)
            Log.d(TAG, "Deleted all images for entry $entryId")

        } catch (e: Exception) {
            Log.e(TAG, "Error deleting images for entry", e)
        }
    }

    /**
     * Get the full file path for an image
     */
    fun getImageFile(image: JournalImage): File {
        return File(getImagesDir(), image.filePath)
    }

    /**
     * Get the full file path for an image by filename
     */
    fun getImageFile(filePath: String): File {
        return File(getImagesDir(), filePath)
    }

    /**
     * Insert an image (for restore from backup)
     */
    suspend fun insert(image: JournalImage): Long {
        return journalImageDao.insert(image)
    }

    /**
     * Insert multiple images (for restore from backup)
     */
    suspend fun insertAll(images: List<JournalImage>): List<Long> {
        return journalImageDao.insertAll(images)
    }
}
