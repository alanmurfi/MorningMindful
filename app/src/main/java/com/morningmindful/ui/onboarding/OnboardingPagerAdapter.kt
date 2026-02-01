package com.morningmindful.ui.onboarding

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
 * Guides user through setup step by step.
 */
class OnboardingPagerAdapter(
    private val activity: OnboardingActivity
) : RecyclerView.Adapter<OnboardingPagerAdapter.OnboardingViewHolder>() {

    // Pages change based on blocking mode selection
    val pages: List<OnboardingPage>
        get() {
            val isGentleMode = activity.blockingMode == SettingsRepository.BLOCKING_MODE_GENTLE
            return if (isGentleMode) {
                listOf(
                    OnboardingPage.WELCOME,
                    OnboardingPage.BLOCKING_MODE,
                    OnboardingPage.BLOCKING_DURATION,
                    OnboardingPage.WORD_COUNT,
                    OnboardingPage.MORNING_WINDOW,
                    OnboardingPage.PERMISSION_USAGE_STATS,
                    OnboardingPage.PERMISSION_OVERLAY,
                    OnboardingPage.READY
                )
            } else {
                listOf(
                    OnboardingPage.WELCOME,
                    OnboardingPage.BLOCKING_MODE,
                    OnboardingPage.BLOCKING_DURATION,
                    OnboardingPage.WORD_COUNT,
                    OnboardingPage.MORNING_WINDOW,
                    OnboardingPage.PERMISSION_ACCESSIBILITY,
                    OnboardingPage.PERMISSION_OVERLAY,
                    OnboardingPage.READY
                )
            }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(pages[position])
    }

    override fun getItemCount() = pages.size

    inner class OnboardingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.pageIcon)
        private val title: TextView = itemView.findViewById(R.id.pageTitle)
        private val description: TextView = itemView.findViewById(R.id.pageDescription)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.pageActionButton)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)
        private val slider: Slider = itemView.findViewById(R.id.pageSlider)
        private val sliderValue: TextView = itemView.findViewById(R.id.sliderValue)
        private val startTimeContainer: View = itemView.findViewById(R.id.startTimeContainer)
        private val endTimeContainer: View = itemView.findViewById(R.id.endTimeContainer)
        private val startTimeValue: TextView = itemView.findViewById(R.id.startTimeValue)
        private val endTimeValue: TextView = itemView.findViewById(R.id.endTimeValue)
        private val startTimeMinus: View = itemView.findViewById(R.id.startTimeMinus)
        private val startTimePlus: View = itemView.findViewById(R.id.startTimePlus)
        private val endTimeMinus: View = itemView.findViewById(R.id.endTimeMinus)
        private val endTimePlus: View = itemView.findViewById(R.id.endTimePlus)
        private val fullBlockCard: CardView = itemView.findViewById(R.id.fullBlockCard)
        private val gentleReminderCard: CardView = itemView.findViewById(R.id.gentleReminderCard)
        private val fullBlockIcon: ImageView = itemView.findViewById(R.id.fullBlockIcon)
        private val gentleReminderIcon: ImageView = itemView.findViewById(R.id.gentleReminderIcon)

        fun bind(page: OnboardingPage) {
            icon.setImageResource(page.iconRes)
            title.text = itemView.context.getString(page.titleRes)
            description.text = itemView.context.getString(page.descriptionRes)

            // Hide all optional elements by default
            actionButton.visibility = View.GONE
            statusText.visibility = View.GONE
            slider.visibility = View.GONE
            sliderValue.visibility = View.GONE
            startTimeContainer.visibility = View.GONE
            endTimeContainer.visibility = View.GONE
            fullBlockCard.visibility = View.GONE
            gentleReminderCard.visibility = View.GONE

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
                OnboardingPage.BLOCKING_DURATION -> {
                    slider.visibility = View.VISIBLE
                    sliderValue.visibility = View.VISIBLE
                    slider.valueFrom = 5f
                    slider.valueTo = 60f
                    slider.stepSize = 5f
                    slider.value = activity.blockingDuration.toFloat()
                    sliderValue.text = "${activity.blockingDuration} minutes"
                    slider.addOnChangeListener { _, value, _ ->
                        activity.blockingDuration = value.toInt()
                        sliderValue.text = "${value.toInt()} minutes"
                    }
                }
                OnboardingPage.WORD_COUNT -> {
                    slider.visibility = View.VISIBLE
                    sliderValue.visibility = View.VISIBLE
                    slider.valueFrom = 50f
                    slider.valueTo = 500f
                    slider.stepSize = 50f
                    slider.value = activity.requiredWordCount.toFloat()
                    sliderValue.text = "${activity.requiredWordCount} words"
                    slider.addOnChangeListener { _, value, _ ->
                        activity.requiredWordCount = value.toInt()
                        sliderValue.text = "${value.toInt()} words"
                    }
                }
                OnboardingPage.MORNING_WINDOW -> {
                    startTimeContainer.visibility = View.VISIBLE
                    endTimeContainer.visibility = View.VISIBLE
                    updateTimeDisplays()

                    startTimeMinus.setOnClickListener {
                        if (activity.morningStartHour > 0) {
                            activity.morningStartHour--
                            updateTimeDisplays()
                        }
                    }
                    startTimePlus.setOnClickListener {
                        if (activity.morningStartHour < activity.morningEndHour - 1) {
                            activity.morningStartHour++
                            updateTimeDisplays()
                        }
                    }
                    endTimeMinus.setOnClickListener {
                        if (activity.morningEndHour > activity.morningStartHour + 1) {
                            activity.morningEndHour--
                            updateTimeDisplays()
                        }
                    }
                    endTimePlus.setOnClickListener {
                        if (activity.morningEndHour < 24) {
                            activity.morningEndHour++
                            updateTimeDisplays()
                        }
                    }
                }
                OnboardingPage.PERMISSION_ACCESSIBILITY -> {
                    actionButton.visibility = View.VISIBLE
                    statusText.visibility = View.VISIBLE

                    val hasPermission = PermissionUtils.hasAccessibilityPermission()
                    if (hasPermission) {
                        statusText.text = "✓ Permission granted"
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = "Enabled"
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = "Tap to open settings"
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = "Open Settings"
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
                        statusText.text = "✓ Permission granted"
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = "Enabled"
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = "Tap to open settings"
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = "Open Settings"
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
                        statusText.text = "✓ Permission granted"
                        statusText.setTextColor(itemView.context.getColor(R.color.success))
                        actionButton.text = "Enabled"
                        actionButton.isEnabled = false
                    } else {
                        statusText.text = "Tap to open settings"
                        statusText.setTextColor(itemView.context.getColor(R.color.text_secondary))
                        actionButton.text = "Open Settings"
                        actionButton.isEnabled = true
                        actionButton.setOnClickListener {
                            PermissionUtils.openOverlaySettings(activity)
                        }
                    }
                }
                else -> {
                    // Welcome and Ready pages - no extra controls
                }
            }
        }

        private fun updateTimeDisplays() {
            startTimeValue.text = formatHour(activity.morningStartHour)
            endTimeValue.text = formatHour(activity.morningEndHour)
        }

        private fun formatHour(hour: Int): String {
            return when {
                hour == 0 -> "12:00 AM"
                hour < 12 -> "$hour:00 AM"
                hour == 12 -> "12:00 PM"
                hour == 24 -> "12:00 AM"
                else -> "${hour - 12}:00 PM"
            }
        }

        private fun updateBlockingModeSelection() {
            val isFullMode = activity.blockingMode == SettingsRepository.BLOCKING_MODE_FULL

            // Update card backgrounds
            fullBlockCard.setCardBackgroundColor(
                itemView.context.getColor(if (isFullMode) R.color.primary_very_light else R.color.surface)
            )
            gentleReminderCard.setCardBackgroundColor(
                itemView.context.getColor(if (!isFullMode) R.color.primary_very_light else R.color.surface)
            )

            // Show/hide check icons
            fullBlockIcon.visibility = if (isFullMode) View.VISIBLE else View.GONE
            gentleReminderIcon.visibility = if (!isFullMode) View.VISIBLE else View.GONE
        }
    }
}

