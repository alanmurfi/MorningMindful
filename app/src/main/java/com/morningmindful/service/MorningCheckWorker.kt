package com.morningmindful.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.morningmindful.MorningMindfulApp
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks if blocking should be active.
 *
 * This replaces the unreliable USER_PRESENT broadcast receiver which doesn't
 * work on modern Android when the app is in the background.
 *
 * The worker runs every 15 minutes and checks:
 * 1. If we're within the morning window
 * 2. If blocking is enabled
 * 3. If the user has already journaled today
 * 4. If blocking should be started
 */
class MorningCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MorningCheckWorker"
        private const val WORK_NAME = "morning_check_work"

        /**
         * Schedule the periodic morning check worker.
         * Should be called when the app starts and when settings change.
         */
        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<MorningCheckWorker>(
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.d(TAG, "Morning check worker scheduled")
        }

        /**
         * Cancel the periodic worker.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Morning check worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Morning check worker running")

        try {
            val app = applicationContext as? MorningMindfulApp
            if (app == null) {
                Log.e(TAG, "Could not get app instance")
                return Result.success()
            }

            // Check if blocking is enabled
            val isEnabled = app.settingsRepository.isBlockingEnabled.first()
            if (!isEnabled) {
                Log.d(TAG, "Blocking is disabled in settings")
                return Result.success()
            }

            // Check if we're within the morning window
            val currentHour = LocalTime.now().hour
            val morningStart = app.settingsRepository.morningStartHour.first()
            val morningEnd = app.settingsRepository.morningEndHour.first()

            if (currentHour < morningStart || currentHour >= morningEnd) {
                Log.d(TAG, "Outside morning window ($morningStart:00 - $morningEnd:00), current hour: $currentHour")
                // Stop monitor service if running outside morning window
                if (MorningMonitorService.isServiceRunning) {
                    MorningMonitorService.stop(applicationContext)
                }
                return Result.success()
            }
            Log.d(TAG, "Within morning window ($morningStart:00 - $morningEnd:00)")

            // Check if we already journaled today
            val requiredWords = app.settingsRepository.requiredWordCount.first()
            val todayEntry = app.journalRepository.getTodayEntry().first()
            if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                Log.d(TAG, "Journal already completed today (${todayEntry.wordCount} words)")
                BlockingState.setJournalCompletedToday(true)
                // Stop monitor service if running since journal is complete
                if (MorningMonitorService.isServiceRunning) {
                    MorningMonitorService.stop(applicationContext)
                }
                return Result.success()
            }

            // Start the morning monitor service to detect screen unlocks reliably
            // The service handles all blocking logic - we just ensure it's running
            if (!MorningMonitorService.isServiceRunning) {
                Log.d(TAG, "Starting MorningMonitorService")
                MorningMonitorService.start(applicationContext)
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in morning check worker", e)
            return Result.success() // Don't retry on error, will run again in 15 mins
        }
    }
}
