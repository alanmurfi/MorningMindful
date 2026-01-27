package com.morningmindful.data

import android.content.Context
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
    private const val KEY_LENGTH = 32 // 256 bits

    /**
     * Gets the existing database encryption key or creates a new one.
     * The key is stored securely using Android Keystore-backed encryption.
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

        // Check if we already have a key
        val existingKey = encryptedPrefs.getString(KEY_DB_PASSPHRASE, null)
        if (existingKey != null) {
            return existingKey.toByteArray(Charsets.ISO_8859_1)
        }

        // Generate a new random key
        val newKey = ByteArray(KEY_LENGTH)
        SecureRandom().nextBytes(newKey)

        // Store the key securely
        encryptedPrefs.edit()
            .putString(KEY_DB_PASSPHRASE, String(newKey, Charsets.ISO_8859_1))
            .apply()

        return newKey
    }
}
