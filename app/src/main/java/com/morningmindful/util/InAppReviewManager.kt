package com.morningmindful.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import com.morningmindful.MorningMindfulApp
import kotlinx.coroutines.flow.first

/**
 * Manages in-app review prompts using Google Play In-App Review API.
 *
 * Review prompts are shown at strategic moments:
 * - After reaching streak milestones (3, 7, 14, 30 days)
 * - After completing a certain number of journal entries
 *
 * Google controls when the review dialog actually appears - we can only request it.
 * The API has built-in rate limiting to prevent spamming users.
 */
object InAppReviewManager {

    private const val TAG = "InAppReviewManager"

    // Preferences keys for tracking review state
    private const val PREF_NAME = "in_app_review"
    private const val KEY_LAST_REVIEW_REQUEST_TIME = "last_review_request_time"
    private const val KEY_REVIEW_REQUESTED_COUNT = "review_requested_count"
    private const val KEY_HAS_REVIEWED = "has_reviewed"

    // Rate limiting: Don't request more than once per 30 days
    private const val MIN_DAYS_BETWEEN_REQUESTS = 30L
    private const val MS_PER_DAY = 24 * 60 * 60 * 1000L

    // Milestone streaks that trigger review prompt
    private val REVIEW_STREAK_MILESTONES = listOf(3, 7, 14, 30)

    // Entry count milestones
    private val REVIEW_ENTRY_MILESTONES = listOf(5, 15, 30)

    /**
     * Check if we should request a review based on current streak.
     * Call this when the user completes their journal for the day.
     */
    suspend fun checkAndRequestReviewForStreak(activity: Activity, currentStreak: Int) {
        if (currentStreak in REVIEW_STREAK_MILESTONES) {
            Log.d(TAG, "Streak milestone reached: $currentStreak days")
            requestReviewIfEligible(activity, "streak_$currentStreak")
        }
    }

    /**
     * Check if we should request a review based on total entries.
     * Call this after saving a new journal entry.
     */
    suspend fun checkAndRequestReviewForEntries(activity: Activity, totalEntries: Int) {
        if (totalEntries in REVIEW_ENTRY_MILESTONES) {
            Log.d(TAG, "Entry milestone reached: $totalEntries entries")
            requestReviewIfEligible(activity, "entries_$totalEntries")
        }
    }

    /**
     * Request a review if the user is eligible (hasn't been asked recently).
     */
    private suspend fun requestReviewIfEligible(activity: Activity, trigger: String) {
        val prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        // Check if user has already reviewed (we assume they did if dialog was shown)
        val hasReviewed = prefs.getBoolean(KEY_HAS_REVIEWED, false)
        if (hasReviewed) {
            Log.d(TAG, "User has already reviewed, skipping")
            return
        }

        // Check rate limiting
        val lastRequestTime = prefs.getLong(KEY_LAST_REVIEW_REQUEST_TIME, 0)
        val daysSinceLastRequest = (System.currentTimeMillis() - lastRequestTime) / MS_PER_DAY

        if (lastRequestTime > 0 && daysSinceLastRequest < MIN_DAYS_BETWEEN_REQUESTS) {
            Log.d(TAG, "Too soon since last request ($daysSinceLastRequest days), skipping")
            return
        }

        // Check that blocking isn't currently active (don't interrupt the user)
        if (BlockingState.shouldBlock()) {
            Log.d(TAG, "Blocking is active, not a good time for review")
            return
        }

        // All checks passed, request review
        Log.d(TAG, "Requesting review (trigger: $trigger)")
        requestReview(activity)

        // Update tracking
        prefs.edit()
            .putLong(KEY_LAST_REVIEW_REQUEST_TIME, System.currentTimeMillis())
            .putInt(KEY_REVIEW_REQUESTED_COUNT, prefs.getInt(KEY_REVIEW_REQUESTED_COUNT, 0) + 1)
            .apply()

        // Track analytics
        Analytics.trackReviewRequested(trigger)
    }

    /**
     * Actually request the review using Play In-App Review API.
     */
    private fun requestReview(activity: Activity) {
        val reviewManager = ReviewManagerFactory.create(activity)

        val requestFlow = reviewManager.requestReviewFlow()
        requestFlow.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                flow.addOnCompleteListener {
                    // The review flow has finished.
                    // We don't know if the user actually left a review.
                    Log.d(TAG, "Review flow completed")

                    // Mark as reviewed (Google rate-limits anyway)
                    activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(KEY_HAS_REVIEWED, true)
                        .apply()
                }
            } else {
                Log.e(TAG, "Failed to request review flow", task.exception)
            }
        }
    }

    /**
     * Reset review state (for testing purposes).
     */
    fun resetReviewState(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        Log.d(TAG, "Review state reset")
    }
}
