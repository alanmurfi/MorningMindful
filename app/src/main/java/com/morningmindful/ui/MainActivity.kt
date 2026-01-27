package com.morningmindful.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
import com.morningmindful.databinding.ActivityMainBinding
import com.morningmindful.service.AppBlockerAccessibilityService
import com.morningmindful.ui.history.HistoryActivity
import com.morningmindful.ui.journal.JournalActivity
import com.morningmindful.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Main activity showing journal history, stats, and access to settings.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
        initializeAds()
    }

    private fun initializeAds() {
        // Initialize the Mobile Ads SDK
        MobileAds.initialize(this) { initializationStatus ->
            Log.d("MainActivity", "AdMob initialized: $initializationStatus")
        }

        // Load a banner ad
        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)
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
                        binding.currentStreakValue.text = "$streak days"
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
                            binding.todayStatusText.text = "Journal completed today"
                            binding.todayStatusIcon.setImageResource(android.R.drawable.ic_menu_day)
                            binding.writeJournalButton.text = "View Today's Entry"
                        } else {
                            binding.todayStatusText.text = "No journal entry yet today"
                            binding.writeJournalButton.text = "Write Now"
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        val hasAccessibility = AppBlockerAccessibilityService.isServiceRunning
        val hasOverlay = Settings.canDrawOverlays(this)

        if (!hasAccessibility || !hasOverlay) {
            binding.permissionWarning.visibility = View.VISIBLE
            binding.setupPermissionsButton.visibility = View.VISIBLE
        } else {
            binding.permissionWarning.visibility = View.GONE
            binding.setupPermissionsButton.visibility = View.GONE
        }
    }

    private fun showPermissionsDialog() {
        val hasAccessibility = AppBlockerAccessibilityService.isServiceRunning
        val hasOverlay = Settings.canDrawOverlays(this)

        val message = buildString {
            append("Morning Mindful needs these permissions to block apps:\n\n")
            if (!hasAccessibility) {
                append("• Accessibility Service - to detect when blocked apps open\n")
            }
            if (!hasOverlay) {
                append("• Display over other apps - to show the journal screen\n")
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Setup Permissions")
            .setMessage(message)
            .setPositiveButton("Open Settings") { _, _ ->
                if (!hasAccessibility) {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else if (!hasOverlay) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
            .setNegativeButton("Later", null)
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
