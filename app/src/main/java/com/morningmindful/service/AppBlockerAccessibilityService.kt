package com.morningmindful.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.morningmindful.MorningMindfulApp
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.util.BlockedApps
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

/**
 * Accessibility Service that monitors app launches and redirects to the journal
 * when a blocked app is opened during the morning blocking period.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var blockedPackages: Set<String> = BlockedApps.DEFAULT_BLOCKED_PACKAGES
    private var lastBlockedPackage: String? = null
    private var lastBlockTime: Long = 0

    companion object {
        private const val TAG = "AppBlockerService"
        private const val BLOCK_COOLDOWN_MS = 1000L  // Prevent rapid-fire redirects

        @Volatile
        var isServiceRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Accessibility Service created")
        loadBlockedApps()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "Accessibility Service connected")

        // Check if journal was already completed today
        serviceScope.launch {
            try {
                val app = MorningMindfulApp.getInstance()
                val todayEntry = app.journalRepository.getTodayEntry().first()
                if (todayEntry != null && todayEntry.wordCount >= getRequiredWordCount()) {
                    BlockingState.setJournalCompletedToday(true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking today's journal", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.d(TAG, "Accessibility Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only handle window state changed events (app launches/switches)
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        Log.d(TAG, "App opened: $packageName")

        // Ignore our own app
        if (packageName == applicationContext.packageName) return

        // Ignore system UI and launcher
        if (isSystemPackage(packageName)) return

        // Check if this app should be blocked
        if (!blockedPackages.contains(packageName)) {
            Log.d(TAG, "App not in blocked list: $packageName")
            return
        }

        // Check if blocking should be active (morning window + not journaled)
        if (!shouldBlockNow()) {
            Log.d(TAG, "Blocking not active right now")
            return
        }

        // Prevent rapid-fire blocking
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockTime < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = now

        Log.d(TAG, "Blocking app: $packageName")
        redirectToJournal(packageName)
    }

    /**
     * Check if we should block right now based on:
     * 1. Blocking is enabled in settings
     * 2. Current time is within morning window
     * 3. Journal not completed today
     */
    private fun shouldBlockNow(): Boolean {
        return try {
            runBlocking {
                val app = MorningMindfulApp.getInstance()

                // Check if blocking is enabled
                val isEnabled = app.settingsRepository.isBlockingEnabled.first()
                if (!isEnabled) {
                    Log.d(TAG, "Blocking disabled in settings")
                    return@runBlocking false
                }

                // Check morning window
                val currentHour = LocalTime.now().hour
                val morningStart = app.settingsRepository.morningStartHour.first()
                val morningEnd = app.settingsRepository.morningEndHour.first()

                if (currentHour < morningStart || currentHour >= morningEnd) {
                    Log.d(TAG, "Outside morning window: $currentHour not in $morningStart-$morningEnd")
                    return@runBlocking false
                }

                // Check if already journaled today
                val requiredWords = app.settingsRepository.requiredWordCount.first()
                val todayEntry = app.journalRepository.getTodayEntry().first()
                if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                    Log.d(TAG, "Journal already completed today (${todayEntry.wordCount} words)")
                    return@runBlocking false
                }

                Log.d(TAG, "Blocking is active! Morning window: $morningStart-$morningEnd, current: $currentHour")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking block status", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    /**
     * Redirect the user to the journal activity.
     */
    private fun redirectToJournal(blockedPackage: String) {
        val intent = Intent(this, JournalActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(JournalActivity.EXTRA_BLOCKED_APP, blockedPackage)
        }
        startActivity(intent)
    }

    /**
     * Check if a package is a system package that shouldn't be blocked.
     */
    private fun isSystemPackage(packageName: String): Boolean {
        val systemPackages = setOf(
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",          // Samsung
            "com.miui.home",                          // Xiaomi
            "com.huawei.android.launcher",           // Huawei
            "com.oppo.launcher",                      // Oppo
            "com.android.settings",
            "com.android.vending",                    // Play Store
        )
        return systemPackages.contains(packageName) ||
                packageName.startsWith("com.android.") ||
                packageName.startsWith("android.")
    }

    /**
     * Load blocked apps from settings.
     */
    private fun loadBlockedApps() {
        serviceScope.launch {
            try {
                val app = MorningMindfulApp.getInstance()
                app.settingsRepository.blockedApps.collect { apps ->
                    blockedPackages = apps
                    Log.d(TAG, "Loaded ${blockedPackages.size} blocked apps")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked apps, using defaults", e)
                blockedPackages = BlockedApps.DEFAULT_BLOCKED_PACKAGES
            }
        }
    }

    /**
     * Get required word count from settings.
     */
    private fun getRequiredWordCount(): Int {
        return try {
            runBlocking {
                val app = MorningMindfulApp.getInstance()
                app.settingsRepository.requiredWordCount.first()
            }
        } catch (e: Exception) {
            200  // Default
        }
    }
}
