package com.morningmindful.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.morningmindful.R
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.util.PermissionUtils

/**
 * Adapter for onboarding ViewPager2.
 * Two phases: Narrative (animated text, emotional) → Setup (interactive, practical)
 */
class OnboardingPagerAdapter(
    private val activity: OnboardingActivity
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_NARRATIVE = 0
        const val VIEW_TYPE_SETUP = 1
    }

    private val animatedPages = mutableSetOf<Int>()
    var currentVisiblePage: Int = 0

    val pages: List<OnboardingPage>
        get() {
            val isGentleMode = activity.blockingMode == SettingsRepository.BLOCKING_MODE_GENTLE
            return if (isGentleMode) {
                listOf(
                    OnboardingPage.SPLASH,
                    OnboardingPage.HOOK,
                    OnboardingPage.PAIN,
                    OnboardingPage.EMPATHY,
                    OnboardingPage.TURN,
                    OnboardingPage.SOLUTION,
                    OnboardingPage.BLOCKING_MODE,
                    OnboardingPage.PERMISSION_USAGE_STATS,
                    OnboardingPage.PERMISSION_OVERLAY,
                    OnboardingPage.READY
                )
            } else {
                listOf(
                    OnboardingPage.SPLASH,
                    OnboardingPage.HOOK,
                    OnboardingPage.PAIN,
                    OnboardingPage.EMPATHY,
                    OnboardingPage.TURN,
                    OnboardingPage.SOLUTION,
                    OnboardingPage.BLOCKING_MODE,
                    OnboardingPage.PERMISSION_ACCESSIBILITY,
                    OnboardingPage.PERMISSION_OVERLAY,
                    OnboardingPage.READY
                )
            }
        }

    /** Index of the first setup page */
    val firstSetupPageIndex: Int
        get() = pages.indexOfFirst { it.pageType == PageType.SETUP }

    /** Number of setup pages (for dot indicators) */
    val setupPageCount: Int
        get() = pages.count { it.pageType == PageType.SETUP }

    override fun getItemViewType(position: Int): Int {
        return if (pages[position].pageType == PageType.NARRATIVE) VIEW_TYPE_NARRATIVE else VIEW_TYPE_SETUP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_NARRATIVE) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_narrative, parent, false)
            NarrativeViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_page, parent, false)
            SetupViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val page = pages[position]
        when (holder) {
            is NarrativeViewHolder -> holder.bind(page)
            is SetupViewHolder -> holder.bind(page)
        }
    }

    override fun getItemCount() = pages.size

    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        super.onViewAttachedToWindow(holder)
        @Suppress("DEPRECATION")
        val position = holder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        if (position != currentVisiblePage) return
        playAnimationForPage(position, holder)
    }

    /**
     * Trigger animations for a page when it becomes visible.
     */
    fun playAnimationForPage(position: Int, holder: RecyclerView.ViewHolder?) {
        if (position in animatedPages) return
        animatedPages.add(position)

        val page = pages[position]
        if (holder is NarrativeViewHolder) {
            holder.playAnimation(page)
        }
    }

    // ─── Narrative ViewHolder ───────────────────────────────────────────

    inner class NarrativeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val line1: TextView = itemView.findViewById(R.id.narrativeLine1)
        private val line2: TextView = itemView.findViewById(R.id.narrativeLine2)
        private val line3: TextView = itemView.findViewById(R.id.narrativeLine3)
        private val line4: TextView = itemView.findViewById(R.id.narrativeLine4)
        private val line5: TextView = itemView.findViewById(R.id.narrativeLine5)
        private val tapToContinue: TextView = itemView.findViewById(R.id.tapToContinue)
        private val continueButton: MaterialButton = itemView.findViewById(R.id.narrativeContinueButton)
        private val lines = listOf(line1, line2, line3, line4, line5)

        fun bind(page: OnboardingPage) {
            // Reset all lines
            lines.forEach { it.alpha = 0f; it.text = "" }
            tapToContinue.alpha = 0f
            continueButton.alpha = 0f
            continueButton.visibility = View.GONE

            // Set text for each line from string resources
            val ctx = itemView.context
            page.lineResIds.forEachIndexed { index, resId ->
                if (index < lines.size) {
                    lines[index].text = ctx.getString(resId)
                }
            }

            // Splash page: center text, larger font
            if (page == OnboardingPage.SPLASH) {
                val container = (line1.parent as? android.widget.LinearLayout)
                container?.gravity = android.view.Gravity.CENTER
                line1.textSize = 40f
                line1.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }

            // Solution page: show continue button instead of tap-to-continue
            if (page == OnboardingPage.SOLUTION) {
                continueButton.visibility = View.VISIBLE
                continueButton.setOnClickListener {
                    activity.advanceToNextPage()
                }
            }

            // Tap anywhere to advance (except on solution page where button is used)
            if (page != OnboardingPage.SOLUTION) {
                itemView.setOnClickListener {
                    activity.advanceToNextPage()
                }
            }
        }

        fun playAnimation(page: OnboardingPage) {
            val activeLines = lines.filter { it.text.isNotEmpty() }

            if (page == OnboardingPage.SPLASH) {
                // Splash: just fade in the single word
                OnboardingAnimationUtils.fadeInView(line1, delayMs = 200, durationMs = 600)
            } else {
                // Staggered line animation
                val stagger = if (page == OnboardingPage.TURN) 800L else 600L
                OnboardingAnimationUtils.animateTextLines(activeLines, staggerDelayMs = stagger)

                // Show tap-to-continue or continue button after all lines animate
                val totalAnimDuration = (activeLines.size * stagger) + 400L
                if (page == OnboardingPage.SOLUTION) {
                    OnboardingAnimationUtils.fadeInView(continueButton, delayMs = totalAnimDuration)
                } else {
                    OnboardingAnimationUtils.fadeInView(tapToContinue, delayMs = totalAnimDuration)
                }
            }
        }
    }

    // ─── Setup ViewHolder ───────────────────────────────────────────────

    inner class SetupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.pageIcon)
        private val title: TextView = itemView.findViewById(R.id.pageTitle)
        private val description: TextView = itemView.findViewById(R.id.pageDescription)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.pageActionButton)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val slider: Slider = itemView.findViewById(R.id.pageSlider)
        private val sliderValue: TextView = itemView.findViewById(R.id.sliderValue)
        private val startTimeContainer: View = itemView.findViewById(R.id.startTimeContainer)
        private val endTimeContainer: View = itemView.findViewById(R.id.endTimeContainer)
        private val fullBlockCard: CardView = itemView.findViewById(R.id.fullBlockCard)
        private val gentleReminderCard: CardView = itemView.findViewById(R.id.gentleReminderCard)
        private val fullBlockIcon: ImageView = itemView.findViewById(R.id.fullBlockIcon)
        private val gentleReminderIcon: ImageView = itemView.findViewById(R.id.gentleReminderIcon)
        private val secondaryButton: MaterialButton = itemView.findViewById(R.id.pageSecondaryButton)
        private val stepsContainer: LinearLayout = itemView.findViewById(R.id.stepsContainer)

        fun bind(page: OnboardingPage) {
            icon.setImageResource(page.iconRes)
            title.text = itemView.context.getString(page.titleRes)
            description.text = itemView.context.getString(page.descriptionRes)

            // Hide all optional elements by default
            actionButton.visibility = View.GONE
            secondaryButton.visibility = View.GONE
            statusText.visibility = View.GONE
            slider.visibility = View.GONE
            sliderValue.visibility = View.GONE
            startTimeContainer.visibility = View.GONE
            endTimeContainer.visibility = View.GONE
            fullBlockCard.visibility = View.GONE
            gentleReminderCard.visibility = View.GONE
            stepsContainer.visibility = View.GONE

            when (page) {
                OnboardingPage.BLOCKING_MODE -> {
                    fullBlockCard.visibility = View.VISIBLE
                    gentleReminderCard.visibility = View.VISIBLE
                    updateBlockingModeSelection()

                    fullBlockCard.setOnClickListener {
                        activity.blockingMode = SettingsRepository.BLOCKING_MODE_FULL
                        updateBlockingModeSelection()
                    }
                    gentleReminderCard.setOnClickListener {
                        activity.blockingMode = SettingsRepository.BLOCKING_MODE_GENTLE
                        updateBlockingModeSelection()
                    }
                }
                OnboardingPage.PERMISSION_ACCESSIBILITY -> {
                    actionButton.visibility = View.VISIBLE
                    statusText.visibility = View.VISIBLE

                    val hasPermission = PermissionUtils.hasAccessibilityPermission()
                    if (hasPermission) {
                        statusText.text = itemView.context.getString(R.string.permission_granted)
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = itemView.context.getString(R.string.enabled)
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = itemView.context.getString(R.string.tap_to_open_settings)
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = itemView.context.getString(R.string.open_settings)
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener {
                            PermissionUtils.openAccessibilitySettings(activity)
                        }
                    }
                }
                OnboardingPage.PERMISSION_USAGE_STATS -> {
                    actionButton.visibility = View.VISIBLE
                    statusText.visibility = View.VISIBLE

                    val hasPermission = PermissionUtils.hasUsageStatsPermission(activity)
                    if (hasPermission) {
                        statusText.text = itemView.context.getString(R.string.permission_granted)
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = itemView.context.getString(R.string.enabled)
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = itemView.context.getString(R.string.tap_to_open_settings)
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = itemView.context.getString(R.string.open_settings)
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener {
                            PermissionUtils.openUsageStatsSettings(activity)
                        }
                    }
                }
                OnboardingPage.PERMISSION_OVERLAY -> {
                    actionButton.visibility = View.VISIBLE
                    statusText.visibility = View.VISIBLE

                    val hasPermission = PermissionUtils.hasOverlayPermission(activity)
                    if (hasPermission) {
                        statusText.text = itemView.context.getString(R.string.permission_granted)
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = itemView.context.getString(R.string.enabled)
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = itemView.context.getString(R.string.tap_to_open_settings)
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = itemView.context.getString(R.string.open_settings)
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener {
                            PermissionUtils.openOverlaySettings(activity)
                        }
                    }
                }
                else -> {
                    // Ready page - no extra controls needed
                }
            }
        }

        private fun updateBlockingModeSelection() {
            val isFullMode = activity.blockingMode == SettingsRepository.BLOCKING_MODE_FULL

            fullBlockCard.setCardBackgroundColor(
                itemView.context.getColor(if (isFullMode) R.color.primary_very_light else R.color.surface)
            )
            gentleReminderCard.setCardBackgroundColor(
                itemView.context.getColor(if (!isFullMode) R.color.primary_very_light else R.color.surface)
            )

            fullBlockIcon.visibility = if (isFullMode) View.VISIBLE else View.GONE
            gentleReminderIcon.visibility = if (!isFullMode) View.VISIBLE else View.GONE
        }
    }
}

