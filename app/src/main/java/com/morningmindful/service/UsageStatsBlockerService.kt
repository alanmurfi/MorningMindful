package com.morningmindful.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.ui.MainActivity
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Service that polls the foreground app using Usage Stats API.
 * Used for "Gentle Reminder" mode when user doesn't want to grant Accessibility permission.
 *
 * Unlike the Accessibility Service which instantly redirects, this service:
 * - Polls every 2-3 seconds to detect foreground app
 * - Shows a full-screen reminder overlay (user can dismiss)
 * - Cannot force redirect - user has to voluntarily go to journal
 */
class UsageStatsBlockerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    // Cached settings
    private var blockedPackages: Set<String> = emptySet()
    private var morningStartHour = 5
    private var morningEndHour = 10
    private var requiredWordCount = 200
    private var todayJournalWordCount = 0

    // Prevent showing overlay repeatedly for same app
    private var lastReminderPackage: String? = null
    private var lastReminderTime: Long = 0
    private val REMINDER_COOLDOWN_MS = 30_000L // 30 seconds cooldown per app

    companion object {
        private const val TAG = "UsageStatsBlocker"
        private const val CHANNEL_ID = "gentle_reminder_channel"
        private const val NOTIFICATION_ID = 2002
        private const val POLLING_INTERVAL_MS = 2500L // Poll every 2.5 seconds

        @Volatile
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, UsageStatsBlockerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, UsageStatsBlockerService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        isServiceRunning = true

        startForeground(NOTIFICATION_ID, createNotification())
        startPolling()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isServiceRunning = false
        pollingJob?.cancel()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gentle Reminder",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when gentle reminder mode is active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Gentle reminder mode active")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            // Load initial settings
            loadSettings()

            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking foreground app", e)
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    private suspend fun loadSettings() {
        val app = MorningMindfulApp.getInstance()
        blockedPackages = app.settingsRepository.blockedApps.first()
        morningStartHour = app.settingsRepository.morningStartHour.first()
        morningEndHour = app.settingsRepository.morningEndHour.first()
        requiredWordCount = app.settingsRepository.requiredWordCount.first()

        // Listen for journal updates
        serviceScope.launch {
            app.journalRepository.getTodayEntry().collect { entry ->
                todayJournalWordCount = entry?.wordCount ?: 0
                if (todayJournalWordCount >= requiredWordCount) {
                    BlockingState.setJournalCompletedToday(true)
                }
            }
        }
    }

    private fun checkForegroundApp() {
        // Check if we're in the morning window - stop service to save battery if outside
        val currentHour = LocalTime.now().hour
        if (currentHour < morningStartHour || currentHour >= morningEndHour) {
            Log.d(TAG, "Outside morning window ($morningStartHour:00 - $morningEndHour:00), current: $currentHour, stopping service")
            stopSelf()
            return
        }

        // Check if already journaled today
        if (todayJournalWordCount >= requiredWordCount || BlockingState.journalCompletedToday.value) {
            Log.d(TAG, "Journal completed, stopping service")
            stopSelf()
            return
        }

        Log.d(TAG, "Checking foreground app... blocked packages: $blockedPackages")

        // Get foreground app
        val foregroundPackage = getForegroundPackage() ?: return

        // Skip our own app
        if (foregroundPackage == packageName) return

        // Check if it's a blocked app
        if (!blockedPackages.contains(foregroundPackage)) return

        // Check cooldown - don't spam user
        val now = System.currentTimeMillis()
        if (foregroundPackage == lastReminderPackage &&
            now - lastReminderTime < REMINDER_COOLDOWN_MS) {
            return
        }

        // Show reminder overlay
        Log.d(TAG, "Blocked app detected: $foregroundPackage")
        lastReminderPackage = foregroundPackage
        lastReminderTime = now
        showReminderOverlay(foregroundPackage)
    }

    private fun getForegroundPackage(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager is null")
            return null
        }

        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 60_000, // Last 60 seconds for better detection
            now
        )

        if (stats.isNullOrEmpty()) {
            Log.w(TAG, "No usage stats available - permission may not be granted")
            return null
        }

        // Get most recently used app
        val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
        val packageName = mostRecent?.packageName
        Log.d(TAG, "Foreground app detected: $packageName")
        return packageName
    }

    private fun showReminderOverlay(blockedPackage: String) {
        val intent = Intent(this, ReminderOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(ReminderOverlayActivity.EXTRA_BLOCKED_APP, blockedPackage)
        }
        startActivity(intent)
    }
}