enum class OnboardingPage(
    val iconRes: Int,
    val titleRes: Int,
    val descriptionRes: Int
) {
    WELCOME(
        R.drawable.ic_sun,
        R.string.onboarding_welcome_title,
        R.string.onboarding_welcome_desc
    ),
    BLOCKING_MODE(
        R.drawable.ic_settings,
        R.string.onboarding_mode_title,
        R.string.onboarding_mode_desc
    ),
    BLOCKING_DURATION(
        R.drawable.ic_notification,
        R.string.onboarding_duration_title,
        R.string.onboarding_duration_desc
    ),
    WORD_COUNT(
        R.drawable.ic_edit,
        R.string.onboarding_wordcount_title,
        R.string.onboarding_wordcount_desc
    ),
    MORNING_WINDOW(
        R.drawable.ic_sun,
        R.string.onboarding_window_title,
        R.string.onboarding_window_desc
    ),
    PERMISSION_ACCESSIBILITY(
        R.drawable.ic_settings,
        R.string.onboarding_perm_accessibility_title,
        R.string.onboarding_perm_accessibility_desc
    ),
    PERMISSION_USAGE_STATS(
        R.drawable.ic_settings,
        R.string.onboarding_perm_usage_stats_title,
        R.string.onboarding_perm_usage_stats_desc
    ),
    PERMISSION_OVERLAY(
        R.drawable.ic_open_external,
        R.string.onboarding_perm_overlay_title,
        R.string.onboarding_perm_overlay_desc
    ),
    READY(
        R.drawable.ic_check_circle,
        R.string.onboarding_ready_title,
        R.string.onboarding_ready_desc
    )
}
