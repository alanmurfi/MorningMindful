package com.morningmindful.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
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
 * Two phases:
 *   1. Narrative — emotional, animated text on purple background
 *   2. Setup — interactive permission/config pages on white background
 */
@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private lateinit var adapter: OnboardingPagerAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var splashAutoAdvanceRunnable: Runnable? = null

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

        // Handle system bar insets — apply padding to buttons container only
        ViewCompat.setOnApplyWindowInsetsListener(binding.onboardingRoot) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.buttonsContainer.updatePadding(
                bottom = systemBars.bottom + resources.getDimensionPixelSize(R.dimen.dot_margin)
            )
            insets
        }

        Analytics.trackOnboardingStarted()

        setupViewPager()
        setupButtons()
        setupDots()

        // Start on splash — apply narrative phase UI
        updatePhaseUI(0)

    }

    private fun setupViewPager() {
        adapter = OnboardingPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Disable swipe initially (narrative phase uses tap)
        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var previousPosition = 0

            override fun onPageSelected(position: Int) {
                // Track step completion when moving forward
                if (position > previousPosition) {
                    val completedPage = adapter.pages[previousPosition]
                    Analytics.trackOnboardingStepCompleted(previousPosition, completedPage.name)
                }
                previousPosition = position

                updatePhaseUI(position)

                // Update visible page and trigger animation
                adapter.currentVisiblePage = position
                val holder = (binding.viewPager.getChildAt(0) as? RecyclerView)
                    ?.findViewHolderForAdapterPosition(position)
                if (holder != null) {
                    adapter.playAnimationForPage(position, holder)
                }

                // Splash auto-advance
                val page = adapter.pages[position]
                if (page == OnboardingPage.SPLASH && page.autoAdvanceMs > 0) {
                    splashAutoAdvanceRunnable = Runnable { advanceToNextPage() }
                    handler.postDelayed(splashAutoAdvanceRunnable!!, page.autoAdvanceMs)
                }
            }
        })
    }

    private fun setupButtons() {
        binding.skipButton.setOnClickListener {
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
            val firstSetup = adapter.firstSetupPageIndex
            // Don't go back into narrative from setup
            if (currentItem > firstSetup) {
                binding.viewPager.currentItem = currentItem - 1
            }
        }
    }

    /** Create dot indicators dynamically for setup pages only */
    private fun setupDots() {
        val dotCount = adapter.setupPageCount
        binding.dotsContainer.removeAllViews()

        for (i in 0 until dotCount) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.dot_size),
                    resources.getDimensionPixelSize(R.dimen.dot_size)
                ).apply {
                    setMargins(
                        resources.getDimensionPixelSize(R.dimen.dot_margin),
                        0,
                        resources.getDimensionPixelSize(R.dimen.dot_margin),
                        0
                    )
                }
                setBackgroundResource(R.drawable.dot_indicator)
            }
            binding.dotsContainer.addView(dot)
        }
    }

    /** Update dots to reflect current setup page position */
    private fun updateDots(position: Int) {
        val firstSetup = adapter.firstSetupPageIndex
        val setupIndex = position - firstSetup

        for (i in 0 until binding.dotsContainer.childCount) {
            binding.dotsContainer.getChildAt(i).isSelected = i == setupIndex
        }
    }

    /** Show/hide UI chrome based on whether we're in narrative or setup phase */
    private fun updatePhaseUI(position: Int) {
        val page = adapter.pages[position]
        val isNarrative = page.pageType == PageType.NARRATIVE

        if (isNarrative) {
            // Narrative phase: hide all chrome, purple background edge-to-edge
            binding.dotsContainer.visibility = View.GONE
            binding.buttonsContainer.visibility = View.GONE
            binding.viewPager.isUserInputEnabled = false
            binding.onboardingRoot.setBackgroundColor(getColor(R.color.primary))

            // Transparent status bar, light icons
            window.statusBarColor = getColor(R.color.primary)
            window.navigationBarColor = getColor(R.color.primary)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        } else {
            // Setup phase: show chrome, white background
            binding.dotsContainer.visibility = View.VISIBLE
            binding.buttonsContainer.visibility = View.VISIBLE
            binding.viewPager.isUserInputEnabled = true
            binding.onboardingRoot.setBackgroundColor(getColor(R.color.background))

            // White status bar with dark icons
            window.statusBarColor = getColor(R.color.background)
            window.navigationBarColor = getColor(R.color.background)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }

            updateDots(position)
            updateButtons(position)
        }
    }

    private fun updateButtons(position: Int) {
        val isLastPage = position == adapter.itemCount - 1
        val firstSetup = adapter.firstSetupPageIndex
        val isFirstSetupPage = position == firstSetup

        binding.backButton.visibility = if (isFirstSetupPage) View.INVISIBLE else View.VISIBLE
        binding.nextButton.text = if (isLastPage) getString(R.string.get_started) else getString(R.string.next)
        binding.skipButton.visibility = if (isLastPage) View.GONE else View.VISIBLE
    }

    /** Called from NarrativeViewHolder tap and continue button */
    fun advanceToNextPage() {
        val current = binding.viewPager.currentItem
        if (current < adapter.itemCount - 1) {
            binding.viewPager.setCurrentItem(current + 1, true)
        }
    }

    private fun finishOnboarding() {
        lifecycleScope.launch {
            val settings = MorningMindfulApp.getInstance().settingsRepository

            settings.setBlockingDurationMinutes(blockingDuration)
            settings.setRequiredWordCount(requiredWordCount)
            settings.setMorningStartHour(morningStartHour)
            settings.setMorningEndHour(morningEndHour)
            settings.setBlockingMode(blockingMode)
            settings.setBlockingEnabled(true)
            settings.setOnboardingCompleted(true)

            Analytics.trackOnboardingCompleted()

            startActivity(Intent(this@OnboardingActivity, MainActivity::class.java))
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh permission status when returning from settings
        val currentPosition = binding.viewPager.currentItem
        val page = adapter.pages[currentPosition]
        if (page.pageType == PageType.SETUP) {
            adapter.notifyDataSetChanged()
            setupDots()
            updatePhaseUI(currentPosition)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        splashAutoAdvanceRunnable?.let { handler.removeCallbacks(it) }
    }
}
