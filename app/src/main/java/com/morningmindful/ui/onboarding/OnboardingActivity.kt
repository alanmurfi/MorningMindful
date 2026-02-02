package com.morningmindful.ui.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.databinding.ActivityOnboardingBinding
import com.morningmindful.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Onboarding flow for first-time users.
 * Guides through settings and permissions step by step.
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter

    // Notification permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Refresh the adapter to show updated permission status
        adapter.notifyDataSetChanged()
    }

    // Backup folder selection launcher
    private val backupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Take persistent permission so we can access this folder later
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            // Store temporarily and show password dialog
            pendingBackupUri = uri
            showBackupPasswordDialog()
        }
    }

    // Settings values to be saved
    var blockingDuration: Int = 15
    var requiredWordCount: Int = 200
    var morningStartHour: Int = 5
    var morningEndHour: Int = 10
    var blockingMode: Int = SettingsRepository.BLOCKING_MODE_FULL
    var autoBackupEnabled: Boolean = false
    var autoBackupUri: String? = null
    var autoBackupPassword: String? = null

    // Temporarily store the selected URI until password is confirmed
    private var pendingBackupUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Update dots and buttons on page change
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateButtons(position)
            }
        })

        // Initial state
        updateDots(0)
        updateButtons(0)
    }

    private fun setupButtons() {
        binding.skipButton.setOnClickListener {
            finishOnboarding()
        }

        binding.nextButton.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                finishOnboarding()
            }
        }

        binding.backButton.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem > 0) {
                binding.viewPager.currentItem = currentItem - 1
            }
        }
    }

    private fun updateDots(position: Int) {
        val dots = listOf(
            binding.dot1, binding.dot2, binding.dot3, binding.dot4,
            binding.dot5, binding.dot6, binding.dot7, binding.dot8, binding.dot9,
            binding.dot10
        )
        dots.forEachIndexed { index, dot ->
            dot.isSelected = index == position
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == adapter.itemCount - 1
        val isFirstPage = position == 0

        binding.backButton.visibility = if (isFirstPage) View.INVISIBLE else View.VISIBLE
        binding.nextButton.text = if (isLastPage) getString(R.string.get_started) else getString(R.string.next)
        binding.skipButton.visibility = if (isLastPage) View.GONE else View.VISIBLE
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            val settings = MorningMindfulApp.getInstance().settingsRepository

            // Save all settings from onboarding
            settings.setBlockingDurationMinutes(blockingDuration)
            settings.setRequiredWordCount(requiredWordCount)
            settings.setMorningStartHour(morningStartHour)
            settings.setMorningEndHour(morningEndHour)
            settings.setBlockingMode(blockingMode)
            settings.setBlockingEnabled(true) // Enable blocking after setup
            settings.setOnboardingCompleted(true)

            // Save auto-backup settings
            settings.setAutoBackupEnabled(autoBackupEnabled)
            settings.setAutoBackupUri(autoBackupUri)
            settings.setAutoBackupPassword(autoBackupPassword)

            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        adapter.notifyDataSetChanged()
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestBackupFolderSelection() {
        backupFolderLauncher.launch(null)
    }

    /**
     * Show dialog to set a memorable password for backup encryption.
     * User must remember this password to restore after reinstall.
     */
    private fun showBackupPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_backup_password, null)
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
                        // Reset - user needs to select folder again
                        pendingBackupUri = null
                    }
                    password != confirmPassword -> {
                        Toast.makeText(this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
                        // Reset - user needs to select folder again
                        pendingBackupUri = null
                    }
                    else -> {
                        // Success - save the settings
                        autoBackupUri = pendingBackupUri.toString()
                        autoBackupPassword = password
                        autoBackupEnabled = true
                        pendingBackupUri = null
                        // Refresh the adapter to show updated status
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                // User cancelled - reset pending URI
                pendingBackupUri = null
            }
            .show()
    }
}
