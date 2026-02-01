package com.morningmindful.ui.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
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

    // Settings values to be saved
    var blockingDuration: Int = 15
    var requiredWordCount: Int = 200
    var morningStartHour: Int = 5
    var morningEndHour: Int = 10
    var blockingMode: Int = SettingsRepository.BLOCKING_MODE_FULL

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
            binding.dot5, binding.dot6, binding.dot7, binding.dot8, binding.dot9
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
}
