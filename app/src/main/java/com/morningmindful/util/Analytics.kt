package com.morningmindful.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase

/**
 * Centralized analytics helper for tracking user events and properties.
 * Uses Firebase Analytics under the hood.
 */
object Analytics {

    private val firebaseAnalytics: FirebaseAnalytics by lazy { Firebase.analytics }

    // ==================== EVENTS ====================

    // Journal Events
    object Events {
        const val JOURNAL_ENTRY_CREATED = "journal_entry_created"
        const val JOURNAL_ENTRY_EDITED = "journal_entry_edited"
        const val JOURNAL_ENTRY_DELETED = "journal_entry_deleted"
        const val JOURNAL_PHOTO_ADDED = "journal_photo_added"
        const val JOURNAL_PROMPT_USED = "journal_prompt_used"
        const val JOURNAL_MOOD_SELECTED = "journal_mood_selected"

        // Blocking Events
        const val BLOCKING_TRIGGERED = "blocking_triggered"
        const val BLOCKING_APP_REDIRECTED = "blocking_app_redirected"
        const val BLOCKING_COMPLETED = "blocking_completed"
        const val BLOCKING_MODE_CHANGED = "blocking_mode_changed"

        // Onboarding Events
        const val ONBOARDING_STARTED = "onboarding_started"
        const val ONBOARDING_COMPLETED = "onboarding_completed"
        const val ONBOARDING_STEP_COMPLETED = "onboarding_step_completed"
        const val ONBOARDING_SKIPPED = "onboarding_skipped"

        // Backup Events
        const val BACKUP_CREATED = "backup_created"
        const val BACKUP_RESTORED = "backup_restored"
        const val BACKUP_FAILED = "backup_failed"

        // Settings Events
        const val SETTINGS_CHANGED = "settings_changed"
        const val PERMISSION_GRANTED = "permission_granted"
        const val PERMISSION_DENIED = "permission_denied"

        // Engagement Events
        const val APP_OPENED = "app_opened"
        const val STREAK_MILESTONE = "streak_milestone"
    }

    // Parameters
    object Params {
        const val WORD_COUNT = "word_count"
        const val PHOTO_COUNT = "photo_count"
        const val MOOD = "mood"
        const val PROMPT_TEXT = "prompt_text"
        const val APP_PACKAGE = "app_package"
        const val BLOCKING_MODE = "blocking_mode"
        const val BLOCKING_DURATION = "blocking_duration_minutes"
        const val STEP_NUMBER = "step_number"
        const val STEP_NAME = "step_name"
        const val SETTING_NAME = "setting_name"
        const val SETTING_VALUE = "setting_value"
        const val PERMISSION_TYPE = "permission_type"
        const val STREAK_DAYS = "streak_days"
        const val ENTRY_COUNT = "entry_count"
        const val SUCCESS = "success"
        const val ERROR_MESSAGE = "error_message"
    }

    // User Properties
    object UserProperties {
        const val TOTAL_ENTRIES = "total_entries"
        const val CURRENT_STREAK = "current_streak"
        const val BLOCKING_ENABLED = "blocking_enabled"
        const val BLOCKING_MODE = "blocking_mode"
        const val LANGUAGE = "app_language"
        const val THEME = "app_theme"
    }

    // ==================== JOURNAL TRACKING ====================

    fun trackJournalEntryCreated(wordCount: Int, photoCount: Int, mood: String?) {
        val params = Bundle().apply {
            putInt(Params.WORD_COUNT, wordCount)
            putInt(Params.PHOTO_COUNT, photoCount)
            mood?.let { putString(Params.MOOD, it) }
        }
        firebaseAnalytics.logEvent(Events.JOURNAL_ENTRY_CREATED, params)
    }

    fun trackJournalEntryEdited(wordCount: Int) {
        val params = Bundle().apply {
            putInt(Params.WORD_COUNT, wordCount)
        }
        firebaseAnalytics.logEvent(Events.JOURNAL_ENTRY_EDITED, params)
    }

    fun trackJournalEntryDeleted() {
        firebaseAnalytics.logEvent(Events.JOURNAL_ENTRY_DELETED, null)
    }

    fun trackPhotoAdded(totalPhotos: Int) {
        val params = Bundle().apply {
            putInt(Params.PHOTO_COUNT, totalPhotos)
        }
        firebaseAnalytics.logEvent(Events.JOURNAL_PHOTO_ADDED, params)
    }

    fun trackPromptUsed(promptText: String) {
        val params = Bundle().apply {
            // Truncate to 100 chars for analytics
            putString(Params.PROMPT_TEXT, promptText.take(100))
        }
        firebaseAnalytics.logEvent(Events.JOURNAL_PROMPT_USED, params)
    }

    fun trackMoodSelected(mood: String) {
        val params = Bundle().apply {
            putString(Params.MOOD, mood)
        }
        firebaseAnalytics.logEvent(Events.JOURNAL_MOOD_SELECTED, params)
    }

    // ==================== BLOCKING TRACKING ====================

    fun trackBlockingTriggered(mode: String, durationMinutes: Int) {
        val params = Bundle().apply {
            putString(Params.BLOCKING_MODE, mode)
            putInt(Params.BLOCKING_DURATION, durationMinutes)
        }
        firebaseAnalytics.logEvent(Events.BLOCKING_TRIGGERED, params)
    }

    fun trackAppRedirected(appPackage: String) {
        val params = Bundle().apply {
            // Only log app name, not full package for privacy
            putString(Params.APP_PACKAGE, appPackage.substringAfterLast('.'))
        }
        firebaseAnalytics.logEvent(Events.BLOCKING_APP_REDIRECTED, params)
    }

