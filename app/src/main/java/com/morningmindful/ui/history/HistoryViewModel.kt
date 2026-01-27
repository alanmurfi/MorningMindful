package com.morningmindful.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.morningmindful.MorningMindfulApp
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.repository.JournalRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HistoryViewModel(
    private val journalRepository: JournalRepository
) : ViewModel() {

    val allEntries: StateFlow<List<JournalEntry>> = journalRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = MorningMindfulApp.getInstance()
                return HistoryViewModel(app.journalRepository) as T
            }
        }
    }
}
