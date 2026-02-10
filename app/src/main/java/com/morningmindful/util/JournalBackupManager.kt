package com.morningmindful.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.entity.JournalImage
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages secure export and import of journal entries.
 * Uses AES-256-GCM encryption with PBKDF2 key derivation.
 *
 * Backup versions:
 * - Version 1: Text entries only
 * - Version 2: Text entries + images (optional)
 */
object JournalBackupManager {

    private const val TAG = "JournalBackupManager"
    private const val BACKUP_VERSION_V1 = 1
    private const val BACKUP_VERSION_V2 = 2
    private const val CURRENT_BACKUP_VERSION = 2
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val ITERATION_COUNT = 100_000
    private const val GCM_TAG_LENGTH = 128
    private const val SALT_LENGTH = 32
    private const val IV_LENGTH = 12

    /**
     * Result of an export operation.
     */
    sealed class ExportResult {
        data class Success(val entryCount: Int, val imageCount: Int = 0) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    /**
     * Result of an import operation.
     */
    sealed class ImportResult {
        data class Success(val importedCount: Int, val skippedCount: Int, val imageCount: Int = 0) : ImportResult()
        data class Error(val message: String) : ImportResult()
    }

    /**
     * Data class for import results including images.
     */
    data class ImportData(
        val entries: List<JournalEntry>,
        val images: Map<LocalDate, List<ImageData>>, // Map entry date to its images
        val result: ImportResult
    )

    /**
     * Data class for image data in backup.
     */
    data class ImageData(
        val filename: String,
        val data: ByteArray,
        val position: Int
    )

