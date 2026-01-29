package com.morningmindful.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foreground service that maintains the blocking state and shows a persistent notification.
 * This ensures the app continues to function even if the system tries to kill background processes.
 */
class MorningBlockerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null

    companion object {
        private const val TAG = "MorningBlockerService"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")

        // Start as foreground service with notification
        startForeground(MorningMindfulApp.BLOCKING_NOTIFICATION_ID, createNotification())

        // Start timer to update notification and check if blocking should end
        startTimer()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }

    private fun createNotification(): Notification {
        val remainingSeconds = BlockingState.getRemainingSeconds()
        val minutes = (remainingSeconds / 60).toInt()
        val seconds = (remainingSeconds % 60).toInt()

        val contentText = if (BlockingState.journalCompletedToday.value) {
            getString(R.string.notification_journal_completed)
        } else if (remainingSeconds > 0) {
            getString(R.string.notification_time_remaining, minutes, seconds)
        } else {
            getString(R.string.notification_routine_complete)
        }

        // Intent to open journal activity
        val journalIntent = Intent(this, JournalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, journalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MorningMindfulApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000)  // Update every second

                // Check if we should still be blocking
                if (!BlockingState.shouldBlock()) {
                    Log.d(TAG, "Blocking period ended, stopping service")
                    stopSelf()
                    break
                }

                // Update notification with remaining time
                val notification = createNotification()
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
                notificationManager.notify(MorningMindfulApp.BLOCKING_NOTIFICATION_ID, notification)
            }
        }
    }
}
