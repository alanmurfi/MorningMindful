package com.morningmindful

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.morningmindful.ui.journal.JournalActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for JournalActivity.
 * Tests the journal entry screen including text input, word count, and save functionality.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class JournalActivityTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun journalActivity_displaysJournalEditText() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that the journal text input is displayed
        onView(withId(R.id.journalEditText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_displaysWordCount() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that word count text is displayed
        onView(withId(R.id.wordCountText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_displaysPrompt() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that a prompt is displayed
        onView(withId(R.id.promptText))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_displaysSaveButton() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that save button is displayed
        onView(withId(R.id.saveButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_displaysSaveDraftButton() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that save draft button is displayed
        onView(withId(R.id.saveDraftButton))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_saveButtonInitiallyDisabled() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Save button should be disabled when no text is entered
        onView(withId(R.id.saveButton))
            .check(matches(not(isEnabled())))
    }

    @Test
    fun journalActivity_typeText_updatesWordCount() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Type some text
        onView(withId(R.id.journalEditText))
            .perform(typeText("Hello world this is a test"))

        // Close keyboard
        onView(withId(R.id.journalEditText))
            .perform(closeSoftKeyboard())

        // Word count should update (5 words)
        onView(withId(R.id.wordCountText))
            .check(matches(withText(org.hamcrest.Matchers.containsString("5"))))
    }

    @Test
    fun journalActivity_displaysMoodSelector() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that mood selector exists
        onView(withId(R.id.moodSelector))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_displaysProgressBar() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that progress bar is displayed
        onView(withId(R.id.wordCountProgress))
            .check(matches(isDisplayed()))
    }

    @Test
    fun journalActivity_hasBackButton() {
        ActivityScenario.launch(JournalActivity::class.java)

        // Check that back button exists
        onView(withId(R.id.backButton))
            .check(matches(isDisplayed()))
    }
}
