package com.morningmindful.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryDetailViewModel @Inject constructor(
    private val journalRepository: JournalRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>(ENTRY_ID_KEY) ?: -1L

    private val _entry = MutableStateFlow<JournalEntry?>(null)
    val entry: StateFlow<JournalEntry?> = _entry.asStateFlow()

    init {
        if (entryId != -1L) {
            loadEntry()
        }
    }

    private fun loadEntry() {
        viewModelScope.launch {
            journalRepository.getEntryById(entryId).collect { entry ->
                _entry.value = entry
            }
        }
    }

    companion object {
        const val ENTRY_ID_KEY = "entry_id"
    }
}
