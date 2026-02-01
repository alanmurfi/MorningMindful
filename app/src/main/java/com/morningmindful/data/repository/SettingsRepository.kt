package com.morningmindful.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.morningmindful.util.BlockedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Secure settings repository using EncryptedSharedPreferences.
 * All settings are encrypted at rest using Android Keystore-backed encryption.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        private const val PREFS_FILE = "encrypted_settings"
        private const val KEY_IS_BLOCKING_ENABLED = "is_blocking_enabled"
        private const val KEY_BLOCKING_DURATION_MINUTES = "blocking_duration_minutes"
        private const val KEY_REQUIRED_WORD_COUNT = "required_word_count"
        private const val KEY_BLOCKED_APPS = "blocked_apps"
        private const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        private const val KEY_MORNING_START_HOUR = "morning_start_hour"
        private const val KEY_MORNING_END_HOUR = "morning_end_hour"
        private const val KEY_BLOCKED_APPS_VERSION = "blocked_apps_version"
        private const val KEY_THEME_MODE = "theme_mode"

        // Increment this when adding new default blocked apps
        private const val CURRENT_BLOCKED_APPS_VERSION = 2  // v2 adds browsers

        // Theme mode constants
        const val THEME_MODE_SYSTEM = 0
        const val THEME_MODE_LIGHT = 1
        const val THEME_MODE_DARK = 2
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

    // StateFlows to emit changes
    private val _isBlockingEnabled = MutableStateFlow(false)
    private val _blockingDurationMinutes = MutableStateFlow(15)
    private val _requiredWordCount = MutableStateFlow(200)
    private val _blockedApps = MutableStateFlow<Set<String>>(BlockedApps.DEFAULT_BLOCKED_PACKAGES)
    private val _hasCompletedOnboarding = MutableStateFlow(false)
    private val _morningStartHour = MutableStateFlow(5)
    private val _morningEndHour = MutableStateFlow(10)
    private val _themeMode = MutableStateFlow(THEME_MODE_SYSTEM)

    init {
        // Load initial values from encrypted storage
        _isBlockingEnabled.value = encryptedPrefs.getBoolean(KEY_IS_BLOCKING_ENABLED, false)
        _blockingDurationMinutes.value = encryptedPrefs.getInt(KEY_BLOCKING_DURATION_MINUTES, 15)
        _requiredWordCount.value = encryptedPrefs.getInt(KEY_REQUIRED_WORD_COUNT, 200)
        _hasCompletedOnboarding.value = encryptedPrefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false)
        _morningStartHour.value = encryptedPrefs.getInt(KEY_MORNING_START_HOUR, 5)
        _morningEndHour.value = encryptedPrefs.getInt(KEY_MORNING_END_HOUR, 10)
        _themeMode.value = encryptedPrefs.getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM)

        // Load blocked apps with migration for new default apps
        _blockedApps.value = loadBlockedAppsWithMigration()
    }

    /**
     * Load blocked apps, merging in any new default apps added in updates.
     * This ensures users get new blocked apps (like browsers) without losing their customizations.
     */
    private fun loadBlockedAppsWithMigration(): Set<String> {
        val savedVersion = encryptedPrefs.getInt(KEY_BLOCKED_APPS_VERSION, 1)
        val savedApps = encryptedPrefs.getStringSet(KEY_BLOCKED_APPS, null)

        // First install - use defaults
        if (savedApps == null) {
            encryptedPrefs.edit()
                .putInt(KEY_BLOCKED_APPS_VERSION, CURRENT_BLOCKED_APPS_VERSION)
                .apply()
            return BlockedApps.DEFAULT_BLOCKED_PACKAGES
        }

        // Already up to date
        if (savedVersion >= CURRENT_BLOCKED_APPS_VERSION) {
            return savedApps
        }

        // Migration needed - merge new defaults with user's existing apps
        val mergedApps = savedApps.toMutableSet()
        mergedApps.addAll(BlockedApps.DEFAULT_BLOCKED_PACKAGES)

        // Save merged list and update version
        encryptedPrefs.edit()
            .putStringSet(KEY_BLOCKED_APPS, mergedApps)
            .putInt(KEY_BLOCKED_APPS_VERSION, CURRENT_BLOCKED_APPS_VERSION)
            .apply()

        return mergedApps
    }

    // Whether blocking is enabled
    val isBlockingEnabled: Flow<Boolean> = _isBlockingEnabled.asStateFlow()

    suspend fun setBlockingEnabled(enabled: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putBoolean(KEY_IS_BLOCKING_ENABLED, enabled).apply()
            _isBlockingEnabled.value = enabled
        }
    }

    // Blocking duration in minutes
    val blockingDurationMinutes: Flow<Int> = _blockingDurationMinutes.asStateFlow()

    suspend fun setBlockingDurationMinutes(minutes: Int) {
        withContext(Dispatchers.IO) {
            // Security: Enforce bounds (1-120 minutes)
            val bounded = minutes.coerceIn(1, 120)
            encryptedPrefs.edit().putInt(KEY_BLOCKING_DURATION_MINUTES, bounded).apply()
            _blockingDurationMinutes.value = bounded
        }
    }

    // Required word count for journal
    val requiredWordCount: Flow<Int> = _requiredWordCount.asStateFlow()

    suspend fun setRequiredWordCount(count: Int) {
        withContext(Dispatchers.IO) {
            // Security: Enforce bounds (1-10000 words)
            val bounded = count.coerceIn(1, 10000)
            encryptedPrefs.edit().putInt(KEY_REQUIRED_WORD_COUNT, bounded).apply()
            _requiredWordCount.value = bounded
        }
    }

    // Blocked apps (package names)
    val blockedApps: Flow<Set<String>> = _blockedApps.asStateFlow()

    suspend fun setBlockedApps(apps: Set<String>) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putStringSet(KEY_BLOCKED_APPS, apps).apply()
            _blockedApps.value = apps
        }
    }

    suspend fun addBlockedApp(packageName: String) {
        // Security: Validate package name format and length
        if (!isValidPackageName(packageName)) return

        withContext(Dispatchers.IO) {
            val current = _blockedApps.value
            val updated = current + packageName
            encryptedPrefs.edit().putStringSet(KEY_BLOCKED_APPS, updated).apply()
            _blockedApps.value = updated
        }
    }

    /**
     * Security: Validate Android package name format.
     * Must be: lowercase letters, digits, underscores, dots. Max 255 chars.
     */
    private fun isValidPackageName(packageName: String): Boolean {
        if (packageName.isBlank() || packageName.length > 255) return false
        // Android package name pattern: segments separated by dots
        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
        return pattern.matches(packageName)
    }

    suspend fun removeBlockedApp(packageName: String) {
        withContext(Dispatchers.IO) {
            val current = _blockedApps.value
            val updated = current - packageName
            encryptedPrefs.edit().putStringSet(KEY_BLOCKED_APPS, updated).apply()
            _blockedApps.value = updated
        }
    }

    // Onboarding completed
    val hasCompletedOnboarding: Flow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    suspend fun setOnboardingCompleted(completed: Boolean) {
        withContext(Dispatchers.IO) {
            encryptedPrefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, completed).apply()
            _hasCompletedOnboarding.value = completed
        }
    }

    // Morning window start hour (default 5am)
    val morningStartHour: Flow<Int> = _morningStartHour.asStateFlow()

    suspend fun setMorningStartHour(hour: Int) {
        withContext(Dispatchers.IO) {
            val bounded = hour.coerceIn(0, 23)
            encryptedPrefs.edit().putInt(KEY_MORNING_START_HOUR, bounded).apply()
            _morningStartHour.value = bounded
        }
    }

    // Morning window end hour (default 10am)
    val morningEndHour: Flow<Int> = _morningEndHour.asStateFlow()

    suspend fun setMorningEndHour(hour: Int) {
        withContext(Dispatchers.IO) {
            // Allow 1-24 for end hour (24 = midnight/end of day)
            val bounded = hour.coerceIn(1, 24)
            encryptedPrefs.edit().putInt(KEY_MORNING_END_HOUR, bounded).apply()
            _morningEndHour.value = bounded
        }
    }

    /**
     * Check if the current time is within the morning window.
     */
    fun isWithinMorningWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        return currentHour in startHour until endHour
    }

    // Theme mode (system, light, dark)
    val themeMode: Flow<Int> = _themeMode.asStateFlow()

    suspend fun setThemeMode(mode: Int) {
        withContext(Dispatchers.IO) {
            val bounded = mode.coerceIn(THEME_MODE_SYSTEM, THEME_MODE_DARK)
            encryptedPrefs.edit().putInt(KEY_THEME_MODE, bounded).apply()
            _themeMode.value = bounded
        }
    }

    /**
     * Get the current theme mode value synchronously (for app startup).
     */
    fun getThemeModeSync(): Int {
        return encryptedPrefs.getInt(KEY_THEME_MODE, THEME_MODE_SYSTEM)
    }
}
