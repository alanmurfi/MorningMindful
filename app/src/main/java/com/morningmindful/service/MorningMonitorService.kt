package com.morningmindful.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.ui.MainActivity
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Foreground service that runs during the morning window to reliably detect screen unlocks.
 *
 * The USER_PRESENT broadcast only works reliably when registered dynamically by a running
 * component. This service runs during morning hours to ensure we catch the first unlock.
 *
 * Battery impact is minimal because:
 * - Only runs during the configured morning window (e.g., 5am-10am)
 * - Stops as soon as the user completes their journal
 * - Uses a low-priority notification
 */
class MorningMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenUnlockReceiver: BroadcastReceiver? = null
    private var screenOnReceiver: BroadcastReceiver? = null
    private var dateChangeReceiver: BroadcastReceiver? = null

    companion object {
        private const val TAG = "MorningMonitorService"
        private const val CHANNEL_ID = "morning_monitor_channel"
        private const val NOTIFICATION_ID = 3001

        @Volatile
        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, MorningMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MorningMonitorService::class.java))
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
        registerScreenUnlockReceiver()
        registerScreenOnReceiver()
        registerDateChangeReceiver()

        // Check if we should stop (outside morning window or already journaled)
        serviceScope.launch {
            checkAndMaybeStop()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isServiceRunning = false
        unregisterScreenUnlockReceiver()
        unregisterScreenOnReceiver()
        unregisterDateChangeReceiver()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Morning Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors for screen unlock during morning hours"
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
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.morning_monitor_active))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun registerScreenUnlockReceiver() {
        if (screenUnlockReceiver != null) return

        screenUnlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    Log.d(TAG, "Screen unlocked detected!")
                    handleScreenUnlock()
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenUnlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenUnlockReceiver, filter)
        }
        Log.d(TAG, "Screen unlock receiver registered")
    }

    private fun unregisterScreenUnlockReceiver() {
        screenUnlockReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Screen unlock receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        screenUnlockReceiver = null
    }

    /**
     * Register for SCREEN_ON as a fallback for USER_PRESENT.
     * When screen turns on, we check if device is unlocked and trigger blocking.
     * This helps on emulators and some devices where USER_PRESENT is unreliable.
     */
    private fun registerScreenOnReceiver() {
        if (screenOnReceiver != null) return

        screenOnReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    Log.d(TAG, "Screen ON detected, checking if unlocked...")
                    // Delay slightly to allow unlock to complete
                    serviceScope.launch {
                        delay(500) // Wait for keyguard to update
                        checkIfUnlockedAndHandle()
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOnReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOnReceiver, filter)
        }
        Log.d(TAG, "Screen ON receiver registered")
    }

    private fun unregisterScreenOnReceiver() {
        screenOnReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Screen ON receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen on receiver", e)
            }
        }
        screenOnReceiver = null
    }

    /**
     * Check if the device is unlocked and handle as if screen unlock occurred.
     * Used as fallback when USER_PRESENT doesn't fire.
     */
    private fun checkIfUnlockedAndHandle() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        val isScreenOn = powerManager?.isInteractive == true
        val isDeviceLocked = keyguardManager?.isDeviceLocked == true

        Log.d(TAG, "Screen check: isScreenOn=$isScreenOn, isDeviceLocked=$isDeviceLocked")

        // If screen is on and device is NOT locked, treat as unlocked
        if (isScreenOn && !isDeviceLocked) {
            Log.d(TAG, "Device is unlocked! Triggering blocking check.")
            handleScreenUnlock()
        }
    }

    private fun registerDateChangeReceiver() {
        if (dateChangeReceiver != null) return

        dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        Log.d(TAG, "Date/time changed detected! Resetting blocking state.")
                        // Force reset blocking state so it can start fresh for new day
                        BlockingState.forceReset()

                        // Immediately check if we should start blocking
                        // This handles the case where USER_PRESENT doesn't fire after date change
                        serviceScope.launch {
                            delay(1000) // Wait for system to settle
                            Log.d(TAG, "Checking blocking state after date change...")
                            checkIfUnlockedAndHandle()
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dateChangeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(dateChangeReceiver, filter)
        }
        Log.d(TAG, "Date change receiver registered")
    }

    private fun unregisterDateChangeReceiver() {
        dateChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Date change receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering date receiver", e)
            }
        }
        dateChangeReceiver = null
    }

    private fun handleScreenUnlock() {
        serviceScope.launch {
            try {
                val app = applicationContext as? MorningMindfulApp
                if (app == null) {
                    Log.e(TAG, "Could not get app instance")
                    return@launch
                }

                // Check if blocking is enabled
                val isEnabled = app.settingsRepository.isBlockingEnabled.first()
                if (!isEnabled) {
                    Log.d(TAG, "Blocking is disabled, stopping service")
                    stopSelf()
                    return@launch
                }

                // Check if we're within the morning window
                val currentHour = LocalTime.now().hour
                val morningStart = app.settingsRepository.morningStartHour.first()
                val morningEnd = app.settingsRepository.morningEndHour.first()

                if (currentHour < morningStart || currentHour >= morningEnd) {
                    Log.d(TAG, "Outside morning window, stopping service")
                    stopSelf()
                    return@launch
                }

                // Check if we already journaled today
                val requiredWords = app.settingsRepository.requiredWordCount.first()
                val todayEntry = app.journalRepository.getTodayEntry().first()
                if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                    Log.d(TAG, "Journal already completed today, stopping service")
                    BlockingState.setJournalCompletedToday(true)
                    stopSelf()
                    return@launch
                }

                // Get blocking duration from settings
                val blockingMinutes = app.settingsRepository.blockingDurationMinutes.first()

                // Start blocking period
                BlockingState.onFirstUnlock(blockingMinutes)
                Log.d(TAG, "Started blocking period for $blockingMinutes minutes")

                // Start the appropriate blocking service
                val blockingMode = app.settingsRepository.blockingMode.first()

                if (blockingMode == SettingsRepository.BLOCKING_MODE_GENTLE) {
                    Log.d(TAG, "Starting Gentle Reminder service")
                    UsageStatsBlockerService.start(this@MorningMonitorService)
                } else {
                    Log.d(TAG, "Starting Full Block service")
                    val serviceIntent = Intent(this@MorningMonitorService, MorningBlockerService::class.java)
                    startForegroundService(serviceIntent)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error handling unlock", e)
            }
        }
    }

    private suspend fun checkAndMaybeStop() {
        val app = applicationContext as? MorningMindfulApp ?: return

        // Check if blocking is enabled
        val isEnabled = app.settingsRepository.isBlockingEnabled.first()
        if (!isEnabled) {
            Log.d(TAG, "Blocking disabled, stopping")
            stopSelf()
            return
        }

        // Check if we're in the morning window
        val currentHour = LocalTime.now().hour
        val morningStart = app.settingsRepository.morningStartHour.first()
        val morningEnd = app.settingsRepository.morningEndHour.first()

        if (currentHour < morningStart || currentHour >= morningEnd) {
            Log.d(TAG, "Outside morning window, stopping")
            stopSelf()
            return
        }

        // Check if already journaled
        val requiredWords = app.settingsRepository.requiredWordCount.first()
        val todayEntry = app.journalRepository.getTodayEntry().first()
        if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
            Log.d(TAG, "Already journaled, stopping")
            BlockingState.setJournalCompletedToday(true)
            stopSelf()
            return
        }

        Log.d(TAG, "Service will continue running (morning window, not journaled)")
    }
}
