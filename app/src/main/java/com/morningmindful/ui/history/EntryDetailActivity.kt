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
import com.morningmindful.R
import com.morningmindful.data.entity.Moods
import com.morningmindful.databinding.ActivityEntryDetailBinding
import com.morningmindful.ui.journal.JournalActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@AndroidEntryPoint
class EntryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEntryDetailBinding
    private val viewModel: EntryDetailViewModel by viewModels()
    private var imagesAdapter: JournalImagesViewAdapter? = null

    companion object {
        const val EXTRA_ENTRY_ID = "entry_id"
    }

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEntryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupImagesRecyclerView()
        observeViewModel()
    }

    private fun setupUI() {
        binding.backButton.setOnClickListener {
            finish()
        }

        binding.editButton.setOnClickListener {
            // Navigate to journal activity to edit this entry
            val entry = viewModel.entry.value
            if (entry != null) {
                val intent = Intent(this, JournalActivity::class.java).apply {
                    putExtra(JournalActivity.EXTRA_EDIT_DATE, entry.date.toString())
                }
                startActivity(intent)
            }
            finish()
        }
    }

    private fun setupImagesRecyclerView() {
        val imagesDir = File(filesDir, "journal_images")
        imagesAdapter = JournalImagesViewAdapter(
            imagesDir = imagesDir,
            onImageClick = { image ->
                // TODO: Show full-screen image view
            }
        )

        binding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@EntryDetailActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imagesAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Entry data
                launch {
                    viewModel.entry.collectLatest { entry ->
                        if (entry != null) {
                            binding.dateHeader.text = entry.date.format(dateFormatter)
                            binding.journalContent.text = entry.content
                            binding.wordCountText.text = getString(R.string.word_count_display, entry.wordCount)
                            binding.timestampText.text = getString(R.string.written_at, timeFormatter.format(Instant.ofEpochMilli(entry.createdAt)))

                            // Show mood if present
                            if (entry.mood != null) {
                                binding.moodContainer.visibility = View.VISIBLE
                                binding.moodEmoji.text = entry.mood
                                // Find mood label
                                val moodLabel = Moods.ALL.find { it.first == entry.mood }?.second ?: ""
                                binding.moodLabel.text = getString(R.string.feeling_mood, moodLabel)
                            } else {
                                binding.moodContainer.visibility = View.GONE
                            }
                        }
                    }
                }

                // Images
                launch {
                    viewModel.images.collectLatest { images ->
                        imagesAdapter?.submitList(images)
                        binding.imagesRecyclerView.visibility = if (images.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }
}
