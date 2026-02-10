package com.morningmindful.ui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.morningmindful.BuildConfig
import com.morningmindful.R
import com.morningmindful.databinding.ActivitySettingsBinding
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.ui.onboarding.OnboardingActivity
import com.morningmindful.service.DailyReminderScheduler
import com.morningmindful.util.Analytics
import com.morningmindful.util.BlockedApps
import com.morningmindful.util.JournalBackupManager
import com.morningmindful.util.PermissionUtils
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()

    // File picker for export
    private val exportFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { handleExportToUri(it) }
    }

    // File picker for import
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleImportFromUri(it) }
    }

    // Temporary storage for export password
    private var pendingExportPassword: String? = null
    private var pendingImportUri: Uri? = null
    private var pendingBackupUri: Uri? = null
    private lateinit var blockedAppsAdapter: BlockedAppsAdapter

    // Backup folder selection for auto-backup
    private val backupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            pendingBackupUri = uri
            showAutoBackupPasswordDialog()
        } else {
            // User cancelled, turn switch back off
            binding.autoBackupSwitch.isChecked = false
        }
    }

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
    }

    private fun setupUI() {
        // Back button
        binding.backButton.setOnClickListener {
            finish()
        }

        // Redo introduction button
        binding.redoIntroButton.setOnClickListener {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
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
            binding.durationValue.text = getString(R.string.duration_minutes, value.toInt())
        }

        // Word count slider
        binding.wordCountSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setRequiredWordCount(value.toInt())
            }
            binding.wordCountValue.text = getString(R.string.word_count_setting, value.toInt())
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
            startActivity(PermissionUtils.getAccessibilitySettingsIntent())
        }

        binding.overlayPermissionButton.setOnClickListener {
            startActivity(PermissionUtils.getOverlaySettingsIntent(this))
        }

        // Usage Stats permission button
        binding.usageStatsPermissionButton.setOnClickListener {
            startActivity(PermissionUtils.getUsageStatsSettingsIntent())
        }

        // Notifications permission button
        binding.notificationsPermissionButton.setOnClickListener {
            startActivity(PermissionUtils.getNotificationSettingsIntent(this))
        }

        // Blocking mode radio group
        binding.blockingModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.modeGentleRadio -> SettingsRepository.BLOCKING_MODE_GENTLE
                else -> SettingsRepository.BLOCKING_MODE_FULL
            }
            viewModel.setBlockingMode(mode)
            updatePermissionVisibility(mode)

            // If switching to gentle mode and accessibility is still enabled, offer to disable it
            if (mode == SettingsRepository.BLOCKING_MODE_GENTLE &&
                PermissionUtils.hasAccessibilityPermission()) {
                showRevokeAccessibilityDialog()
            }
        }

        // Reset today's progress
        binding.resetTodayButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_today_title)
                .setMessage(R.string.reset_today_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    viewModel.resetTodayProgress()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Reset to defaults
        binding.resetDefaultsButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_settings_title)
                .setMessage(R.string.reset_settings_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    viewModel.resetToDefaults()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Test crash for Crashlytics - only show in debug builds
        if (BuildConfig.DEBUG) {
            binding.testCrashDivider.visibility = View.VISIBLE
            binding.testCrashButton.visibility = View.VISIBLE
            binding.testCrashButton.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.test_crash_confirm_title)
                    .setMessage(R.string.test_crash_confirm_message)
                    .setPositiveButton(R.string.crash) { _, _ ->
                        throw RuntimeException("Test Crash for Firebase Crashlytics")
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        } else {
            binding.testCrashDivider.visibility = View.GONE
            binding.testCrashButton.visibility = View.GONE
        }

        // Theme radio group
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val themeMode = when (checkedId) {
                R.id.themeLightRadio -> SettingsRepository.THEME_MODE_LIGHT
                R.id.themeDarkRadio -> SettingsRepository.THEME_MODE_DARK
                else -> SettingsRepository.THEME_MODE_SYSTEM
            }
            viewModel.setThemeMode(themeMode)
            // Apply the theme immediately (directly, not via repository which may not have saved yet)
            val nightMode = when (themeMode) {
                SettingsRepository.THEME_MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                SettingsRepository.THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }

        // Privacy Policy
        binding.privacyPolicyButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))
            startActivity(intent)
        }

        // Export journals
        binding.exportButton.setOnClickListener {
            showExportPasswordDialog()
        }

        // Import journals
        binding.importButton.setOnClickListener {
            importFileLauncher.launch(arrayOf("*/*"))
        }

        // Auto-backup switch
        binding.autoBackupSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check if we already have a backup folder configured
                val existingUri = viewModel.getAutoBackupUri()
                if (existingUri != null) {
                    // Already configured, just enable
                    viewModel.setAutoBackupEnabled(true)
                } else {
                    // Need to set up - launch folder picker
                    backupFolderLauncher.launch(null)
                }
            } else {
                // Disable auto-backup
                viewModel.setAutoBackupEnabled(false)
            }
        }

        // Change backup folder button
        binding.changeBackupFolderButton.setOnClickListener {
            backupFolderLauncher.launch(null)
        }

        // Include images in backup checkbox
        binding.includeImagesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setIncludeImagesInBackup(isChecked)
        }

        // App version
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.appVersion.text = packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            binding.appVersion.text = getString(R.string.default_version)
        }

        // Daily reminder switch
        binding.reminderEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setDailyReminderEnabled(isChecked)
            if (isChecked) {
                val hour = viewModel.getDailyReminderHourSync()
                val minute = viewModel.getDailyReminderMinuteSync()
                DailyReminderScheduler.schedule(this, hour, minute)
                Analytics.trackReminderEnabled(hour, minute)
            } else {
                DailyReminderScheduler.cancel(this)
                Analytics.trackReminderDisabled()
            }
            binding.reminderTimeContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Reminder time picker
        binding.reminderTimeContainer.setOnClickListener {
            showReminderTimePicker()
        }
    }

    private fun showReminderTimePicker() {
        val currentHour = viewModel.getDailyReminderHourSync()
        val currentMinute = viewModel.getDailyReminderMinuteSync()

        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(currentHour)
            .setMinute(currentMinute)
            .setTitleText(getString(R.string.reminder_time))
            .build()

        picker.addOnPositiveButtonClickListener {
            val hour = picker.hour
            val minute = picker.minute
            viewModel.setDailyReminderTime(hour, minute)
            updateReminderTimeDisplay(hour, minute)

            // Reschedule with new time
            if (viewModel.isDailyReminderEnabledSync()) {
                DailyReminderScheduler.schedule(this, hour, minute)
            }
        }

        picker.show(supportFragmentManager, "reminder_time_picker")
    }

    private fun updateReminderTimeDisplay(hour: Int, minute: Int) {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        binding.reminderTimeValue.text = String.format("%d:%02d %s", displayHour, minute, amPm)
    }

    private fun showExportPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordInput)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.export_set_password)
            .setMessage(R.string.export_password_message)
            .setView(dialogView)
            .setPositiveButton(R.string.export_button) { _, _ ->
                val password = passwordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                when {
                    password.length < 8 -> {
                        Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show()
                    }
                    password != confirmPassword -> {
                        Toast.makeText(this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        pendingExportPassword = password
                        exportFileLauncher.launch(JournalBackupManager.generateBackupFilename())
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleExportToUri(uri: Uri) {
        val password = pendingExportPassword ?: return
        pendingExportPassword = null

        lifecycleScope.launch {
            val entries = viewModel.getAllEntriesForExport()

            val result = JournalBackupManager.exportEntries(
                context = this@SettingsActivity,
                entries = entries,
                outputUri = uri,
                password = password
            )

            when (result) {
                is JournalBackupManager.ExportResult.Success -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.export_success, result.entryCount),
                        Toast.LENGTH_LONG
                    ).show()
                }
                is JournalBackupManager.ExportResult.Error -> {
                    Toast.makeText(
                        this@SettingsActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun handleImportFromUri(uri: Uri) {
        pendingImportUri = uri
        showImportPasswordDialog()
    }

    private fun showImportPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_password_single, null)
        val passwordInput = dialogView.findViewById<TextInputEditText>(R.id.passwordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.import_enter_password)
            .setMessage(R.string.import_password_message)
            .setView(dialogView)
            .setPositiveButton(R.string.import_button) { _, _ ->
                val password = passwordInput.text.toString()
                val uri = pendingImportUri ?: return@setPositiveButton
                pendingImportUri = null

                performImport(uri, password)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun performImport(uri: Uri, password: String) {
        lifecycleScope.launch {
            val existingDates = viewModel.getAllEntryDates()

            val (entries, result) = JournalBackupManager.importEntries(
                context = this@SettingsActivity,
                inputUri = uri,
                password = password,
                existingDates = existingDates
            )

            when (result) {
                is JournalBackupManager.ImportResult.Success -> {
                    if (entries.isNotEmpty()) {
                        viewModel.importEntries(entries)
                    }

                    val message = if (result.skippedCount > 0) {
                        getString(R.string.import_success_with_skipped, result.importedCount, result.skippedCount)
                    } else {
                        getString(R.string.import_success, result.importedCount)
                    }
                    Toast.makeText(this@SettingsActivity, message, Toast.LENGTH_LONG).show()
                }
                is JournalBackupManager.ImportResult.Error -> {
                    Toast.makeText(this@SettingsActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val PRIVACY_POLICY_URL = "https://alanmurfi.github.io/MorningMindful/"
    }

    /**
     * Show dialog to set password for auto-backup encryption.
     */
    private fun showAutoBackupPasswordDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_backup_password, null)
        val passwordInput = dialogView.findViewById<EditText>(R.id.passwordInput)
        val confirmPasswordInput = dialogView.findViewById<EditText>(R.id.confirmPasswordInput)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.auto_backup_password_title)
            .setMessage(R.string.auto_backup_password_message)
            .setView(dialogView)
            .setPositiveButton(R.string.ok) { _, _ ->
                val password = passwordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                when {
                    password.length < 8 -> {
                        Toast.makeText(this, R.string.password_too_short, Toast.LENGTH_SHORT).show()
                        binding.autoBackupSwitch.isChecked = false
                        pendingBackupUri = null
                    }
                    password != confirmPassword -> {
                        Toast.makeText(this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
                        binding.autoBackupSwitch.isChecked = false
                        pendingBackupUri = null
                    }
                    else -> {
                        // Save auto-backup settings
                        val uri = pendingBackupUri
                        if (uri != null) {
                            viewModel.setAutoBackupUri(uri.toString())
                            viewModel.setAutoBackupPassword(password)
                            viewModel.setAutoBackupEnabled(true)
                            Toast.makeText(this, R.string.auto_backup_enabled, Toast.LENGTH_SHORT).show()
                        }
                        pendingBackupUri = null
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                binding.autoBackupSwitch.isChecked = false
                pendingBackupUri = null
            }
            .setOnCancelListener {
                binding.autoBackupSwitch.isChecked = false
                pendingBackupUri = null
            }
            .show()
    }

    /**
     * Update the auto-backup status text and visibility.
     */
    private fun updateAutoBackupUI(enabled: Boolean, lastBackupTime: Long) {
        binding.autoBackupSwitch.isChecked = enabled
        binding.changeBackupFolderButton.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.includeImagesRow.visibility = if (enabled) View.VISIBLE else View.GONE

        // Update include images checkbox state
        if (enabled) {
            binding.includeImagesCheckbox.isChecked = viewModel.isIncludeImagesInBackup()
        }

        if (enabled && lastBackupTime > 0) {
            val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            val formattedDate = dateFormat.format(Date(lastBackupTime))
            binding.autoBackupStatus.text = getString(R.string.last_backup, formattedDate)
        } else if (enabled) {
            binding.autoBackupStatus.text = getString(R.string.auto_backup_enabled)
        } else {
            binding.autoBackupStatus.text = getString(R.string.auto_backup_disabled)
        }
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
                        // Clamp value to slider range (5-60)
                        val clampedMinutes = minutes.coerceIn(5, 60)
                        binding.durationSlider.value = clampedMinutes.toFloat()
                        binding.durationValue.text = getString(R.string.duration_minutes, clampedMinutes)
                    }
                }

                // Required word count
                launch {
                    viewModel.requiredWordCount.collectLatest { count ->
                        binding.wordCountSlider.value = count.toFloat()
                        binding.wordCountValue.text = getString(R.string.word_count_setting, count)
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

                // Theme mode
                launch {
                    viewModel.themeMode.collectLatest { mode ->
                        val radioId = when (mode) {
                            SettingsRepository.THEME_MODE_LIGHT -> R.id.themeLightRadio
                            SettingsRepository.THEME_MODE_DARK -> R.id.themeDarkRadio
                            else -> R.id.themeSystemRadio
                        }
                        binding.themeRadioGroup.check(radioId)
                    }
                }

                // Blocking mode
                launch {
                    viewModel.blockingMode.collectLatest { mode ->
                        val radioId = when (mode) {
                            SettingsRepository.BLOCKING_MODE_GENTLE -> R.id.modeGentleRadio
                            else -> R.id.modeFullRadio
                        }
                        binding.blockingModeRadioGroup.check(radioId)
                        updatePermissionVisibility(mode)
                    }
                }

                // Auto-backup enabled
                launch {
                    viewModel.autoBackupEnabled.collectLatest { enabled ->
                        val lastBackup = viewModel.getLastBackupTime()
                        updateAutoBackupUI(enabled, lastBackup)
                    }
                }

                // Last backup time
                launch {
                    viewModel.lastBackupTime.collectLatest { time ->
                        val enabled = viewModel.isAutoBackupEnabled()
                        updateAutoBackupUI(enabled, time)
                    }
                }

                // Daily reminder enabled
                launch {
                    viewModel.dailyReminderEnabled.collectLatest { enabled ->
                        binding.reminderEnabledSwitch.isChecked = enabled
                        binding.reminderTimeContainer.visibility = if (enabled) View.VISIBLE else View.GONE
                    }
                }

                // Daily reminder time
                launch {
                    viewModel.dailyReminderHour.collectLatest { hour ->
                        val minute = viewModel.getDailyReminderMinuteSync()
                        updateReminderTimeDisplay(hour, minute)
                    }
                }

                launch {
                    viewModel.dailyReminderMinute.collectLatest { minute ->
                        val hour = viewModel.getDailyReminderHourSync()
                        updateReminderTimeDisplay(hour, minute)
                    }
                }
            }
        }
    }

    private fun formatHour(hour: Int): String {
        return when {
            hour == 0 -> getString(R.string.time_12am)
            hour < 12 -> getString(R.string.time_am, hour)
            hour == 12 -> getString(R.string.time_12pm)
            hour == 24 -> getString(R.string.time_12am_next_day)
            else -> getString(R.string.time_pm, hour - 12)
        }
    }

    private fun updatePermissionStatus() {
        val hasAccessibility = PermissionUtils.hasAccessibilityPermission()
        val hasOverlay = PermissionUtils.hasOverlayPermission(this)
        val hasUsageStats = PermissionUtils.hasUsageStatsPermission(this)
        val hasNotifications = PermissionUtils.hasNotificationPermission(this)

        binding.accessibilityStatus.text = if (hasAccessibility) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        binding.accessibilityStatus.setTextColor(
            getColor(if (hasAccessibility) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        binding.overlayStatus.text = if (hasOverlay) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        binding.overlayStatus.setTextColor(
            getColor(if (hasOverlay) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        binding.usageStatsStatus.text = if (hasUsageStats) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        binding.usageStatsStatus.setTextColor(
            getColor(if (hasUsageStats) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )

        binding.notificationsStatus.text = if (hasNotifications) getString(R.string.status_enabled) else getString(R.string.status_disabled)
        binding.notificationsStatus.setTextColor(
            getColor(if (hasNotifications) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
    }

    private fun updatePermissionVisibility(mode: Int) {
        val isGentleMode = mode == SettingsRepository.BLOCKING_MODE_GENTLE

        // Dim permissions not needed for current mode (but still show them)
        val activeAlpha = 1.0f
        val dimmedAlpha = 0.4f

        binding.accessibilityPermissionButton.alpha = if (isGentleMode) dimmedAlpha else activeAlpha
        binding.usageStatsPermissionButton.alpha = if (isGentleMode) activeAlpha else dimmedAlpha
        // Overlay is always needed
        binding.overlayPermissionButton.alpha = activeAlpha
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

    private fun showRevokeAccessibilityDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.revoke_accessibility_title)
            .setMessage(R.string.revoke_accessibility_message)
            .setPositiveButton(R.string.open_settings) { _, _ ->
                startActivity(PermissionUtils.getAccessibilitySettingsIntent())
            }
            .setNegativeButton(R.string.no_thanks, null)
            .show()
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
            .setTitle(R.string.select_app_to_block)
            .setItems(appNames) { _, which ->
                val selectedPackage = installedApps[which].first
                viewModel.toggleBlockedApp(selectedPackage, true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}

data class BlockedAppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isInstalled: Boolean
)
