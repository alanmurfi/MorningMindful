package com.morningmindful.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.data.entity.JournalImage
import com.morningmindful.data.repository.JournalImageRepository
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
    private val journalImageRepository: JournalImageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val entryId: Long = savedStateHandle.get<Long>(ENTRY_ID_KEY) ?: -1L

    private val _entry = MutableStateFlow<JournalEntry?>(null)
    val entry: StateFlow<JournalEntry?> = _entry.asStateFlow()

    private val _images = MutableStateFlow<List<JournalImage>>(emptyList())
    val images: StateFlow<List<JournalImage>> = _images.asStateFlow()

    init {
        if (entryId != -1L) {
            loadEntry()
            loadImages()
        }
    }

    private fun loadEntry() {
        viewModelScope.launch {
            journalRepository.getEntryById(entryId).collect { entry ->
                _entry.value = entry
            }
        }
    }

    private fun loadImages() {
        viewModelScope.launch {
            journalImageRepository.getImagesForEntry(entryId).collect { imageList ->
                _images.value = imageList
            }
        }
    }

    /**
     * Get the images directory for the adapter
     */
    fun getImagesDir() = journalImageRepository.getImageFile("")

    companion object {
        const val ENTRY_ID_KEY = "entry_id"
    }
}
