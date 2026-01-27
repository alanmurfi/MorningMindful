package com.morningmindful.data

import android.content.Context
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
import com.morningmindful.data.entity.JournalEntry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

@Database(
    entities = [JournalEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun journalEntryDao(): JournalEntryDao

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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Handle migration from unencrypted to encrypted database.
         * For development: deletes old unencrypted database if encryption marker is missing.
         * For production: would need proper data migration.
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

            val isEncrypted = encryptedPrefs.getBoolean("is_encrypted", false)

            if (!isEncrypted) {
                val dbFile = context.getDatabasePath(DATABASE_NAME)
                val keyExists = DatabaseKeyManager.hasExistingKey(context)

                // Only delete database if it exists AND no encryption key exists
                // This means it's truly an old unencrypted database, not an encrypted
                // one where just the migration flag was tampered with
                if (dbFile.exists() && !keyExists) {
                    Log.d(TAG, "Migrating to encrypted database - removing old unencrypted database")
                    val dbShm = File(dbFile.path + "-shm")
                    val dbWal = File(dbFile.path + "-wal")
                    dbFile.delete()
                    dbShm.delete()
                    dbWal.delete()
                }

                // Mark that we've migrated to encrypted
                encryptedPrefs.edit().putBoolean("is_encrypted", true).apply()
            }
        }
    }
}
