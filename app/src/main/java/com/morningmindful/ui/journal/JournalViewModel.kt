package com.morningmindful.ui.journal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.morningmindful.R
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.util.BlockingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * ViewModel for the Journal screen.
 *
 * Uses AndroidViewModel to safely hold Application context (no memory leak).
 * Uses Hilt for dependency injection.
 * Uses SavedStateHandle to get the edit date from Intent extras.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    application: Application,
    private val journalRepository: JournalRepository,
    private val settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    // Get edit date from SavedStateHandle (populated from Intent extras)
    private val editDate: LocalDate? = savedStateHandle.get<String>(EXTRA_EDIT_DATE)?.let {
        try {
            LocalDate.parse(it)
        } catch (e: Exception) {
            null
        }
    }

    // Whether we're editing a past entry
    val isEditingPastEntry: Boolean = editDate != null && editDate != LocalDate.now()
    private val targetDate: LocalDate = editDate ?: LocalDate.now()

    // Current journal text
    private val _journalText = MutableStateFlow("")
    val journalText: StateFlow<String> = _journalText.asStateFlow()

    // Word count
    private val _wordCount = MutableStateFlow(0)
    val wordCount: StateFlow<Int> = _wordCount.asStateFlow()

    // Selected mood
    private val _selectedMood = MutableStateFlow<String?>(null)
    val selectedMood: StateFlow<String?> = _selectedMood.asStateFlow()

    // Required word count from settings
    val requiredWordCount: StateFlow<Int> = settingsRepository.requiredWordCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 200)

    // Remaining time formatted
    private val _remainingTimeFormatted = MutableStateFlow("15:00")
    val remainingTimeFormatted: StateFlow<String> = _remainingTimeFormatted.asStateFlow()

    // Save state
    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // Existing entry for the target date (today or past entry being edited)
    val existingEntry: StateFlow<JournalEntry?> = journalRepository.getEntryByDate(targetDate)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Journal prompt
    private val _journalPrompt = MutableStateFlow(getRandomPrompt())
    val journalPrompt: StateFlow<String> = _journalPrompt.asStateFlow()

    // Track if content has been modified since last save
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    // Track the last saved content to detect changes
    private var lastSavedContent: String = ""
    private var lastSavedMood: String? = null

    // Auto-save job
    private var autoSaveJob: Job? = null
    private val autoSaveDelayMs = 3000L // 3 seconds after typing stops

    // Auto-save status for UI feedback
    private val _autoSaveStatus = MutableStateFlow<AutoSaveStatus>(AutoSaveStatus.Idle)
    val autoSaveStatus: StateFlow<AutoSaveStatus> = _autoSaveStatus.asStateFlow()

    init {
        startTimer()
        loadExistingEntry()
        ensureBlockingStarted()
    }

    /**
     * Ensure blocking period is started if we're in the morning window.
     * This is a fallback in case the screen unlock receiver didn't fire.
     */
    private fun ensureBlockingStarted() {
        // Don't start blocking when editing past entries
        if (isEditingPastEntry) return

        viewModelScope.launch {
            // Check if we're within the morning window first
            val currentHour = LocalTime.now().hour
            val startHour = settingsRepository.morningStartHour.first()
            val endHour = settingsRepository.morningEndHour.first()

            if (currentHour < startHour || currentHour >= endHour) {
                // Outside morning window - don't start blocking
                return@launch
            }

            // If blocking hasn't started yet and we're opening the journal,
            // start the blocking period with the configured duration
            if (BlockingState.getRemainingSeconds() <= 0 && !BlockingState.journalCompletedToday.value) {
                val duration = settingsRepository.blockingDurationMinutes.first()
                BlockingState.onFirstUnlock(duration)
            }
        }
    }

    fun updateJournalText(text: String) {
        _journalText.value = text
        _wordCount.value = countWords(text)

        // Check if content has changed from last saved state
        _hasUnsavedChanges.value = text != lastSavedContent || _selectedMood.value != lastSavedMood

        // Trigger auto-save with debounce
        scheduleAutoSave()
    }

    fun setMood(mood: String?) {
        _selectedMood.value = mood

        // Check if mood has changed from last saved state
        _hasUnsavedChanges.value = _journalText.value != lastSavedContent || mood != lastSavedMood

        // Trigger auto-save with debounce
        scheduleAutoSave()
    }

    /**
     * Schedule auto-save after a delay (debounced).
     * Cancels any pending auto-save and starts a new timer.
     */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()

        // Only auto-save if there's content to save
        if (_journalText.value.isBlank()) return

        autoSaveJob = viewModelScope.launch {
            delay(autoSaveDelayMs)
            performAutoSave()
        }
    }

    /**
     * Perform the actual auto-save operation.
     */
    private suspend fun performAutoSave() {
        val text = _journalText.value
        if (text.isBlank() || !_hasUnsavedChanges.value) return

        try {
            _autoSaveStatus.value = AutoSaveStatus.Saving
            saveEntry(text, _wordCount.value)

            // Update last saved state
            lastSavedContent = text
            lastSavedMood = _selectedMood.value
            _hasUnsavedChanges.value = false

            _autoSaveStatus.value = AutoSaveStatus.Saved

            // Reset status after a brief delay
            delay(2000)
            _autoSaveStatus.value = AutoSaveStatus.Idle
        } catch (e: Exception) {
            _autoSaveStatus.value = AutoSaveStatus.Error
        }
    }

    /**
     * Save journal and unlock blocking (requires minimum word count)
     */
    fun saveJournalEntry() {
        val text = _journalText.value
        val words = _wordCount.value
        val required = requiredWordCount.value

        if (words < required) {
            _saveState.value = SaveState.Error(getApplication<Application>().getString(R.string.error_minimum_words, required))
            return
        }

        // Cancel any pending auto-save
        autoSaveJob?.cancel()

        viewModelScope.launch {
            _saveState.value = SaveState.Saving

            try {
                saveEntry(text, words)

                // Update saved state
                lastSavedContent = text
                lastSavedMood = _selectedMood.value
                _hasUnsavedChanges.value = false

                // Mark journal as completed - this unlocks blocking (only for today's entry)
                if (!isEditingPastEntry) {
                    BlockingState.onJournalCompleted()
                }

                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                _saveState.value = SaveState.Error(getApplication<Application>().getString(R.string.error_failed_to_save, e.message ?: ""))
            }
        }
    }

    /**
     * Save progress without unlocking (no minimum word count required)
     */
    fun saveDraft() {
        val text = _journalText.value
        val words = _wordCount.value

        if (text.isBlank()) {
            _saveState.value = SaveState.Error(getApplication<Application>().getString(R.string.error_nothing_to_save))
            return
        }

        // Cancel any pending auto-save
        autoSaveJob?.cancel()

        viewModelScope.launch {
            _saveState.value = SaveState.Saving

            try {
                saveEntry(text, words)

                // Update saved state
                lastSavedContent = text
                lastSavedMood = _selectedMood.value
                _hasUnsavedChanges.value = false

                _saveState.value = SaveState.DraftSaved

            } catch (e: Exception) {
                _saveState.value = SaveState.Error(getApplication<Application>().getString(R.string.error_failed_to_save, e.message ?: ""))
            }
        }
    }

    private suspend fun saveEntry(text: String, words: Int) {
        // Sanitize inputs before saving
        val sanitizedContent = JournalEntry.sanitizeContent(text)
        val sanitizedWordCount = JournalEntry.sanitizeWordCount(words)

        val existingId = existingEntry.value?.id
        val entry = JournalEntry(
            id = existingId ?: 0,
            date = targetDate,
            content = sanitizedContent,
            wordCount = sanitizedWordCount,
            mood = _selectedMood.value,
            createdAt = existingEntry.value?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        if (existingId != null) {
            journalRepository.update(entry)
        } else {
            journalRepository.insert(entry)
        }
    }

    /**
     * Count words in text, with support for CJK (Chinese, Japanese, Korean) characters.
     * - For CJK characters: each character counts as one word (since CJK doesn't use spaces)
     * - For other text: split on whitespace and count
     */
    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0

        var count = 0
        var inWord = false

        for (char in text) {
            when {
                // CJK character ranges - each counts as one word
                isCjkCharacter(char) -> {
                    if (inWord) {
                        count++ // End the current word
                        inWord = false
                    }
                    count++ // Count the CJK character as a word
                }
                // Whitespace ends a word
                char.isWhitespace() -> {
                    if (inWord) {
                        count++
                        inWord = false
                    }
                }
                // Other characters are part of a word
                else -> {
                    inWord = true
                }
            }
        }

        // Don't forget the last word if text doesn't end with whitespace
        if (inWord) count++

        return count
    }

    /**
     * Check if a character is CJK (Chinese, Japanese, Korean).
     * Covers the main Unicode blocks for CJK characters.
     */
    private fun isCjkCharacter(char: Char): Boolean {
        val codePoint = char.code
        return (codePoint in 0x4E00..0x9FFF) ||    // CJK Unified Ideographs
               (codePoint in 0x3400..0x4DBF) ||    // CJK Extension A
               (codePoint in 0x3000..0x303F) ||    // CJK Symbols and Punctuation
               (codePoint in 0x3040..0x309F) ||    // Hiragana
               (codePoint in 0x30A0..0x30FF) ||    // Katakana
               (codePoint in 0xAC00..0xD7AF) ||    // Hangul Syllables (Korean)
               (codePoint in 0x1100..0x11FF)       // Hangul Jamo (Korean)
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                val remaining = BlockingState.getRemainingSeconds()
                val minutes = (remaining / 60).toInt()
                val seconds = (remaining % 60).toInt()
                _remainingTimeFormatted.value = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
            }
        }
    }

    private fun loadExistingEntry() {
        viewModelScope.launch {
            val entry = journalRepository.getEntryByDate(targetDate).first()
            if (entry != null) {
                _journalText.value = entry.content
                _wordCount.value = entry.wordCount
                _selectedMood.value = entry.mood

                // Initialize last saved state to track changes
                lastSavedContent = entry.content
                lastSavedMood = entry.mood
            }
        }
    }

    private fun getRandomPrompt(): String {
        val app = getApplication<Application>()
        val prompts = listOf(
            app.getString(R.string.prompt_grateful),
            app.getString(R.string.prompt_accomplish),
            app.getString(R.string.prompt_feeling),
            app.getString(R.string.prompt_on_mind),
            app.getString(R.string.prompt_ideal_day),
            app.getString(R.string.prompt_self_care),
            app.getString(R.string.prompt_looking_forward),
            app.getString(R.string.prompt_younger_self),
            app.getString(R.string.prompt_challenge),
            app.getString(R.string.prompt_joy)
        )
        return prompts.random()
    }

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Success : SaveState()
        object DraftSaved : SaveState()
        data class Error(val message: String) : SaveState()
    }

    sealed class AutoSaveStatus {
        object Idle : AutoSaveStatus()
        object Saving : AutoSaveStatus()
        object Saved : AutoSaveStatus()
        object Error : AutoSaveStatus()
    }

    companion object {
        // Must match JournalActivity.EXTRA_EDIT_DATE for SavedStateHandle to work
        const val EXTRA_EDIT_DATE = "edit_date"
    }
}
