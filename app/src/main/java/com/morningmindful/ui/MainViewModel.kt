package com.morningmindful.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.morningmindful.MorningMindfulApp
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val journalRepository: JournalRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    // Stats
    val totalEntries: StateFlow<Int> = journalRepository.getTotalEntryCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val totalWords: StateFlow<Int> = journalRepository.getTotalWordCount()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val currentStreak: StateFlow<Int> = journalRepository.getCurrentStreak()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Recent entries
    val recentEntries: StateFlow<List<JournalEntry>> = journalRepository.getRecentEntries(10)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Today's status
    private val _journalCompletedToday = MutableStateFlow(false)
    val journalCompletedToday: StateFlow<Boolean> = _journalCompletedToday.asStateFlow()

    init {
        checkTodayStatus()
        observeTodayEntry()
    }

    /**
     * Manually refresh today's status. Call this from onResume to pick up changes.
     */
    fun refreshTodayStatus() {
        checkTodayStatus()
    }

    private fun checkTodayStatus() {
        viewModelScope.launch {
            val todayEntry = journalRepository.getTodayEntry().first()
            val requiredWords = settingsRepository.requiredWordCount.first()
            _journalCompletedToday.value = todayEntry != null && todayEntry.wordCount >= requiredWords
        }
    }

    /**
     * Observe today's entry so the UI updates when entries are added/deleted.
     */
    private fun observeTodayEntry() {
        viewModelScope.launch {
            journalRepository.getTodayEntry().collect { todayEntry ->
                val requiredWords = settingsRepository.requiredWordCount.first()
                _journalCompletedToday.value = todayEntry != null && todayEntry.wordCount >= requiredWords
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = MorningMindfulApp.getInstance()
                return MainViewModel(
                    app.journalRepository,
                    app.settingsRepository
                ) as T
            }
        }
    }
}