    /**
     * Export journal entries to an encrypted file.
     *
     * @param context Android context
     * @param entries List of journal entries to export
     * @param outputUri URI to write the backup file
     * @param password User-provided password for encryption
     * @param images Map of entry ID to list of images (optional)
     * @param imagesDir Directory containing image files (optional)
     * @param includeImages Whether to include images in backup
     */
    fun exportEntries(
        context: Context,
        entries: List<JournalEntry>,
        outputUri: Uri,
        password: String,
        images: Map<Long, List<JournalImage>> = emptyMap(),
        imagesDir: File? = null,
        includeImages: Boolean = false
    ): ExportResult {
        val trace = PerformanceTraces.startCreateBackup()
        return try {
            if (password.length < 8) {
                return ExportResult.Error("Password must be at least 8 characters")
            }

            if (entries.isEmpty()) {
                return ExportResult.Error("No entries to export")
            }

            var imageCount = 0

            // Convert entries to JSON
            val jsonArray = JSONArray()
            entries.forEach { entry ->
                val jsonEntry = JSONObject().apply {
                    put("date", entry.date.toString())
                    put("content", entry.content)
                    put("wordCount", entry.wordCount)
                    put("mood", entry.mood ?: JSONObject.NULL)
                    put("createdAt", entry.createdAt)
                    put("updatedAt", entry.updatedAt)

                    // Add images if enabled
                    if (includeImages && imagesDir != null) {
                        val entryImages = images[entry.id] ?: emptyList()
                        if (entryImages.isNotEmpty()) {
                            val imagesArray = JSONArray()
                            entryImages.forEach { image ->
                                val imageFile = File(imagesDir, image.filePath)
                                if (imageFile.exists()) {
                                    try {
                                        val imageData = imageFile.readBytes()
                                        val imageJson = JSONObject().apply {
                                            put("filename", image.filePath)
                                            put("position", image.position)
                                            put("data", Base64.encodeToString(imageData, Base64.NO_WRAP))
                                        }
                                        imagesArray.put(imageJson)
                                        imageCount++
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to read image: ${image.filePath}", e)
                                    }
                                }
                            }
                            if (imagesArray.length() > 0) {
                                put("images", imagesArray)
                            }
                        }
                    }
                }
                jsonArray.put(jsonEntry)
            }

            val backupJson = JSONObject().apply {
                put("version", if (includeImages) CURRENT_BACKUP_VERSION else BACKUP_VERSION_V1)
                put("exportedAt", System.currentTimeMillis())
                put("entryCount", entries.size)
                put("imageCount", imageCount)
                put("entries", jsonArray)
            }

            // Encrypt the JSON data
            val encryptedData = encrypt(backupJson.toString(), password)

            // Write to file
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                outputStream.write(encryptedData.toByteArray(Charsets.UTF_8))
            } ?: return ExportResult.Error("Could not open output file")

            trace.putMetric("entry_count", entries.size.toLong())
            trace.putMetric("image_count", imageCount.toLong())
            trace.stop()
            ExportResult.Success(entries.size, imageCount)
        } catch (e: Exception) {
            trace.stop()
            ExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Import journal entries from an encrypted backup file.
     *
     * @param context Android context
     * @param inputUri URI to read the backup file from
     * @param password User-provided password for decryption
     * @param existingDates Set of dates that already have entries (to skip duplicates)
     * @return Pair of imported entries and result
     */
    fun importEntries(
        context: Context,
        inputUri: Uri,
        password: String,
        existingDates: Set<LocalDate>
    ): Pair<List<JournalEntry>, ImportResult> {
        val importData = importEntriesWithImages(context, inputUri, password, existingDates)
        return Pair(importData.entries, importData.result)
    }

    /**
     * Import journal entries with images from an encrypted backup file.
     *
     * @param context Android context
     * @param inputUri URI to read the backup file from
     * @param password User-provided password for decryption
     * @param existingDates Set of dates that already have entries (to skip duplicates)
     * @return ImportData containing entries, images, and result
     */
    fun importEntriesWithImages(
        context: Context,
        inputUri: Uri,
        password: String,
        existingDates: Set<LocalDate>
    ): ImportData {
        val trace = PerformanceTraces.startRestoreBackup()
        return try {
            // Read encrypted data from file
            val encryptedData = context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            } ?: return ImportData(emptyList(), emptyMap(), ImportResult.Error("Could not open backup file"))

            // Decrypt
            val decryptedJson = try {
                decrypt(encryptedData, password)
            } catch (e: Exception) {
                return ImportData(emptyList(), emptyMap(), ImportResult.Error("Incorrect password or corrupted file"))
            }

            // Parse JSON
            val backupJson = JSONObject(decryptedJson)
            val version = backupJson.optInt("version", 0)

            if (version < BACKUP_VERSION_V1 || version > CURRENT_BACKUP_VERSION) {
                return ImportData(emptyList(), emptyMap(), ImportResult.Error("Unsupported backup version"))
            }

            val entriesArray = backupJson.getJSONArray("entries")
            val importedEntries = mutableListOf<JournalEntry>()
            val importedImages = mutableMapOf<LocalDate, List<ImageData>>()
            var skippedCount = 0
            var imageCount = 0

            for (i in 0 until entriesArray.length()) {
                val jsonEntry = entriesArray.getJSONObject(i)
                val date = LocalDate.parse(jsonEntry.getString("date"))

                // Skip if entry already exists for this date
                if (existingDates.contains(date)) {
                    skippedCount++
                    continue
                }

                val entry = JournalEntry(
                    id = 0, // Will be auto-generated
                    date = date,
                    content = JournalEntry.sanitizeContent(jsonEntry.getString("content")),
                    wordCount = JournalEntry.sanitizeWordCount(jsonEntry.getInt("wordCount")),
                    mood = if (jsonEntry.isNull("mood")) null else jsonEntry.getString("mood"),
                    createdAt = jsonEntry.getLong("createdAt"),
                    updatedAt = jsonEntry.getLong("updatedAt")
                )

                // Validate mood
                val finalEntry = if (JournalEntry.isValidMood(entry.mood)) {
                    entry
                } else {
                    // Import with null mood if invalid
                    entry.copy(mood = null)
                }
                importedEntries.add(finalEntry)

                // Extract images if present (v2 format)
                if (version >= BACKUP_VERSION_V2 && jsonEntry.has("images")) {
                    val imagesArray = jsonEntry.getJSONArray("images")
                    val entryImages = mutableListOf<ImageData>()
                    for (j in 0 until imagesArray.length()) {
                        val imageJson = imagesArray.getJSONObject(j)
                        try {
                            val imageData = ImageData(
                                filename = imageJson.getString("filename"),
                                data = Base64.decode(imageJson.getString("data"), Base64.NO_WRAP),
                                position = imageJson.optInt("position", j)
                            )
                            entryImages.add(imageData)
                            imageCount++
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to decode image", e)
                        }
                    }
                    if (entryImages.isNotEmpty()) {
                        importedImages[date] = entryImages
                    }
                }
            }

            trace.putMetric("imported_count", importedEntries.size.toLong())
            trace.putMetric("skipped_count", skippedCount.toLong())
            trace.putMetric("image_count", imageCount.toLong())
            trace.stop()
            ImportData(importedEntries, importedImages, ImportResult.Success(importedEntries.size, skippedCount, imageCount))
        } catch (e: Exception) {
            trace.stop()
            ImportData(emptyList(), emptyMap(), ImportResult.Error("Import failed: ${e.message}"))
        }
    }

