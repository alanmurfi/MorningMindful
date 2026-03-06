package com.morningmindful.ui.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView

object OnboardingAnimationUtils {

    /**
     * Animate text lines fading in and sliding up with a stagger delay.
     */
    fun animateTextLines(
        textViews: List<TextView>,
        staggerDelayMs: Long = 600L,
        initialDelayMs: Long = 0L
    ) {
        textViews.forEachIndexed { index, tv ->
            if (tv.text.isNullOrEmpty()) return@forEachIndexed

            tv.alpha = 0f
            tv.translationY = 30f

            val fadeIn = ObjectAnimator.ofFloat(tv, View.ALPHA, 0f, 1f).apply {
                duration = 400
            }
            val slideUp = ObjectAnimator.ofFloat(tv, View.TRANSLATION_Y, 30f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
            }

            AnimatorSet().apply {
                playTogether(fadeIn, slideUp)
                startDelay = initialDelayMs + (index * staggerDelayMs)
                start()
            }
        }
    }

    /**
     * Fade in a view after a delay.
     */
    fun fadeInView(view: View, delayMs: Long = 0L, durationMs: Long = 400L) {
        view.alpha = 0f
        ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
            duration = durationMs
            startDelay = delayMs
            start()
        }
    }
}
