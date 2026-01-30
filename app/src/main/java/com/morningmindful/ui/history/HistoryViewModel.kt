package com.morningmindful.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val journalRepository: JournalRepository
) : ViewModel() {

    val allEntries: StateFlow<List<JournalEntry>> = journalRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
