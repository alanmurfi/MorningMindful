package com.morningmindful.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.morningmindful.databinding.ActivityHistoryBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val viewModel: HistoryViewModel by viewModels { HistoryViewModel.Factory }
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
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allEntries.collectLatest { entries ->
                    if (entries.isEmpty()) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.entriesRecycler.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.entriesRecycler.visibility = View.VISIBLE
                        adapter.submitList(entries)
                    }
                }
            }
        }
    }
}
