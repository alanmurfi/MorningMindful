package com.morningmindful.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Manages the encryption key for the SQLCipher database.
 * The key is generated once and stored securely using EncryptedSharedPreferences.
 */
object DatabaseKeyManager {

    private const val PREFS_FILE = "db_key_prefs"
    private const val KEY_DB_PASSPHRASE = "db_passphrase"
    private const val KEY_DB_PASSPHRASE_V2 = "db_passphrase_v2" // Base64 encoded
    private const val KEY_LENGTH = 32 // 256 bits

    /**
     * Checks if a database encryption key already exists.
     * Used to determine if database is already encrypted.
     */
    fun hasExistingKey(context: Context): Boolean {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            encryptedPrefs.getString(KEY_DB_PASSPHRASE, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the existing database encryption key or creates a new one.
     * The key is stored securely using Android Keystore-backed encryption.
     *
     * Migration note: Keys are now stored as Base64 (v2). Old ISO-8859-1 keys
     * are automatically migrated on first access.
     */
    fun getOrCreateKey(context: Context): ByteArray {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Check for new Base64-encoded key (v2)
        val existingKeyV2 = encryptedPrefs.getString(KEY_DB_PASSPHRASE_V2, null)
        if (existingKeyV2 != null) {
            return Base64.decode(existingKeyV2, Base64.NO_WRAP)
        }

        // Check for legacy ISO-8859-1 key and migrate it
        val legacyKey = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (legacyKey != null) {
            val keyBytes = legacyKey.toByteArray(Charsets.ISO_8859_1)
            // Migrate to Base64 encoding
            encryptedPrefs.edit()
                .putString(KEY_DB_PASSPHRASE_V2, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
                .remove(KEY_DB_PASSPHRASE) // Remove legacy key
                .apply()
            return keyBytes
        }

        // Generate a new random key
        val newKey = ByteArray(KEY_LENGTH)
        SecureRandom().nextBytes(newKey)

        // Store the key securely using Base64 encoding
        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE_V2, Base64.encodeToString(newKey, Base64.NO_WRAP))
            .apply()

        return newKey
    }
}
