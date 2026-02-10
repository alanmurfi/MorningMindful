package com.morningmindful.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.morningmindful.BuildConfig
import com.morningmindful.R
import com.morningmindful.databinding.ActivityMainBinding
import com.morningmindful.MorningMindfulApp
import com.morningmindful.ui.history.HistoryActivity
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.ui.onboarding.OnboardingActivity
import com.morningmindful.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.first
import com.morningmindful.util.PermissionUtils
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.service.AppBlockerAccessibilityService
import com.morningmindful.service.MorningMonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Main activity showing journal history, stats, and access to settings.
 * Annotated with @AndroidEntryPoint to enable Hilt injection.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if onboarding is needed
        lifecycleScope.launch {
            val hasCompletedOnboarding = MorningMindfulApp.getInstance()
                .settingsRepository.hasCompletedOnboarding.first()

            if (!hasCompletedOnboarding) {
                startActivity(Intent(this@MainActivity, OnboardingActivity::class.java))
                finish()
                return@launch
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupUI()
        observeViewModel()
        initializeAds()
        updateAnalyticsUserProperties()
    }

    private fun setupWindowInsets() {
        // Apply window insets to the ad container so it sits above the navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.adContainer) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = insets.bottom)
            windowInsets
        }
    }

    private fun initializeAds() {
        try {
            // Check if AdMob Banner ID is configured
            val bannerId = BuildConfig.ADMOB_BANNER_ID
            if (bannerId.isNullOrBlank()) {
                Log.w("MainActivity", "AdMob Banner ID not configured, hiding ad view")
                binding.adContainer.visibility = View.GONE
                return
            }

            // Initialize the Mobile Ads SDK
            MobileAds.initialize(this) { initializationStatus ->
                Log.d("MainActivity", "AdMob initialized: $initializationStatus")
            }

            // Create AdView programmatically to avoid XML initialization issues
            val adView = AdView(this)
            adView.setAdSize(AdSize.BANNER)
            adView.adUnitId = bannerId

            // Add listener to debug ad loading
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d("MainActivity", "Ad loaded successfully")
                    adView.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("MainActivity", "Ad failed to load: ${error.code} - ${error.message}")
                    // Error codes: https://developers.google.com/android/reference/com/google/android/gms/ads/AdRequest
                    // 0 = ERROR_CODE_INTERNAL_ERROR
                    // 1 = ERROR_CODE_INVALID_REQUEST
                    // 2 = ERROR_CODE_NETWORK_ERROR
                    // 3 = ERROR_CODE_NO_FILL (no ads available)
                }

                override fun onAdOpened() {
                    Log.d("MainActivity", "Ad opened")
                }

                override fun onAdClicked() {
                    Log.d("MainActivity", "Ad clicked")
                }

                override fun onAdClosed() {
                    Log.d("MainActivity", "Ad closed")
                }
            }

            // Add AdView to container
            binding.adContainer.addView(adView)

            // Load a banner ad
            val adRequest = AdRequest.Builder().build()
            adView.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing ads", e)
            binding.adContainer.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        checkAccessibilityServiceHealth()
        viewModel.refreshTodayStatus()
        ensureMorningMonitorRunning()
    }

    /**
     * Ensure the MorningMonitorService is running if we're in the morning window.
     * The service handles all blocking logic - we just make sure it's started.
     */
    private fun ensureMorningMonitorRunning() {
        lifecycleScope.launch {
            val settings = MorningMindfulApp.getInstance().settingsRepository

            // Check if blocking is enabled
            val isEnabled = settings.isBlockingEnabled.first()
            if (!isEnabled) return@launch

            // Check if we're in the morning window
            val currentHour = LocalTime.now().hour
            val startHour = settings.morningStartHour.first()
            val endHour = settings.morningEndHour.first()

            if (currentHour < startHour || currentHour >= endHour) {
                return@launch
            }

            // Check if already journaled today
            val requiredWords = settings.requiredWordCount.first()
            val todayEntry = MorningMindfulApp.getInstance().journalRepository.getTodayEntry().first()
            if (todayEntry != null && todayEntry.wordCount >= requiredWords) {
                com.morningmindful.util.BlockingState.setJournalCompletedToday(true)
                return@launch
            }

            // Start the monitor service if not running - it handles all blocking logic
            if (!MorningMonitorService.isServiceRunning) {
                Log.d("MainActivity", "Starting MorningMonitorService from onResume")
                MorningMonitorService.start(this@MainActivity)
            }
        }
    }

    private fun setupUI() {
        // Settings button
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Write journal button
        binding.writeJournalButton.setOnClickListener {
            startActivity(Intent(this, JournalActivity::class.java))
        }

        // Permission setup button
        binding.setupPermissionsButton.setOnClickListener {
            showPermissionsDialog()
        }

        // View all entries button
        binding.viewAllButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Stats
                launch {
                    viewModel.totalEntries.collectLatest { count ->
                        binding.totalEntriesValue.text = count.toString()
                    }
                }

                launch {
                    viewModel.totalWords.collectLatest { words ->
                        binding.totalWordsValue.text = formatNumber(words)
                    }
                }

                launch {
                    viewModel.currentStreak.collectLatest { streak ->
                        binding.currentStreakValue.text = getString(R.string.streak_days, streak)
                    }
                }

                // Recent entries
                launch {
                    viewModel.recentEntries.collectLatest { entries ->
                        if (entries.isEmpty()) {
                            binding.emptyState.visibility = View.VISIBLE
                            binding.recentEntriesCard.visibility = View.GONE
                        } else {
                            binding.emptyState.visibility = View.GONE
                            binding.recentEntriesCard.visibility = View.VISIBLE

                            // Show last 3 entries preview
                            val previewText = entries.take(3).joinToString("\n\n") { entry ->
                                val preview = entry.content.take(100) + if (entry.content.length > 100) "..." else ""
                                "${entry.date}: $preview"
                            }
                            binding.recentEntriesPreview.text = previewText
                        }
                    }
                }

                // Today's status
                launch {
                    viewModel.journalCompletedToday.collectLatest { completed ->
                        if (completed) {
                            binding.todayStatusText.text = getString(R.string.journal_completed_today)
                            binding.todayStatusIcon.setImageResource(android.R.drawable.ic_menu_day)
                            binding.writeJournalButton.text = getString(R.string.view_todays_entry)
                        } else {
                            binding.todayStatusText.text = getString(R.string.no_journal_yet)
                            binding.writeJournalButton.text = getString(R.string.write_now)
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        lifecycleScope.launch {
            val blockingMode = MorningMindfulApp.getInstance()
                .settingsRepository.blockingMode.first()

            val hasRequiredPermissions = if (blockingMode == SettingsRepository.BLOCKING_MODE_GENTLE) {
                // Gentle mode: needs Usage Stats and Overlay
                PermissionUtils.hasUsageStatsPermission(this@MainActivity) &&
                    PermissionUtils.hasOverlayPermission(this@MainActivity)
            } else {
                // Full mode: needs Accessibility and Overlay
                PermissionUtils.hasAccessibilityPermission() &&
                    PermissionUtils.hasOverlayPermission(this@MainActivity)
            }

            if (!hasRequiredPermissions) {
                binding.permissionWarning.visibility = View.VISIBLE
                binding.setupPermissionsButton.visibility = View.VISIBLE
            } else {
                binding.permissionWarning.visibility = View.GONE
                binding.setupPermissionsButton.visibility = View.GONE
            }
        }
    }

    private fun showPermissionsDialog() {
        lifecycleScope.launch {
            val blockingMode = MorningMindfulApp.getInstance()
                .settingsRepository.blockingMode.first()

            val isGentleMode = blockingMode == SettingsRepository.BLOCKING_MODE_GENTLE
            val hasOverlay = PermissionUtils.hasOverlayPermission(this@MainActivity)

            val message = buildString {
                append(getString(R.string.permissions_needed_intro))
                if (isGentleMode) {
                    val hasUsageStats = PermissionUtils.hasUsageStatsPermission(this@MainActivity)
                    if (!hasUsageStats) {
                        append("\n\n• Usage Access - to see which app you're using")
                    }
                } else {
                    val hasAccessibility = PermissionUtils.hasAccessibilityPermission()
                    if (!hasAccessibility) {
                        append(getString(R.string.accessibility_permission_needed))
                    }
                }
                if (!hasOverlay) {
                    append(getString(R.string.overlay_permission_needed))
                }
            }

            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.setup_permissions)
                .setMessage(message)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    // Open appropriate settings based on mode
                    if (isGentleMode) {
                        val hasUsageStats = PermissionUtils.hasUsageStatsPermission(this@MainActivity)
                        when {
                            !hasUsageStats -> PermissionUtils.openUsageStatsSettings(this@MainActivity)
                            !hasOverlay -> PermissionUtils.openOverlaySettings(this@MainActivity)
                        }
                    } else {
                        PermissionUtils.getNextMissingPermissionIntent(this@MainActivity)?.let { intent ->
                            startActivity(intent)
                        }
                    }
                }
                .setNegativeButton(R.string.later, null)
                .show()
        }
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }

    /**
     * Check if the Accessibility Service is in an inconsistent state:
     * - User has enabled the permission in system settings
     * - But the service isn't actually running (can happen after app reinstall/update)
     *
     * This prompts the user to toggle the service off and on to re-establish the connection.
     */
    private fun checkAccessibilityServiceHealth() {
        lifecycleScope.launch {
            val blockingMode = MorningMindfulApp.getInstance()
                .settingsRepository.blockingMode.first()

            // Only relevant for Full Block mode
            if (blockingMode != SettingsRepository.BLOCKING_MODE_FULL) {
                return@launch
            }

            // Check if permission appears granted but service isn't running
            val permissionGranted = PermissionUtils.hasAccessibilityPermission()
            val serviceRunning = AppBlockerAccessibilityService.isServiceRunning

            if (permissionGranted && !serviceRunning) {
                Log.w("MainActivity", "Accessibility service inconsistency detected: permission=$permissionGranted, running=$serviceRunning")
                showAccessibilityServiceRestartDialog()
            }
        }
    }

    private fun showAccessibilityServiceRestartDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.service_restart_needed_title)
            .setMessage(R.string.service_restart_needed_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                PermissionUtils.openAccessibilitySettings(this)
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    /**
     * Update Firebase Analytics user properties with current user state.
     * Called on app launch to keep user properties current.
     */
    private fun updateAnalyticsUserProperties() {
        lifecycleScope.launch {
            val app = MorningMindfulApp.getInstance()
            val settings = app.settingsRepository

            // Get current values
            val totalEntries = viewModel.totalEntries.first()
            val currentStreak = viewModel.currentStreak.first()
            val blockingEnabled = settings.isBlockingEnabled.first()
            val blockingMode = if (settings.blockingMode.first() == SettingsRepository.BLOCKING_MODE_GENTLE) "gentle" else "full"

            // Update analytics
            com.morningmindful.util.Analytics.setUserProperties(
                totalEntries = totalEntries,
                currentStreak = currentStreak,
                blockingEnabled = blockingEnabled,
                blockingMode = blockingMode
            )

            // Track app opened
            com.morningmindful.util.Analytics.trackAppOpened()

            // Check for streak milestones
            if (currentStreak in listOf(3, 7, 14, 30, 60, 90, 180, 365)) {
                com.morningmindful.util.Analytics.trackStreakMilestone(currentStreak)
            }
        }
    }
}
