package com.morningmindful

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.morningmindful.ui.settings.SettingsActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for SettingsActivity.
 * Tests the settings screen functionality including toggles and sliders.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class SettingsActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun settingsActivity_displaysBlockingToggle() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that blocking toggle is displayed
        onView(withId(R.id.blockingEnabledSwitch))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysDurationSlider() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that duration slider is displayed
        onView(withId(R.id.durationSlider))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysWordCountSlider() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that word count slider is displayed
        onView(withId(R.id.wordCountSlider))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysMorningWindowSection() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that morning window section is displayed
        onView(withText("Morning Window"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysBlockedAppsSection() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that blocked apps section header exists
        onView(withText("Blocked Apps"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysResetButton() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that reset button is displayed
        onView(withId(R.id.resetTodayButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysPrivacyPolicyButton() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that privacy policy button is displayed
        onView(withId(R.id.privacyPolicyButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysAppVersion() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that version info is displayed
        onView(withId(R.id.appVersion))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_blockingToggle_isClickable() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that blocking toggle is clickable
        onView(withId(R.id.blockingEnabledSwitch))
            .check(matches(isClickable()))
    }

    @Test
    fun settingsActivity_hasBackButton() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that back button exists
        onView(withId(R.id.backButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysThemeSection() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that appearance section header exists
        onView(withText("Appearance"))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysThemeRadioGroup() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that theme radio group is displayed
        onView(withId(R.id.themeRadioGroup))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsActivity_displaysThemeOptions() {
        ActivityScenario.launch(SettingsActivity::class.java)

        // Check that all theme options are displayed
        onView(withId(R.id.themeSystemRadio))
            .check(matches(isDisplayed()))
        onView(withId(R.id.themeLightRadio))
            .check(matches(isDisplayed()))
        onView(withId(R.id.themeDarkRadio))
            .check(matches(isDisplayed()))
    }
}