    /**
     * Encrypt data using AES-256-GCM with PBKDF2 key derivation.
     */
    private fun encrypt(plaintext: String, password: String): String {
        // Generate random salt and IV
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }

        // Derive key from password
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        // Encrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine salt + iv + encrypted data
        val combined = salt + iv + encryptedBytes

        // Return as Base64
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt data using AES-256-GCM with PBKDF2 key derivation.
     */
    private fun decrypt(encryptedBase64: String, password: String): String {
        // Decode Base64
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)

        // Extract salt, iv, and encrypted data
        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val encryptedBytes = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)

        // Derive key from password
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance(KEY_ALGORITHM)
        val secretKey = SecretKeySpec(keyFactory.generateSecret(keySpec).encoded, "AES")

        // Decrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val decryptedBytes = cipher.doFinal(encryptedBytes)

        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Generate a suggested filename for the backup.
     */
    fun generateBackupFilename(): String {
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm"))
        return "morning_mindful_backup_$timestamp.mmbackup"
    }

    /**
     * Export journal entries to an encrypted file in a folder (for auto-backup).
     * Uses a fixed filename "morning_mindful_auto.mmbackup" and overwrites previous backup.
     *
     * @param context Android context
     * @param entries List of journal entries to export
     * @param folderUri URI of the folder to save backup to
     * @param password User-provided password for encryption
     * @param images Map of entry ID to list of images (optional)
     * @param imagesDir Directory containing image files (optional)
     * @param includeImages Whether to include images in backup
     */
    fun exportToFolder(
        context: Context,
        entries: List<JournalEntry>,
        folderUri: Uri,
        password: String,
        images: Map<Long, List<JournalImage>> = emptyMap(),
        imagesDir: File? = null,
        includeImages: Boolean = false
    ): ExportResult {
        return try {
            if (password.length < 8) {
                return ExportResult.Error("Password must be at least 8 characters")
            }

            if (entries.isEmpty()) {
                return ExportResult.Error("No entries to export")
            }

            var imageCount = 0

            // Convert entries to JSON
            val jsonArray = JSONArray()
            entries.forEach { entry ->
                val jsonEntry = JSONObject().apply {
                    put("date", entry.date.toString())
                    put("content", entry.content)
                    put("wordCount", entry.wordCount)
                    put("mood", entry.mood ?: JSONObject.NULL)
                    put("createdAt", entry.createdAt)
                    put("updatedAt", entry.updatedAt)

                    // Add images if enabled
                    if (includeImages && imagesDir != null) {
                        val entryImages = images[entry.id] ?: emptyList()
                        if (entryImages.isNotEmpty()) {
                            val imagesArray = JSONArray()
                            entryImages.forEach { image ->
                                val imageFile = File(imagesDir, image.filePath)
                                if (imageFile.exists()) {
                                    try {
                                        val imageData = imageFile.readBytes()
                                        val imageJson = JSONObject().apply {
                                            put("filename", image.filePath)
                                            put("position", image.position)
                                            put("data", Base64.encodeToString(imageData, Base64.NO_WRAP))
                                        }
                                        imagesArray.put(imageJson)
                                        imageCount++
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to read image: ${image.filePath}", e)
                                    }
                                }
                            }
                            if (imagesArray.length() > 0) {
                                put("images", imagesArray)
                            }
                        }
                    }
                }
                jsonArray.put(jsonEntry)
            }

            val backupJson = JSONObject().apply {
                put("version", if (includeImages) CURRENT_BACKUP_VERSION else BACKUP_VERSION_V1)
                put("exportedAt", System.currentTimeMillis())
                put("entryCount", entries.size)
                put("imageCount", imageCount)
                put("entries", jsonArray)
            }

            // Encrypt the JSON data
            val encryptedData = encrypt(backupJson.toString(), password)

            // Create file in folder using DocumentFile
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return ExportResult.Error("Could not access backup folder")

            // Delete existing backup file if it exists
            val existingFile = folder.findFile(AUTO_BACKUP_FILENAME)
            existingFile?.delete()

            // Create new file
            val newFile = folder.createFile("application/octet-stream", AUTO_BACKUP_FILENAME)
                ?: return ExportResult.Error("Could not create backup file")

            // Write to file
            context.contentResolver.openOutputStream(newFile.uri)?.use { outputStream ->
                outputStream.write(encryptedData.toByteArray(Charsets.UTF_8))
            } ?: return ExportResult.Error("Could not write to backup file")

            ExportResult.Success(entries.size, imageCount)
        } catch (e: SecurityException) {
            ExportResult.Error("Permission denied: ${e.message}")
        } catch (e: Exception) {
            ExportResult.Error("Export failed: ${e.message}")
        }
    }

