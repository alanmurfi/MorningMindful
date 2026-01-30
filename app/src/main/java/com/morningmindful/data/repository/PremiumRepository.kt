package com.morningmindful.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for managing premium/pro status.
 * Uses encrypted storage for security.
 */
class PremiumRepository(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "encrypted_premium"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_PURCHASE_TYPE = "purchase_type" // "lifetime" or "monthly"
        private const val KEY_PURCHASE_TOKEN = "purchase_token"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _isPremium = MutableStateFlow(false)
    val isPremium: Flow<Boolean> = _isPremium.asStateFlow()

    init {
        // Load cached premium status
        _isPremium.value = encryptedPrefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    /**
     * Check if user has premium access (either subscription or lifetime).
     */
    fun hasPremiumAccess(): Boolean {
        return _isPremium.value
    }

    /**
     * Update premium status after purchase verification.
     */
    fun setPremiumStatus(isPremium: Boolean, purchaseType: String? = null, purchaseToken: String? = null) {
        encryptedPrefs.edit().apply {
            putBoolean(KEY_IS_PREMIUM, isPremium)
            purchaseType?.let { putString(KEY_PURCHASE_TYPE, it) }
            purchaseToken?.let { putString(KEY_PURCHASE_TOKEN, it) }
            apply()
        }
        _isPremium.value = isPremium
    }

    /**
     * Get the type of purchase (lifetime or monthly).
     */
    fun getPurchaseType(): String? {
        return encryptedPrefs.getString(KEY_PURCHASE_TYPE, null)
    }

    /**
     * Clear premium status (e.g., if subscription expires).
     */
    fun clearPremiumStatus() {
        encryptedPrefs.edit().apply {
            remove(KEY_IS_PREMIUM)
            remove(KEY_PURCHASE_TYPE)
            remove(KEY_PURCHASE_TOKEN)
            apply()
        }
        _isPremium.value = false
    }
}
