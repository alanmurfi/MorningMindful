package com.morningmindful.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.morningmindful.MorningMindfulApp
import com.morningmindful.databinding.ActivitySettingsBinding
import com.morningmindful.service.AppBlockerAccessibilityService
import com.morningmindful.ui.upgrade.UpgradeActivity
import com.morningmindful.util.BlockedApps
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.Factory }
    private lateinit var blockedAppsAdapter: BlockedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updatePremiumStatus()
    }

    private fun updatePremiumStatus() {
        val app = application as MorningMindfulApp
        val isPremium = app.premiumRepository.hasPremiumAccess()

        if (isPremium) {
            binding.upgradeCard.visibility = View.GONE
        } else {
            binding.upgradeCard.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Blocking enabled switch
        binding.blockingEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBlockingEnabled(isChecked)
        }

        // Blocking duration slider
        binding.durationSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setBlockingDuration(value.toInt())
            }
            binding.durationValue.text = "${value.toInt()} minutes"
        }

        // Word count slider
        binding.wordCountSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setRequiredWordCount(value.toInt())
            }
            binding.wordCountValue.text = "${value.toInt()} words"
        }

        // Morning start slider
        binding.morningStartSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setMorningStartHour(value.toInt())
                // Ensure end time is always after start time
                if (value >= binding.morningEndSlider.value) {
                    binding.morningEndSlider.value = (value + 1).coerceAtMost(24f)
                }
            }
            binding.morningStartValue.text = formatHour(value.toInt())
        }

        // Morning end slider
        binding.morningEndSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setMorningEndHour(value.toInt())
                // Ensure start time is always before end time
                if (value <= binding.morningStartSlider.value) {
                    binding.morningStartSlider.value = (value - 1).coerceAtLeast(0f)
                }
            }
            binding.morningEndValue.text = formatHour(value.toInt())
        }

        // Blocked apps recycler
        blockedAppsAdapter = BlockedAppsAdapter(
            onAppToggled = { packageName, isBlocked ->
                viewModel.toggleBlockedApp(packageName, isBlocked)
            }
        )
        binding.blockedAppsRecycler.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            adapter = blockedAppsAdapter
        }

        // Add custom app button
        binding.addCustomAppButton.setOnClickListener {
            showInstalledAppsDialog()
        }

        // Permission buttons
        binding.accessibilityPermissionButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.overlayPermissionButton.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        // Reset today's progress
        binding.resetTodayButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset Today's Progress?")
                .setMessage("This will delete today's journal entry so app blocking will activate again. Use this for testing or if you want to redo your journal.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetTodayProgress()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Reset to defaults
        binding.resetDefaultsButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Reset All Settings?")
                .setMessage("This will reset all settings and blocked apps to their default values.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetToDefaults()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Privacy Policy
        binding.privacyPolicyButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
            startActivity(intent)
        }

        // Upgrade to Premium
        binding.upgradeCard.setOnClickListener {
            startActivity(Intent(this, UpgradeActivity::class.java))
        }

        // App version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.appVersion.text = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            binding.appVersion.text = "1.0"
        }
    }

    companion object {
        private const val PRIVACY_POLICY_URL = "https://alanmurfi.github.io/MorningMindful/"
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Blocking enabled
                launch {
                    viewModel.isBlockingEnabled.collectLatest { enabled ->
                        binding.blockingEnabledSwitch.isChecked = enabled
                    }
                }

                // Blocking duration
                launch {
                    viewModel.blockingDurationMinutes.collectLatest { minutes ->
                        binding.durationSlider.value = minutes.toFloat()
                        binding.durationValue.text = "$minutes minutes"
                    }
                }

                // Required word count
                launch {
                    viewModel.requiredWordCount.collectLatest { count ->
                        binding.wordCountSlider.value = count.toFloat()
                        binding.wordCountValue.text = "$count words"
                    }
                }

                // Blocked apps
                launch {
                    viewModel.blockedApps.collectLatest { apps ->
                        val appsList = getBlockedAppsList(apps)
                        blockedAppsAdapter.submitList(appsList)
                    }
                }

                // Morning start hour
                launch {
                    viewModel.morningStartHour.collectLatest { hour ->
                        binding.morningStartSlider.value = hour.toFloat()
                        binding.morningStartValue.text = formatHour(hour)
                    }
                }

                // Morning end hour
                launch {
                    viewModel.morningEndHour.collectLatest { hour ->
                        binding.morningEndSlider.value = hour.toFloat()
                        binding.morningEndValue.text = formatHour(hour)
                    }
                }
            }
        }
    }

    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> "12:00 AM"
            hour < 12 -> "$hour:00 AM"
            hour == 12 -> "12:00 PM"
            hour == 24 -> "12:00 AM (next day)"
            else -> "${hour - 12}:00 PM"
        }
    }

    private fun updatePermissionStatus() {
        val hasAccessibility = AppBlockerAccessibilityService.isServiceRunning
        val hasOverlay = Settings.canDrawOverlays(this)

        binding.accessibilityStatus.text = if (hasAccessibility) "Enabled" else "Disabled"
        binding.accessibilityStatus.setTextColor(
            getColor(if (hasAccessibility) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        binding.overlayStatus.text = if (hasOverlay) "Enabled" else "Disabled"
        binding.overlayStatus.setTextColor(
            getColor(if (hasOverlay) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    private fun getBlockedAppsList(blockedPackages: Set<String>): List<BlockedAppItem> {
        val allKnownApps = BlockedApps.DEFAULT_BLOCKED_PACKAGES.map { packageName ->
            BlockedAppItem(
                packageName = packageName,
                appName = BlockedApps.getAppDisplayName(packageName),
                isBlocked = blockedPackages.contains(packageName),
                isInstalled = isAppInstalled(packageName)
            )
        }

        // Add any custom blocked apps not in the default list
        val customApps = blockedPackages
            .filter { it !in BlockedApps.DEFAULT_BLOCKED_PACKAGES }
            .map { packageName ->
                BlockedAppItem(
                    packageName = packageName,
                    appName = getAppNameFromPackage(packageName),
                    isBlocked = true,
                    isInstalled = isAppInstalled(packageName)
                )
            }

        return (allKnownApps + customApps).sortedBy { it.appName }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast(".")
        }
    }

    private fun showInstalledAppsDialog() {
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { appInfo ->
                // Filter to show only user apps (not system apps)
                (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { appInfo ->
                Pair(
                    appInfo.packageName,
                    packageManager.getApplicationLabel(appInfo).toString()
                )
            }
            .sortedBy { it.second }

        val appNames = installedApps.map { it.second }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select App to Block")
            .setItems(appNames) { _, which ->
                val selectedPackage = installedApps[which].first
                viewModel.toggleBlockedApp(selectedPackage, true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class BlockedAppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isInstalled: Boolean
)
