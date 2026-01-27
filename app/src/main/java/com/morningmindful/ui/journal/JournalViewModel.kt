package com.morningmindful.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.morningmindful.MorningMindfulApp
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

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

    // Existing entry for today (if resuming)
    val existingEntry: StateFlow<JournalEntry?> = journalRepository.getTodayEntry()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Journal prompt
    private val _journalPrompt = MutableStateFlow(getRandomPrompt())
    val journalPrompt: StateFlow<String> = _journalPrompt.asStateFlow()

    init {
        startTimer()
        loadExistingEntry()
    }

    fun updateJournalText(text: String) {
        _journalText.value = text
        _wordCount.value = countWords(text)
    }

    fun setMood(mood: String?) {
        _selectedMood.value = mood
    }

    fun saveJournalEntry() {
        val text = _journalText.value
        val words = _wordCount.value
        val required = requiredWordCount.value

        if (words < required) {
            _saveState.value = SaveState.Error("Please write at least $required words")
            return
        }

        viewModelScope.launch {
            _saveState.value = SaveState.Saving

            try {
                val existingId = existingEntry.value?.id
                val entry = JournalEntry(
                    id = existingId ?: 0,
                    date = LocalDate.now(),
                    content = text,
                    wordCount = words,
                    mood = _selectedMood.value,
                    createdAt = existingEntry.value?.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                if (existingId != null) {
                    journalRepository.update(entry)
                } else {
                    journalRepository.insert(entry)
                }

                // Mark journal as completed
                BlockingState.onJournalCompleted()

                _saveState.value = SaveState.Success

            } catch (e: Exception) {
                _saveState.value = SaveState.Error("Failed to save: ${e.message}")
            }
        }
    }

    private fun countWords(text: String): Int {
        return text.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
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
            val entry = journalRepository.getTodayEntry().first()
            if (entry != null) {
                _journalText.value = entry.content
                _wordCount.value = entry.wordCount
                _selectedMood.value = entry.mood
            }
        }
    }

    private fun getRandomPrompt(): String {
        val prompts = listOf(
            "What are you grateful for this morning?",
            "What's one thing you want to accomplish today?",
            "How are you feeling right now? Why might that be?",
            "What's been on your mind lately?",
            "Describe your ideal day. What would make today great?",
            "What's one small thing you can do today to take care of yourself?",
            "Write about something you're looking forward to.",
            "What would you tell your younger self?",
            "What's a challenge you're facing? How might you approach it?",
            "Describe a moment recently that brought you joy."
        )
        return prompts.random()
    }

    sealed class SaveState {
        object Idle : SaveState()
        object Saving : SaveState()
        object Success : SaveState()
        data class Error(val message: String) : SaveState()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = MorningMindfulApp.getInstance()
                return JournalViewModel(
                    app.journalRepository,
                    app.settingsRepository
                ) as T
            }
        }
    }
}
