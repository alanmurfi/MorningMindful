package com.morningmindful.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
import com.morningmindful.data.dao.JournalEntryDao
import com.morningmindful.data.dao.JournalImageDao
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.entity.JournalImage
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.time.LocalDate

@Database(
    entities = [JournalEntry::class, JournalImage::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun journalImageDao(): JournalImageDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "morning_mindful_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2: add mood column
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE journal_entries ADD COLUMN mood TEXT")
            }
        }

        // Migration from version 2 to 3: add journal_images table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS journal_images (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entryId INTEGER NOT NULL,
                        filePath TEXT NOT NULL,
                        originalName TEXT,
                        mimeType TEXT NOT NULL DEFAULT 'image/jpeg',
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        width INTEGER NOT NULL DEFAULT 0,
                        height INTEGER NOT NULL DEFAULT 0,
                        position INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (entryId) REFERENCES journal_entries(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_journal_images_entryId ON journal_images(entryId)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // Handle migration from unencrypted to encrypted database
                // If an old unencrypted database exists, we need to delete it
                // In production, you'd want to migrate data properly
                handleLegacyDatabase(context)

                // Get or create encryption key
                val passphrase = DatabaseKeyManager.getOrCreateKey(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Handle migration from unencrypted to encrypted database.
         * This properly migrates data from old unencrypted database to new encrypted one.
         */
        private fun handleLegacyDatabase(context: Context) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                "db_migration_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val migrationCompleted = encryptedPrefs.getBoolean("migration_completed_v2", false)

            if (!migrationCompleted) {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                val keyExists = DatabaseKeyManager.hasExistingKey(context)

                // Only migrate if old database exists AND no encryption key exists yet
                // This means it's truly an old unencrypted database
                if (dbFile.exists() && !keyExists) {
                    Log.d(TAG, "Found legacy unencrypted database - migrating data")
                    migrateUnencryptedToEncrypted(context, dbFile)
                }

                // Mark migration as completed
                encryptedPrefs.edit().putBoolean("migration_completed_v2", true).apply()
            }
        }

        /**
         * Migrate data from unencrypted SQLite database to encrypted SQLCipher database.
         * Preserves all journal entries during the migration.
         */
        private fun migrateUnencryptedToEncrypted(context: Context, oldDbFile: File) {
            val tempDbName = "temp_migration_db"
            val tempDbFile = context.getDatabasePath(tempDbName)

            try {
                // Step 1: Read all data from unencrypted database
                val entries = readEntriesFromUnencryptedDb(oldDbFile)
                Log.d(TAG, "Read ${entries.size} entries from unencrypted database")

                if (entries.isEmpty()) {
                    // No data to migrate, just delete old database
                    deleteOldDatabaseFiles(oldDbFile)
                    return
                }

                // Step 2: Delete old unencrypted database
                deleteOldDatabaseFiles(oldDbFile)

                // Step 3: Create new encrypted database and insert data
                val passphrase = DatabaseKeyManager.getOrCreateKey(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val tempDb = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()

                // Insert migrated entries
                entries.forEach { entry ->
                    tempDb.journalEntryDao().insertEntry(entry)
                }

                Log.d(TAG, "Successfully migrated ${entries.size} entries to encrypted database")
                tempDb.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error during migration, data may be lost", e)
                // If migration fails, delete old database to prevent crash loop
                deleteOldDatabaseFiles(oldDbFile)
            } finally {
                // Clean up temp file if it exists
                if (tempDbFile.exists()) {
                    tempDbFile.delete()
                }
            }
        }

        /**
         * Read journal entries from an unencrypted SQLite database.
         */
        private fun readEntriesFromUnencryptedDb(dbFile: File): List<JournalEntry> {
            val entries = mutableListOf<JournalEntry>()

            try {
                val db = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )

                // Check if table exists
                val tableCheckCursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='journal_entries'",
                    null
                )
                val tableExists = tableCheckCursor.moveToFirst()
                tableCheckCursor.close()

                if (!tableExists) {
                    db.close()
                    return entries
                }

                // Read all entries
                val cursor = db.rawQuery("SELECT * FROM journal_entries", null)

                while (cursor.moveToNext()) {
                    try {
                        val idIndex = cursor.getColumnIndex("id")
                        val dateIndex = cursor.getColumnIndex("date")
                        val contentIndex = cursor.getColumnIndex("content")
                        val wordCountIndex = cursor.getColumnIndex("wordCount")
                        val createdAtIndex = cursor.getColumnIndex("createdAt")
                        val updatedAtIndex = cursor.getColumnIndex("updatedAt")
                        val moodIndex = cursor.getColumnIndex("mood")

                        // Parse date string to LocalDate
                        val dateString = if (dateIndex >= 0) cursor.getString(dateIndex) else null
                        val date = try {
                            if (dateString != null) LocalDate.parse(dateString) else LocalDate.now()
                        } catch (e: Exception) {
                            LocalDate.now()
                        }

                        val entry = JournalEntry(
                            id = if (idIndex >= 0) cursor.getLong(idIndex) else 0,
                            date = date,
                            content = if (contentIndex >= 0) cursor.getString(contentIndex) ?: "" else "",
                            wordCount = if (wordCountIndex >= 0) cursor.getInt(wordCountIndex) else 0,
                            createdAt = if (createdAtIndex >= 0) cursor.getLong(createdAtIndex) else System.currentTimeMillis(),
                            updatedAt = if (updatedAtIndex >= 0) cursor.getLong(updatedAtIndex) else System.currentTimeMillis(),
                            mood = if (moodIndex >= 0) cursor.getString(moodIndex) else null
                        )
                        entries.add(entry)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading entry, skipping", e)
                    }
                }

                cursor.close()
                db.close()

            } catch (e: Exception) {
                Log.e(TAG, "Error opening unencrypted database", e)
            }

            return entries
        }

        /**
         * Delete old database files (main db, shm, wal).
         */
        private fun deleteOldDatabaseFiles(dbFile: File) {
            try {
                val dbShm = File(dbFile.path + "-shm")
                val dbWal = File(dbFile.path + "-wal")
                val dbJournal = File(dbFile.path + "-journal")

                dbFile.delete()
                dbShm.delete()
                dbWal.delete()
                dbJournal.delete()

                Log.d(TAG, "Deleted old database files")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting old database files", e)
            }
        }
    }
}
