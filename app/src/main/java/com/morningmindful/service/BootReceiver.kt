package com.morningmindful.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives BOOT_COMPLETED broadcast to ensure our app is ready
 * to handle first unlock after device restart.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device booted, Morning Mindful ready")
            // The ScreenUnlockReceiver will handle the first unlock
            // No additional action needed here, but we could start
            // any initialization if required in the future
        }
    }
}
