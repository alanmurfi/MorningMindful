package com.morningmindful.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main screen.
 * Uses Hilt for dependency injection - no manual Factory needed.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
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

    val longestStreak: StateFlow<Int> = journalRepository.getLongestStreak()
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
}
