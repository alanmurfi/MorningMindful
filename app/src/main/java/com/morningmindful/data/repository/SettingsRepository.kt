package com.morningmindful.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.morningmindful.util.BlockedApps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val IS_BLOCKING_ENABLED = booleanPreferencesKey("is_blocking_enabled")
        private val BLOCKING_DURATION_MINUTES = intPreferencesKey("blocking_duration_minutes")
        private val REQUIRED_WORD_COUNT = intPreferencesKey("required_word_count")
        private val BLOCKED_APPS = stringSetPreferencesKey("blocked_apps")
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val MORNING_START_HOUR = intPreferencesKey("morning_start_hour")
        private val MORNING_END_HOUR = intPreferencesKey("morning_end_hour")
    }

    // Whether blocking is enabled
    val isBlockingEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[IS_BLOCKING_ENABLED] ?: true
        }

    suspend fun setBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_BLOCKING_ENABLED] = enabled
        }
    }

    // Blocking duration in minutes
    val blockingDurationMinutes: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[BLOCKING_DURATION_MINUTES] ?: 15
        }

    suspend fun setBlockingDurationMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[BLOCKING_DURATION_MINUTES] = minutes
        }
    }

    // Required word count for journal
    val requiredWordCount: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[REQUIRED_WORD_COUNT] ?: 200
        }

    suspend fun setRequiredWordCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[REQUIRED_WORD_COUNT] = count
        }
    }

    // Blocked apps (package names)
    val blockedApps: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[BLOCKED_APPS] ?: BlockedApps.DEFAULT_BLOCKED_PACKAGES
        }

    suspend fun setBlockedApps(apps: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[BLOCKED_APPS] = apps
        }
    }

    suspend fun addBlockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLOCKED_APPS] ?: BlockedApps.DEFAULT_BLOCKED_PACKAGES
            preferences[BLOCKED_APPS] = current + packageName
        }
    }

    suspend fun removeBlockedApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BLOCKED_APPS] ?: BlockedApps.DEFAULT_BLOCKED_PACKAGES
            preferences[BLOCKED_APPS] = current - packageName
        }
    }

    // Onboarding completed
    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = completed
        }
    }

    // Morning window start hour (default 5am)
    val morningStartHour: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MORNING_START_HOUR] ?: 5
        }

    suspend fun setMorningStartHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[MORNING_START_HOUR] = hour.coerceIn(0, 23)
        }
    }

    // Morning window end hour (default 10am)
    val morningEndHour: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[MORNING_END_HOUR] ?: 10
        }

    suspend fun setMorningEndHour(hour: Int) {
        context.dataStore.edit { preferences ->
            preferences[MORNING_END_HOUR] = hour.coerceIn(0, 23)
        }
    }

    /**
     * Check if the current time is within the morning window.
     */
    fun isWithinMorningWindow(currentHour: Int, startHour: Int, endHour: Int): Boolean {
        return currentHour in startHour until endHour
    }
}
