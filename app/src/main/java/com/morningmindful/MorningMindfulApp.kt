package com.morningmindful

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class annotated with @HiltAndroidApp to enable Hilt dependency injection.
 *
 * Hilt generates all the Dagger components and injects dependencies automatically.
 */
@HiltAndroidApp
class MorningMindfulApp : Application() {

    // Injected by Hilt - available after onCreate()
    @Inject lateinit var journalRepository: JournalRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Load SQLCipher native library before any database access
        System.loadLibrary("sqlcipher")

        // Initialize Firebase Crashlytics
        // Disable in debug builds to avoid noise during development
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Apply saved theme mode
        applyThemeMode()

        createNotificationChannel()
    }

    /**
     * Apply the saved theme mode preference.
     * Called at app startup and when theme preference changes.
     */
    fun applyThemeMode() {
        val themeMode = settingsRepository.getThemeModeSync()
        val nightMode = when (themeMode) {
            SettingsRepository.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            SettingsRepository.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Morning Mindful",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when morning blocking is active"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "morning_mindful_channel"
        const val BLOCKING_NOTIFICATION_ID = 1001

        @Volatile
        private var instance: MorningMindfulApp? = null

        fun getInstance(): MorningMindfulApp {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
}
