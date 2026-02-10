package com.morningmindful

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.metrics.Trace
import com.morningmindful.data.repository.JournalImageRepository
import com.morningmindful.util.PerformanceTraces
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.service.MorningCheckWorker
import com.morningmindful.service.MorningMonitorService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
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
    @Inject lateinit var journalImageRepository: JournalImageRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    // Performance trace for app startup
    private var startupTrace: Trace? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Start tracking app startup performance
        startupTrace = PerformanceTraces.startAppStartup()

        // Load SQLCipher native library before any database access
        System.loadLibrary("sqlcipher")

        // Initialize Firebase Crashlytics
        // Only enable in release builds to avoid noise during development
        // Debug builds can still test via the "Test Crash" button in Settings
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        // Apply saved theme mode
        applyThemeMode()

        createNotificationChannel()

        // Schedule the morning check worker for reliable blocking
        // This replaces the unreliable USER_PRESENT broadcast
        MorningCheckWorker.schedule(this)

        // Start morning monitor service immediately if within morning window
        startMorningMonitorIfNeeded()

        // Stop startup trace - app is now ready
        startupTrace?.stop()
        startupTrace = null
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun startMorningMonitorIfNeeded() {
        appScope.launch {
            try {
                // Check if blocking is enabled
                val isEnabled = settingsRepository.isBlockingEnabled.first()
                if (!isEnabled) return@launch

                // Check if we're within the morning window
                val currentHour = LocalTime.now().hour
                val morningStart = settingsRepository.morningStartHour.first()
                val morningEnd = settingsRepository.morningEndHour.first()

                if (currentHour < morningStart || currentHour >= morningEnd) {
                    return@launch
                }

                // Check if already journaled today
                val requiredWords = settingsRepository.requiredWordCount.first()
                val todayEntry = journalRepository.getTodayEntry().first()
                if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                    return@launch
                }

                // Start the monitor service
                if (!MorningMonitorService.isServiceRunning) {
                    MorningMonitorService.start(this@MorningMindfulApp)
                }
            } catch (e: Exception) {
                // Ignore errors during startup
            }
        }
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
