package com.morningmindful.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Singleton object that manages the current blocking state.
 * This is shared between the Accessibility Service and other components.
 */
object BlockingState {

    private val _isBlocking = MutableStateFlow(false)
    val isBlocking: StateFlow<Boolean> = _isBlocking.asStateFlow()

    private val _blockingEndTime = MutableStateFlow<LocalDateTime?>(null)
    val blockingEndTime: StateFlow<LocalDateTime?> = _blockingEndTime.asStateFlow()

    private val _journalCompletedToday = MutableStateFlow(false)
    val journalCompletedToday: StateFlow<Boolean> = _journalCompletedToday.asStateFlow()

    private val _firstUnlockTime = MutableStateFlow<LocalDateTime?>(null)
    val firstUnlockTime: StateFlow<LocalDateTime?> = _firstUnlockTime.asStateFlow()

    private var lastResetDate: LocalDate? = null

    /**
     * Call this when the device is unlocked for the first time today.
     * Starts the 15-minute blocking window.
     */
    fun onFirstUnlock(blockingDurationMinutes: Int = 15) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()

        // Reset state if it's a new day
        if (lastResetDate != today) {
            resetForNewDay()
            lastResetDate = today
        }

        // Only start blocking if we haven't already unlocked today and haven't journaled
        if (_firstUnlockTime.value == null && !_journalCompletedToday.value) {
            _firstUnlockTime.value = now
            _blockingEndTime.value = now.plusMinutes(blockingDurationMinutes.toLong())
            _isBlocking.value = true
        }
    }

    /**
     * Call this when a valid journal entry has been completed.
     * Immediately ends the blocking period.
     */
    fun onJournalCompleted() {
        _journalCompletedToday.value = true
        _isBlocking.value = false
    }

    /**
     * Check if we should currently be blocking apps.
     */
    fun shouldBlock(): Boolean {
        val endTime = _blockingEndTime.value ?: return false
        val now = LocalDateTime.now()

        // Check if blocking period has expired
        if (now.isAfter(endTime)) {
            _isBlocking.value = false
            return false
        }

        // Check if journal was completed
        if (_journalCompletedToday.value) {
            return false
        }

        return _isBlocking.value
    }

    /**
     * Get remaining blocking time in seconds.
     */
    fun getRemainingSeconds(): Long {
        val endTime = _blockingEndTime.value ?: return 0
        val now = LocalDateTime.now()

        if (now.isAfter(endTime)) return 0

        return java.time.Duration.between(now, endTime).seconds
    }

    /**
     * Reset state for a new day.
     */
    private fun resetForNewDay() {
        _isBlocking.value = false
        _blockingEndTime.value = null
        _journalCompletedToday.value = false
        _firstUnlockTime.value = null
    }

    /**
     * Force reset (for testing or manual override).
     */
    fun forceReset() {
        resetForNewDay()
        lastResetDate = null
    }

    /**
     * Mark that journal was already completed today (e.g., loaded from database).
     */
    fun setJournalCompletedToday(completed: Boolean) {
        _journalCompletedToday.value = completed
        if (completed) {
            _isBlocking.value = false
        }
    }
}