    fun trackBlockingCompleted(mode: String) {
        val params = Bundle().apply {
            putString(Params.BLOCKING_MODE, mode)
        }
        firebaseAnalytics.logEvent(Events.BLOCKING_COMPLETED, params)
    }

    fun trackBlockingModeChanged(newMode: String) {
        val params = Bundle().apply {
            putString(Params.BLOCKING_MODE, newMode)
        }
        firebaseAnalytics.logEvent(Events.BLOCKING_MODE_CHANGED, params)
    }

    // ==================== ONBOARDING TRACKING ====================

    fun trackOnboardingStarted() {
        firebaseAnalytics.logEvent(Events.ONBOARDING_STARTED, null)
    }

    fun trackOnboardingStepCompleted(stepNumber: Int, stepName: String) {
        val params = Bundle().apply {
            putInt(Params.STEP_NUMBER, stepNumber)
            putString(Params.STEP_NAME, stepName)
        }
        firebaseAnalytics.logEvent(Events.ONBOARDING_STEP_COMPLETED, params)
    }

    fun trackOnboardingCompleted() {
        firebaseAnalytics.logEvent(Events.ONBOARDING_COMPLETED, null)
    }

    fun trackOnboardingSkipped(atStep: Int) {
        val params = Bundle().apply {
            putInt(Params.STEP_NUMBER, atStep)
        }
        firebaseAnalytics.logEvent(Events.ONBOARDING_SKIPPED, params)
    }

    // ==================== BACKUP TRACKING ====================

    fun trackBackupCreated(entryCount: Int, success: Boolean) {
        val params = Bundle().apply {
            putInt(Params.ENTRY_COUNT, entryCount)
            putBoolean(Params.SUCCESS, success)
        }
        firebaseAnalytics.logEvent(Events.BACKUP_CREATED, params)
    }

    fun trackBackupRestored(entryCount: Int, success: Boolean) {
        val params = Bundle().apply {
            putInt(Params.ENTRY_COUNT, entryCount)
            putBoolean(Params.SUCCESS, success)
        }
        firebaseAnalytics.logEvent(Events.BACKUP_RESTORED, params)
    }

    fun trackBackupFailed(error: String) {
        val params = Bundle().apply {
            putString(Params.ERROR_MESSAGE, error.take(100))
        }
        firebaseAnalytics.logEvent(Events.BACKUP_FAILED, params)
    }

    // ==================== SETTINGS TRACKING ====================

    fun trackSettingChanged(settingName: String, value: String) {
        val params = Bundle().apply {
            putString(Params.SETTING_NAME, settingName)
            putString(Params.SETTING_VALUE, value)
        }
        firebaseAnalytics.logEvent(Events.SETTINGS_CHANGED, params)
    }

    fun trackPermissionGranted(permissionType: String) {
        val params = Bundle().apply {
            putString(Params.PERMISSION_TYPE, permissionType)
        }
        firebaseAnalytics.logEvent(Events.PERMISSION_GRANTED, params)
    }

    fun trackPermissionDenied(permissionType: String) {
        val params = Bundle().apply {
            putString(Params.PERMISSION_TYPE, permissionType)
        }
        firebaseAnalytics.logEvent(Events.PERMISSION_DENIED, params)
    }

    // ==================== ENGAGEMENT TRACKING ====================

    fun trackAppOpened() {
        firebaseAnalytics.logEvent(Events.APP_OPENED, null)
    }

    fun trackStreakMilestone(days: Int) {
        val params = Bundle().apply {
            putInt(Params.STREAK_DAYS, days)
        }
        firebaseAnalytics.logEvent(Events.STREAK_MILESTONE, params)
    }

    // ==================== USER PROPERTIES ====================

    fun setUserProperties(
        totalEntries: Int? = null,
        currentStreak: Int? = null,
        blockingEnabled: Boolean? = null,
        blockingMode: String? = null,
        language: String? = null,
        theme: String? = null
    ) {
        totalEntries?.let {
            firebaseAnalytics.setUserProperty(UserProperties.TOTAL_ENTRIES, bucketCount(it))
        }
        currentStreak?.let {
            firebaseAnalytics.setUserProperty(UserProperties.CURRENT_STREAK, bucketStreak(it))
        }
        blockingEnabled?.let {
            firebaseAnalytics.setUserProperty(UserProperties.BLOCKING_ENABLED, it.toString())
        }
        blockingMode?.let {
            firebaseAnalytics.setUserProperty(UserProperties.BLOCKING_MODE, it)
        }
        language?.let {
            firebaseAnalytics.setUserProperty(UserProperties.LANGUAGE, it)
        }
        theme?.let {
            firebaseAnalytics.setUserProperty(UserProperties.THEME, it)
        }
    }

    // Bucket counts to avoid high-cardinality user properties
    private fun bucketCount(count: Int): String = when {
        count == 0 -> "0"
        count <= 5 -> "1-5"
        count <= 10 -> "6-10"
        count <= 25 -> "11-25"
        count <= 50 -> "26-50"
        count <= 100 -> "51-100"
        else -> "100+"
    }

    private fun bucketStreak(days: Int): String = when {
        days == 0 -> "0"
        days <= 3 -> "1-3"
        days <= 7 -> "4-7"
        days <= 14 -> "8-14"
        days <= 30 -> "15-30"
        else -> "30+"
    }
}
