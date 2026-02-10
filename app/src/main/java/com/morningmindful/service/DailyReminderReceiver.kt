package com.morningmindful.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.util.Analytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that shows daily reminder notifications.
 *
 * When triggered by AlarmManager:
 * 1. Checks if user has already journaled today
 * 2. If not, shows a reminder notification
 * 3. Reschedules alarm for tomorrow
 */
class DailyReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DailyReminderReceiver"
        private const val CHANNEL_ID = "daily_reminder_channel"
        private const val NOTIFICATION_ID = 5001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Daily reminder triggered")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? MorningMindfulApp
                if (app == null) {
                    Log.e(TAG, "Could not get app instance")
                    return@launch
                }

                // Check if reminder is still enabled
                if (!app.settingsRepository.isDailyReminderEnabledSync()) {
                    Log.d(TAG, "Daily reminder disabled, skipping")
                    return@launch
                }

                // Check if user has already journaled today
                val todayEntry = app.journalRepository.getTodayEntry().first()
                val requiredWords = app.settingsRepository.requiredWordCount.first()

                if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                    Log.d(TAG, "User already journaled today, skipping notification")
                } else {
                    // Show notification
                    showReminderNotification(context)
                    Analytics.trackReminderShown()
                }

                // Reschedule for tomorrow
                val hour = app.settingsRepository.getDailyReminderHourSync()
                val minute = app.settingsRepository.getDailyReminderMinuteSync()
                DailyReminderScheduler.rescheduleForTomorrow(context, hour, minute)

            } catch (e: Exception) {
                Log.e(TAG, "Error handling reminder", e)
            }
        }
    }

    private fun showReminderNotification(context: Context) {
        createNotificationChannel(context)

        // Intent to open journal when notification is tapped
        val journalIntent = Intent(context, JournalActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            journalIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Get a random motivational message
        val messages = context.resources.getStringArray(R.array.reminder_messages)
        val message = messages.random()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)

        Log.d(TAG, "Showed reminder notification")
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
