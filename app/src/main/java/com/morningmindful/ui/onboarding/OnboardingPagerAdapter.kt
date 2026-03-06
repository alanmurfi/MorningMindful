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
 * Value-first flow: Welcome → Problem → How It Works → Mode → Permissions → Ready
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
                    OnboardingPage.PROBLEM,
                    OnboardingPage.HOW_IT_WORKS,
                    OnboardingPage.BLOCKING_MODE,
                    OnboardingPage.PERMISSION_USAGE_STATS,
                    OnboardingPage.PERMISSION_OVERLAY,
                    OnboardingPage.READY
                )
            } else {
                listOf(
                    OnboardingPage.WELCOME,
                    OnboardingPage.PROBLEM,
                    OnboardingPage.HOW_IT_WORKS,
                    OnboardingPage.BLOCKING_MODE,
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
                OnboardingPage.HOW_IT_WORKS -> {
                    stepsContainer.visibility = View.VISIBLE
                    itemView.findViewById<TextView>(R.id.step1Title).text =
                        itemView.context.getString(R.string.onboarding_step1_title)
                    itemView.findViewById<TextView>(R.id.step1Desc).text =
                        itemView.context.getString(R.string.onboarding_step1_desc)
                    itemView.findViewById<TextView>(R.id.step2Title).text =
                        itemView.context.getString(R.string.onboarding_step2_title)
                    itemView.findViewById<TextView>(R.id.step2Desc).text =
                        itemView.context.getString(R.string.onboarding_step2_desc)
                    itemView.findViewById<TextView>(R.id.step3Title).text =
                        itemView.context.getString(R.string.onboarding_step3_title)
                    itemView.findViewById<TextView>(R.id.step3Desc).text =
                        itemView.context.getString(R.string.onboarding_step3_desc)
                }
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
                    // Welcome, Problem, Ready pages - no extra controls needed
                }
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
    PROBLEM(
        R.drawable.ic_block,
        R.string.onboarding_problem_title,
        R.string.onboarding_problem_desc
    ),
    HOW_IT_WORKS(
        R.drawable.ic_journal,
        R.string.onboarding_how_it_works_title,
        R.string.onboarding_how_it_works_desc
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
    PERMISSION_NOTIFICATIONS(
        R.drawable.ic_notification,
        R.string.onboarding_perm_notifications_title,
        R.string.onboarding_perm_notifications_desc
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
    BACKUP_SETUP(
        R.drawable.ic_backup,
        R.string.onboarding_backup_title,
        R.string.onboarding_backup_desc
    ),
    READY(
        R.drawable.ic_check_circle,
        R.string.onboarding_ready_title,
        R.string.onboarding_ready_desc
    )
}
