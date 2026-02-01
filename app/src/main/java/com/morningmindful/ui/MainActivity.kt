package com.morningmindful.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.ads.AdRequest
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        setupUI()
        observeViewModel()
        initializeAds()
    }

    private fun initializeAds() {
        try {
            // Check if AdMob Banner ID is configured
            val bannerId = BuildConfig.ADMOB_BANNER_ID
            if (bannerId.isNullOrBlank()) {
                Log.w("MainActivity", "AdMob Banner ID not configured, hiding ad view")
                binding.adView.visibility = android.view.View.GONE
                return
            }

            // Initialize the Mobile Ads SDK
            MobileAds.initialize(this) { initializationStatus ->
                Log.d("MainActivity", "AdMob initialized: $initializationStatus")
            }

            // Set adUnitId from BuildConfig (loaded from secrets.properties)
            binding.adView.adUnitId = bannerId

            // Load a banner ad
            val adRequest = AdRequest.Builder().build()
            binding.adView.loadAd(adRequest)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing ads", e)
            binding.adView.visibility = android.view.View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
        viewModel.refreshTodayStatus()
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
        if (!PermissionUtils.hasAllPermissions(this)) {
            binding.permissionWarning.visibility = View.VISIBLE
            binding.setupPermissionsButton.visibility = View.VISIBLE
        } else {
            binding.permissionWarning.visibility = View.GONE
            binding.setupPermissionsButton.visibility = View.GONE
        }
    }

    private fun showPermissionsDialog() {
        val hasAccessibility = PermissionUtils.hasAccessibilityPermission()
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)

        val message = buildString {
            append(getString(R.string.permissions_needed_intro))
            if (!hasAccessibility) {
                append(getString(R.string.accessibility_permission_needed))
            }
            if (!hasOverlay) {
                append(getString(R.string.overlay_permission_needed))
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setup_permissions)
            .setMessage(message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                PermissionUtils.getNextMissingPermissionIntent(this)?.let { intent ->
                    startActivity(intent)
                }
            }
            .setNegativeButton(R.string.later, null)
            .show()
    }

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
            number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
            else -> number.toString()
        }
    }
}