enum class PageType { NARRATIVE, SETUP }

enum class OnboardingPage(
    val pageType: PageType,
    // For narrative pages
    val lineResIds: List<Int> = emptyList(),
    val autoAdvanceMs: Long = 0,
    // For setup pages
    val iconRes: Int = 0,
    val titleRes: Int = 0,
    val descriptionRes: Int = 0
) {
    // ─── Narrative Pages ────────────────────────────────────────────
    SPLASH(
        PageType.NARRATIVE,
        lineResIds = listOf(R.string.onboarding_splash),
        autoAdvanceMs = 1500
    ),
    HOOK(
        PageType.NARRATIVE,
        lineResIds = listOf(
            R.string.onboarding_hook_1,
            R.string.onboarding_hook_2,
            R.string.onboarding_hook_3,
            R.string.onboarding_hook_4
        )
    ),
    PAIN(
        PageType.NARRATIVE,
        lineResIds = listOf(
            R.string.onboarding_pain_1,
            R.string.onboarding_pain_2,
            R.string.onboarding_pain_3,
            R.string.onboarding_pain_4
        )
    ),
    EMPATHY(
        PageType.NARRATIVE,
        lineResIds = listOf(
            R.string.onboarding_empathy_1,
            R.string.onboarding_empathy_2,
            R.string.onboarding_empathy_3
        )
    ),
    TURN(
        PageType.NARRATIVE,
        lineResIds = listOf(
            R.string.onboarding_turn_1,
            R.string.onboarding_turn_2,
            R.string.onboarding_turn_3
        )
    ),
    SOLUTION(
        PageType.NARRATIVE,
        lineResIds = listOf(
            R.string.onboarding_solution_1,
            R.string.onboarding_solution_2,
            R.string.onboarding_solution_3,
            R.string.onboarding_solution_4
        )
    ),

    // ─── Setup Pages ────────────────────────────────────────────────
    BLOCKING_MODE(
        PageType.SETUP,
        iconRes = R.drawable.ic_settings,
        titleRes = R.string.onboarding_mode_title,
        descriptionRes = R.string.onboarding_mode_desc
    ),
    PERMISSION_ACCESSIBILITY(
        PageType.SETUP,
        iconRes = R.drawable.ic_settings,
        titleRes = R.string.onboarding_perm_accessibility_title,
        descriptionRes = R.string.onboarding_perm_accessibility_desc
    ),
    PERMISSION_USAGE_STATS(
        PageType.SETUP,
        iconRes = R.drawable.ic_settings,
        titleRes = R.string.onboarding_perm_usage_stats_title,
        descriptionRes = R.string.onboarding_perm_usage_stats_desc
    ),
    PERMISSION_OVERLAY(
        PageType.SETUP,
        iconRes = R.drawable.ic_open_external,
        titleRes = R.string.onboarding_perm_overlay_title,
        descriptionRes = R.string.onboarding_perm_overlay_desc
    ),
    READY(
        PageType.SETUP,
        iconRes = R.drawable.ic_check_circle,
        titleRes = R.string.onboarding_ready_new_title,
        descriptionRes = R.string.onboarding_ready_new_desc
    )
}
