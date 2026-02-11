package com.morningmindful.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.morningmindful.BuildConfig
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

    // Blocking duration (cached)
    private var blockingDurationMinutes = 15

    // Screen unlock receiver for starting the timer
    private var unlockReceiver: BroadcastReceiver? = null

    // Date change receiver for resetting state on new day
    private var dateChangeReceiver: BroadcastReceiver? = null

    // Job for journal entry listener (needs to be restarted on date change)
    private var journalListenerJob: kotlinx.coroutines.Job? = null

    companion object {
        private const val TAG = "AppBlockerService"
        private const val BLOCK_COOLDOWN_MS = 1000L  // Prevent rapid-fire redirects

        @Volatile
        var isServiceRunning = false
            private set

        /** Debug logging helper - only logs in debug builds */
        private fun logDebug(message: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, message)
        }
    }

    override fun onCreate() {
        super.onCreate()
        logDebug( "Accessibility Service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        logDebug( "Accessibility Service connected")

        // Start all reactive listeners - no blocking calls
        startSettingsListeners()

        // Register unlock receiver dynamically (manifest-declared doesn't work reliably on newer Android)
        registerUnlockReceiver()

        // Register date change receiver to reset state on new day
        registerDateChangeReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        unregisterUnlockReceiver()
        unregisterDateChangeReceiver()
        logDebug( "Accessibility Service destroyed")
    }

    /**
     * Register a dynamic broadcast receiver for USER_PRESENT (screen unlock).
     * Manifest-declared receivers don't work reliably for this broadcast on Android 8+.
     */
    private fun registerUnlockReceiver() {
        if (unlockReceiver != null) return

        unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    logDebug( "Screen unlocked (USER_PRESENT)")
                    handleScreenUnlock()
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(unlockReceiver, filter)
        }
        logDebug("Unlock receiver registered")
    }

    private fun unregisterUnlockReceiver() {
        unlockReceiver?.let {
            try {
                unregisterReceiver(it)
                logDebug( "Unlock receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering unlock receiver", e)
            }
        }
        unlockReceiver = null
    }

    /**
     * Register a date change receiver to reset state when the date changes.
     * This ensures the cached journal word count is refreshed for the new day.
     */
    private fun registerDateChangeReceiver() {
        if (dateChangeReceiver != null) return

        dateChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_DATE_CHANGED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        logDebug("Date/time changed detected! Resetting cached state.")
                        handleDateChange()
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
        logDebug("Date change receiver registered")
    }

    private fun unregisterDateChangeReceiver() {
        dateChangeReceiver?.let {
            try {
                unregisterReceiver(it)
                logDebug("Date change receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering date change receiver", e)
            }
        }
        dateChangeReceiver = null
    }

    /**
     * Handle date change - reset cached journal word count and restart listener.
     */
    private fun handleDateChange() {
        // Reset cached journal word count for new day
        todayJournalWordCount = 0

        // Force reset BlockingState for new day
        BlockingState.forceReset()

        // Cancel and restart the journal listener to query the new date
        journalListenerJob?.cancel()
        startJournalEntryListener()

        logDebug("Date change handled - reset journal word count and restarted listener")
    }

    /**
     * Handle screen unlock - start the blocking timer if within morning window.
     */
    private fun handleScreenUnlock() {
        // Check if blocking is enabled
        if (!isBlockingEnabled) {
            logDebug( "Blocking is disabled in settings")
            return
        }

        // Check if we're within the morning window
        val currentHour = LocalTime.now().hour
        if (currentHour < morningStartHour || currentHour >= morningEndHour) {
            logDebug( "Outside morning window ($morningStartHour:00 - $morningEndHour:00), current hour: $currentHour")
            return
        }
        logDebug( "Within morning window ($morningStartHour:00 - $morningEndHour:00)")

        // Check if already journaled today
        if (todayJournalWordCount >= requiredWordCount) {
            logDebug( "Journal already completed today")
            BlockingState.setJournalCompletedToday(true)
            return
        }

        // Start blocking period with timer
        BlockingState.onFirstUnlock(blockingDurationMinutes)
        logDebug( "Started blocking period for $blockingDurationMinutes minutes")

        // Start foreground service to maintain blocking state and show notification
        val serviceIntent = Intent(this, MorningBlockerService::class.java)
        startForegroundService(serviceIntent)
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
                    logDebug( "Settings updated: isBlockingEnabled = $enabled")
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
                    logDebug( "Settings updated: morningStartHour = $hour")
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
                    logDebug( "Settings updated: morningEndHour = $hour")
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
                    logDebug( "Settings updated: requiredWordCount = $count")
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
                    logDebug( "Loaded ${blockedPackages.size} blocked apps")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading blocked apps, using defaults", e)
                blockedPackages = BlockedApps.DEFAULT_BLOCKED_PACKAGES
            }
        }

        // Listen to blocking duration
        serviceScope.launch {
            try {
                app.settingsRepository.blockingDurationMinutes.collect { minutes ->
                    blockingDurationMinutes = minutes
                    logDebug( "Settings updated: blockingDurationMinutes = $minutes")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listening to blockingDurationMinutes", e)
            }
        }

        // Start the journal entry listener
        startJournalEntryListener()
    }

    /**
     * Start listening to journal entry changes.
     * This is separate so it can be restarted when the date changes.
     */
    private fun startJournalEntryListener() {
        val app = MorningMindfulApp.getInstance()

        // Listen to journal entry changes - this is the key optimization
        // Instead of querying the database on every accessibility event,
        // we cache the word count and update it reactively
        journalListenerJob = serviceScope.launch {
            try {
                app.journalRepository.getTodayEntry().collect { entry ->
                    todayJournalWordCount = entry?.wordCount ?: 0
                    logDebug( "Journal updated: todayJournalWordCount = $todayJournalWordCount")

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

        logDebug( "App opened: $packageName")

        // Ignore our own app
        if (packageName == applicationContext.packageName) return

        // Ignore system UI and launcher
        if (isSystemPackage(packageName)) return

        // Check if this app should be blocked
        if (!blockedPackages.contains(packageName)) {
            logDebug( "App not in blocked list: $packageName")
            return
        }

        // Check if blocking should be active (morning window + not journaled)
        // This is now instant - no database queries, just cached values
        if (!shouldBlockNow()) {
            logDebug( "Blocking not active right now")
            return
        }

        // Prevent rapid-fire blocking
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && now - lastBlockTime < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockTime = now

        logDebug( "Blocking app: $packageName")

        // Start timer if not already started (fallback in case unlock wasn't detected)
        if (BlockingState.blockingEndTime.value == null) {
            logDebug( "Timer not started yet, starting now as fallback")
            BlockingState.onFirstUnlock(blockingDurationMinutes)
        }

        // Always ensure the foreground service is running for notification
        val serviceIntent = Intent(this, MorningBlockerService::class.java)
        startForegroundService(serviceIntent)

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
     * 4. Blocking timer has not expired OR timer not started yet (we'll start it)
     */
    private fun shouldBlockNow(): Boolean {
        // Check if blocking is enabled (cached value)
        if (!isBlockingEnabled) {
            logDebug( "Blocking disabled in settings")
            return false
        }

        // Check morning window (cached values)
        val currentHour = LocalTime.now().hour
        if (currentHour < morningStartHour || currentHour >= morningEndHour) {
            logDebug( "Outside morning window: $currentHour not in $morningStartHour-$morningEndHour")
            return false
        }

        // Check if already journaled today (cached values)
        if (todayJournalWordCount >= requiredWordCount) {
            logDebug( "Journal already completed today ($todayJournalWordCount words)")
            return false
        }

        // Check if journal was already completed today via BlockingState
        if (BlockingState.journalCompletedToday.value) {
            logDebug("Journal already completed today (BlockingState)")
            return false
        }

        // If blocking timer hasn't started yet, that's OK - we'll start it when blocking
        // This handles the case where USER_PRESENT wasn't detected but we're in morning window
        val timerStarted = BlockingState.blockingEndTime.value != null
        if (timerStarted) {
            // Timer is running - check if it's expired
            if (!BlockingState.shouldBlock()) {
                logDebug("Blocking timer has expired")
                return false
            }
        } else {
            // Timer not started yet - we're in morning window and haven't journaled
            // We should block and will start the timer in onAccessibilityEvent
            logDebug("Timer not started yet, but in morning window - will start timer")
        }

        logDebug( "Blocking is active! Morning window: $morningStartHour-$morningEndHour, current: $currentHour")
        return true
    }

    override fun onInterrupt() {
        logDebug( "Accessibility Service interrupted")
    }

    /**
     * Redirect the user to the journal activity.
     */
    private fun redirectToJournal(blockedPackage: String) {
        // Track app redirect
        com.morningmindful.util.Analytics.trackAppRedirected(blockedPackage)

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
     * Note: Chrome (com.android.chrome) is NOT a system package and CAN be blocked.
     */
    private fun isSystemPackage(packageName: String): Boolean {
        // Explicit list of blockable apps that start with "com.android."
        val blockableAndroidApps = setOf(
            "com.android.chrome",  // Chrome browser - can be blocked
        )

        // If it's in the blockable list, it's NOT a system package
        if (blockableAndroidApps.contains(packageName)) {
            return false
        }

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
