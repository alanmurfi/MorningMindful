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
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Accessibility Service that monitors app launches and redirects to the journal
 * when a blocked app is opened during the morning blocking period.
 *
 * Performance: Uses reactive caching to avoid blocking the accessibility thread.
 * All settings are cached locally and updated via Flow.collect() in the background.
 */
class AppBlockerAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Cached blocked packages - updated reactively
    private var blockedPackages: Set<String> = BlockedApps.DEFAULT_BLOCKED_PACKAGES

    // Cached settings - updated reactively (no runBlocking needed)
    private var isBlockingEnabled = true
    private var morningStartHour = 5
    private var morningEndHour = 10
    private var requiredWordCount = 200
    private var todayJournalWordCount = 0

    // Rate limiting for redirects
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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.d(TAG, "Accessibility Service connected")

        // Start all reactive listeners - no blocking calls
        startSettingsListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.d(TAG, "Accessibility Service destroyed")
    }

    /**
     * Start reactive listeners for all settings and journal state.
     * These run in the background and update cached values automatically.
     * No runBlocking needed - the accessibility thread is never blocked.
     */
    private fun startSettingsListeners() {
        val app = MorningMindfulApp.getInstance()

        // Listen to blocking enabled setting
        serviceScope.launch {
            try {
                app.settingsRepository.isBlockingEnabled.collect { enabled ->
                    isBlockingEnabled = enabled
                    Log.d(TAG, "Settings updated: isBlockingEnabled = $enabled")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to isBlockingEnabled", e)
            }
        }

        // Listen to morning start hour
        serviceScope.launch {
            try {
                app.settingsRepository.morningStartHour.collect { hour ->
                    morningStartHour = hour
                    Log.d(TAG, "Settings updated: morningStartHour = $hour")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to morningStartHour", e)
            }
        }

        // Listen to morning end hour
        serviceScope.launch {
            try {
                app.settingsRepository.morningEndHour.collect { hour ->
                    morningEndHour = hour
                    Log.d(TAG, "Settings updated: morningEndHour = $hour")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to morningEndHour", e)
            }
        }

        // Listen to required word count
        serviceScope.launch {
            try {
                app.settingsRepository.requiredWordCount.collect { count ->
                    requiredWordCount = count
                    Log.d(TAG, "Settings updated: requiredWordCount = $count")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to requiredWordCount", e)
            }
        }

        // Listen to blocked apps list
        serviceScope.launch {
            try {
                app.settingsRepository.blockedApps.collect { apps ->
                    blockedPackages = apps
                    Log.d(TAG, "Loaded ${blockedPackages.size} blocked apps")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked apps, using defaults", e)
                blockedPackages = BlockedApps.DEFAULT_BLOCKED_PACKAGES
            }
        }

        // Listen to journal entry changes - this is the key optimization
        // Instead of querying the database on every accessibility event,
        // we cache the word count and update it reactively
        serviceScope.launch {
            try {
                app.journalRepository.getTodayEntry().collect { entry ->
                    todayJournalWordCount = entry?.wordCount ?: 0
                    Log.d(TAG, "Journal updated: todayJournalWordCount = $todayJournalWordCount")

                    // Update BlockingState if journal is completed
                    if (todayJournalWordCount >= requiredWordCount) {
                        BlockingState.setJournalCompletedToday(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to journal entry", e)
            }
        }
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
        // This is now instant - no database queries, just cached values
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
     * Check if we should block right now based on cached values.
     *
     * This method is now instant (no blocking) because all values are cached
     * and updated reactively by the Flow listeners in startSettingsListeners().
     *
     * Checks:
     * 1. Blocking is enabled in settings
     * 2. Current time is within morning window
     * 3. Journal not completed today (word count < required)
     */
    private fun shouldBlockNow(): Boolean {
        // Check if blocking is enabled (cached value)
        if (!isBlockingEnabled) {
            Log.d(TAG, "Blocking disabled in settings")
            return false
        }

        // Check morning window (cached values)
        val currentHour = LocalTime.now().hour
        if (currentHour < morningStartHour || currentHour >= morningEndHour) {
            Log.d(TAG, "Outside morning window: $currentHour not in $morningStartHour-$morningEndHour")
            return false
        }

        // Check if already journaled today (cached values)
        if (todayJournalWordCount >= requiredWordCount) {
            Log.d(TAG, "Journal already completed today ($todayJournalWordCount words)")
            return false
        }

        Log.d(TAG, "Blocking is active! Morning window: $morningStartHour-$morningEndHour, current: $currentHour")
        return true
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
}
