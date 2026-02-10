package com.morningmindful.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.morningmindful.R
import com.morningmindful.databinding.ActivityHistoryBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: JournalEntryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        adapter = JournalEntryAdapter { entry ->
            // Open entry detail
            val intent = Intent(this, EntryDetailActivity::class.java).apply {
                putExtra(EntryDetailActivity.EXTRA_ENTRY_ID, entry.id)
            }
            startActivity(intent)
        }

        binding.entriesRecycler.apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
            adapter = this@HistoryActivity.adapter
        }

        // Setup search
        binding.searchInput.doAfterTextChanged { text ->
            viewModel.setSearchQuery(text?.toString() ?: "")
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Hide keyboard
                binding.searchInput.clearFocus()
                true
            } else {
                false
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Combine entries and search query to show appropriate UI
                viewModel.entries.combine(viewModel.searchQuery) { entries, query ->
                    Pair(entries, query)
                }.collectLatest { (entries, query) ->
                    val isSearching = query.isNotBlank()

                    if (entries.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.entriesRecycler.visibility = View.GONE
                        binding.searchResultsInfo.visibility = View.GONE

                        // Update empty state text based on search
                        if (isSearching) {
                            binding.emptyStateTitle.text = getString(R.string.no_search_results)
                            binding.emptyStateSubtitle.text = getString(R.string.try_different_search)
                        } else {
                            binding.emptyStateTitle.text = getString(R.string.no_entries_yet)
                            binding.emptyStateSubtitle.text = getString(R.string.start_writing_history)
                        }
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.entriesRecycler.visibility = View.VISIBLE
                        adapter.submitList(entries)

                        // Show search results count when searching
                        if (isSearching) {
                            binding.searchResultsInfo.visibility = View.VISIBLE
                            binding.searchResultsInfo.text = if (entries.size == 1) {
                                getString(R.string.search_results_one)
                            } else {
                                getString(R.string.search_results_count, entries.size)
                            }
                        } else {
                            binding.searchResultsInfo.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
}
