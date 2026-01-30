package com.morningmindful

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.morningmindful.ui.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MainActivity.
 * Tests the main screen functionality including navigation and stats display.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun mainActivity_displaysAppTitle() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that the app name is displayed
        onView(withText(R.string.app_name))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_displaysStreakCard() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that the current streak section is displayed
        onView(withText(R.string.current_streak))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_displaysLongestStreakCard() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that the longest streak section is displayed
        onView(withText(R.string.longest_streak))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_settingsButtonExists() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that settings button is displayed
        onView(withId(R.id.settingsButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_writeJournalButtonExists() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that write journal button exists
        onView(withId(R.id.writeJournalButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_viewAllButtonExists() {
        ActivityScenario.launch(MainActivity::class.java)

        // Check that view all entries button exists
        onView(withId(R.id.viewAllButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun mainActivity_clickSettingsButton_opensSettings() {
        ActivityScenario.launch(MainActivity::class.java)

        // Click settings button
        onView(withId(R.id.settingsButton))
            .perform(click())

        // Verify we're on settings screen (check for a settings-specific view)
        // Settings screen should have blocking toggle
        onView(withText("Blocking"))
            .check(matches(isDisplayed()))
    }
}
