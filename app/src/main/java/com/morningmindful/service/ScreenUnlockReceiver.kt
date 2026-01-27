package com.morningmindful.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.morningmindful.MorningMindfulApp
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Receives screen unlock events (USER_PRESENT) to detect first unlock of the day.
 */
class ScreenUnlockReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenUnlockReceiver"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_USER_PRESENT) return

        Log.d(TAG, "Screen unlocked (USER_PRESENT)")

        context?.let { ctx ->
            scope.launch {
                try {
                    val app = ctx.applicationContext as? MorningMindfulApp
                    if (app == null) {
                        Log.e(TAG, "Could not get app instance")
                        return@launch
                    }

                    // Check if blocking is enabled
                    val isEnabled = app.settingsRepository.isBlockingEnabled.first()
                    if (!isEnabled) {
                        Log.d(TAG, "Blocking is disabled in settings")
                        return@launch
                    }

                    // Check if we're within the morning window
                    val currentHour = LocalTime.now().hour
                    val morningStart = app.settingsRepository.morningStartHour.first()
                    val morningEnd = app.settingsRepository.morningEndHour.first()

                    if (currentHour < morningStart || currentHour >= morningEnd) {
                        Log.d(TAG, "Outside morning window ($morningStart:00 - $morningEnd:00), current hour: $currentHour")
                        return@launch
                    }
                    Log.d(TAG, "Within morning window ($morningStart:00 - $morningEnd:00)")

                    // Check if we already journaled today
                    val requiredWords = app.settingsRepository.requiredWordCount.first()
                    val todayEntry = app.journalRepository.getTodayEntry().first()
                    if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                        Log.d(TAG, "Journal already completed today")
                        BlockingState.setJournalCompletedToday(true)
                        return@launch
                    }

                    // Get blocking duration from settings
                    val blockingMinutes = app.settingsRepository.blockingDurationMinutes.first()

                    // Start blocking period
                    BlockingState.onFirstUnlock(blockingMinutes)
                    Log.d(TAG, "Started blocking period for $blockingMinutes minutes")

                    // Start foreground service to maintain blocking state
                    val serviceIntent = Intent(ctx, MorningBlockerService::class.java)
                    ctx.startForegroundService(serviceIntent)

                } catch (e: Exception) {
                    Log.e(TAG, "Error handling unlock", e)
                }
            }
        }
    }
}
