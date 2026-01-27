package com.morningmindful.ui.history

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.morningmindful.data.entity.Moods
import com.morningmindful.databinding.ActivityEntryDetailBinding
import com.morningmindful.ui.journal.JournalActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

class EntryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryDetailBinding
    private val viewModel: EntryDetailViewModel by viewModels {
        EntryDetailViewModel.Factory(intent.getLongExtra(EXTRA_ENTRY_ID, -1))
    }

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.editButton.setOnClickListener {
            // Navigate to journal activity to edit
            startActivity(Intent(this, JournalActivity::class.java))
            finish()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.entry.collectLatest { entry ->
                    if (entry != null) {
                        binding.dateHeader.text = entry.date.format(dateFormatter)
                        binding.journalContent.text = entry.content
                        binding.wordCountText.text = "${entry.wordCount} words"
                        binding.timestampText.text = "Written at ${timeFormatter.format(Date(entry.createdAt))}"

                        // Show mood if present
                        if (entry.mood != null) {
                            binding.moodContainer.visibility = View.VISIBLE
                            binding.moodEmoji.text = entry.mood
                            // Find mood label
                            val moodLabel = Moods.ALL.find { it.first == entry.mood }?.second ?: ""
                            binding.moodLabel.text = "Feeling $moodLabel"
                        } else {
                            binding.moodContainer.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }
}
