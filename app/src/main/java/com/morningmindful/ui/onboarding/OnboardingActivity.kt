package com.morningmindful.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.morningmindful.MorningMindfulApp
import com.morningmindful.R
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.databinding.ActivityOnboardingBinding
import com.morningmindful.ui.MainActivity
import com.morningmindful.util.Analytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Onboarding flow for first-time users.
 * Value-first approach: shows why the app matters before asking for setup.
 * Uses smart defaults for duration, word count, and morning window.
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter

    // Settings values — smart defaults applied
    var blockingDuration: Int = 15
    var requiredWordCount: Int = 200
    var morningStartHour: Int = 5
    var morningEndHour: Int = 10
    var blockingMode: Int = SettingsRepository.BLOCKING_MODE_FULL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Track onboarding started
        Analytics.trackOnboardingStarted()

        setupViewPager()
        setupButtons()
    }

    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Update dots and buttons on page change
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPosition = 0

            override fun onPageSelected(position: Int) {
                // Track step completion when moving forward
                if (position > previousPosition) {
                    val completedPage = adapter.pages[previousPosition]
                    Analytics.trackOnboardingStepCompleted(previousPosition, completedPage.name)
                }
                previousPosition = position

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
            // Track which step was skipped at
            val currentStep = binding.viewPager.currentItem
            Analytics.trackOnboardingSkipped(currentStep)
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
            binding.dot5, binding.dot6, binding.dot7
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

            // Save settings with smart defaults
            settings.setBlockingDurationMinutes(blockingDuration)
            settings.setRequiredWordCount(requiredWordCount)
            settings.setMorningStartHour(morningStartHour)
            settings.setMorningEndHour(morningEndHour)
            settings.setBlockingMode(blockingMode)
            settings.setBlockingEnabled(true) // Enable blocking after setup
            settings.setOnboardingCompleted(true)

            // Track onboarding completed
            Analytics.trackOnboardingCompleted()

            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        adapter.notifyDataSetChanged()
    }
}
