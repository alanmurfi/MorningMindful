package com.morningmindful.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.morningmindful.MorningMindfulApp
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EntryDetailViewModel(
    private val journalRepository: JournalRepository,
    private val entryId: Long
) : ViewModel() {

    private val _entry = MutableStateFlow<JournalEntry?>(null)
    val entry: StateFlow<JournalEntry?> = _entry.asStateFlow()

    init {
        loadEntry()
    }

    private fun loadEntry() {
        viewModelScope.launch {
            journalRepository.getEntryById(entryId).collect { entry ->
                _entry.value = entry
            }
        }
    }

    class Factory(private val entryId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = MorningMindfulApp.getInstance()
            return EntryDetailViewModel(app.journalRepository, entryId) as T
        }
    }
}
