package com.morningmindful.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningmindful.data.repository.JournalRepository
import com.morningmindful.data.repository.SettingsRepository
import com.morningmindful.util.BlockedApps
import com.morningmindful.util.BlockingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val journalRepository: JournalRepository
) : ViewModel() {

    val isBlockingEnabled: StateFlow<Boolean> = settingsRepository.isBlockingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val blockingDurationMinutes: StateFlow<Int> = settingsRepository.blockingDurationMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 15)

    val requiredWordCount: StateFlow<Int> = settingsRepository.requiredWordCount
        .stateIn(viewModelScope, SharingStarted.Eagerly, 200)

    val blockedApps: StateFlow<Set<String>> = settingsRepository.blockedApps
        .stateIn(viewModelScope, SharingStarted.Eagerly, BlockedApps.DEFAULT_BLOCKED_PACKAGES)

    val morningStartHour: StateFlow<Int> = settingsRepository.morningStartHour
        .stateIn(viewModelScope, SharingStarted.Eagerly, 5)

    val morningEndHour: StateFlow<Int> = settingsRepository.morningEndHour
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    val themeMode: StateFlow<Int> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.THEME_MODE_SYSTEM)

    fun setBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBlockingEnabled(enabled)
        }
    }

    fun setBlockingDuration(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setBlockingDurationMinutes(minutes)
        }
    }

    fun setRequiredWordCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.setRequiredWordCount(count)
        }
    }

    fun toggleBlockedApp(packageName: String, block: Boolean) {
        viewModelScope.launch {
            if (block) {
                settingsRepository.addBlockedApp(packageName)
            } else {
                settingsRepository.removeBlockedApp(packageName)
            }
        }
    }

    fun setMorningStartHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setMorningStartHour(hour)
        }
    }

    fun setMorningEndHour(hour: Int) {
        viewModelScope.launch {
            settingsRepository.setMorningEndHour(hour)
        }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            settingsRepository.setBlockingEnabled(true)
            settingsRepository.setBlockingDurationMinutes(15)
            settingsRepository.setRequiredWordCount(200)
            settingsRepository.setBlockedApps(BlockedApps.DEFAULT_BLOCKED_PACKAGES)
            settingsRepository.setMorningStartHour(5)
            settingsRepository.setMorningEndHour(10)
        }
    }

    /**
     * Delete today's journal entry so blocking will activate again.
     * Useful for testing or if user wants to redo their journal.
     */
    fun resetTodayProgress() {
        viewModelScope.launch {
            // Delete today's journal entry
            val todayEntry = journalRepository.getTodayEntry().first()
            if (todayEntry != null) {
                journalRepository.deleteById(todayEntry.id)
            }
            // Reset the blocking state
            BlockingState.forceReset()
        }
    }
}