    /**
     * Find and count entries in an auto-backup file in a folder.
     * Used to detect if backup exists after reinstall.
     *
     * @return Number of entries in backup, or null if no backup found or error
     */
    fun findAutoBackupInFolder(context: Context, folderUri: Uri): Int? {
        return try {
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return null

            val backupFile = folder.findFile(AUTO_BACKUP_FILENAME) ?: return null

            // We can't decrypt without password, but we can check file exists
            // Return -1 to indicate backup exists but count unknown
            -1
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Import from the auto-backup file in a folder.
     */
    fun importFromFolder(
        context: Context,
        folderUri: Uri,
        password: String,
        existingDates: Set<LocalDate>
    ): Pair<List<JournalEntry>, ImportResult> {
        return try {
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return Pair(emptyList(), ImportResult.Error("Could not access backup folder"))

            val backupFile = folder.findFile(AUTO_BACKUP_FILENAME)
                ?: return Pair(emptyList(), ImportResult.Error("No backup file found"))

            importEntries(context, backupFile.uri, password, existingDates)
        } catch (e: Exception) {
            Pair(emptyList(), ImportResult.Error("Import failed: ${e.message}"))
        }
    }

    /**
     * Import from the auto-backup file in a folder with images.
     */
    fun importFromFolderWithImages(
        context: Context,
        folderUri: Uri,
        password: String,
        existingDates: Set<LocalDate>
    ): ImportData {
        return try {
            val folder = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, folderUri)
                ?: return ImportData(emptyList(), emptyMap(), ImportResult.Error("Could not access backup folder"))

            val backupFile = folder.findFile(AUTO_BACKUP_FILENAME)
                ?: return ImportData(emptyList(), emptyMap(), ImportResult.Error("No backup file found"))

            importEntriesWithImages(context, backupFile.uri, password, existingDates)
        } catch (e: Exception) {
            ImportData(emptyList(), emptyMap(), ImportResult.Error("Import failed: ${e.message}"))
        }
    }

    private const val AUTO_BACKUP_FILENAME = "morning_mindful_auto.mmbackup"
}
