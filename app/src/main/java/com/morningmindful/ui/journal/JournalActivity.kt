package com.morningmindful.ui.journal

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.morningmindful.R
import com.morningmindful.data.entity.Moods
import com.morningmindful.databinding.ActivityJournalBinding
import com.morningmindful.util.BlockedApps
import com.morningmindful.util.BlockingState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Journal entry screen where users write their morning reflection.
 * This screen appears when trying to open a blocked app during the morning period.
 */
class JournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalBinding
    private val viewModel: JournalViewModel by viewModels { JournalViewModel.Factory }

    companion object {
        const val EXTRA_BLOCKED_APP = "blocked_app"
    }

    private val moodViews = mutableMapOf<String, MaterialCardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupMoodSelector()
        observeViewModel()
        handleBlockedAppMessage()
        setupBackPressHandler()
    }

    private fun setupUI() {
        // Word count tracking
        binding.journalEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                viewModel.updateJournalText(text)
            }
        })

        // Save & Unlock button (requires minimum word count)
        binding.saveButton.setOnClickListener {
            viewModel.saveJournalEntry()
        }

        // Save Draft button (saves without unlocking)
        binding.saveDraftButton.setOnClickListener {
            viewModel.saveDraft()
        }

        // Skip button (only visible after timer expires)
        binding.skipButton.setOnClickListener {
            showSkipConfirmation()
        }

        // Back button
        binding.backButton.setOnClickListener {
            handleBackPress()
        }
    }

    private fun handleBackPress() {
        // If blocking is still active, show message instead of closing
        if (BlockingState.shouldBlock()) {
            showBlockingMessage()
        } else {
            finish()
        }
    }

    private fun setupMoodSelector() {
        val moodContainer = binding.moodSelector

        Moods.ALL.forEach { (emoji, label) ->
            val cardView = MaterialCardView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.mood_card_size),
                    resources.getDimensionPixelSize(R.dimen.mood_card_size)
                ).apply {
                    marginEnd = resources.getDimensionPixelSize(R.dimen.mood_card_margin)
                }
                radius = resources.getDimension(R.dimen.mood_card_radius)
                cardElevation = 2f
                setCardBackgroundColor(ContextCompat.getColor(this@JournalActivity, R.color.surface))
                isClickable = true
                isFocusable = true

                setOnClickListener {
                    viewModel.setMood(emoji)
                    updateMoodSelection(emoji)
                }
            }

            val textView = TextView(this).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                text = emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
            }

            cardView.addView(textView)
            moodContainer.addView(cardView)
            moodViews[emoji] = cardView
        }
    }

    private fun updateMoodSelection(selectedMood: String?) {
        moodViews.forEach { (emoji, card) ->
            if (emoji == selectedMood) {
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_light))
                card.strokeWidth = resources.getDimensionPixelSize(R.dimen.mood_card_stroke)
                card.strokeColor = ContextCompat.getColor(this, R.color.primary)
            } else {
                card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface))
                card.strokeWidth = 0
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Word count and progress
                launch {
                    viewModel.wordCount.collectLatest { count ->
                        val required = viewModel.requiredWordCount.value
                        binding.wordCountText.text = "$count / $required words"

                        val progress = (count.toFloat() / required * 100).toInt().coerceIn(0, 100)
                        binding.wordCountProgress.progress = progress

                        // Update button state
                        binding.saveButton.isEnabled = count >= required
                        binding.saveButton.alpha = if (count >= required) 1f else 0.5f
                    }
                }

                // Timer - only show when blocking is active
                launch {
                    viewModel.remainingTimeFormatted.collectLatest { time ->
                        val isBlockingActive = BlockingState.shouldBlock()
                        val remainingSeconds = BlockingState.getRemainingSeconds()

                        if (isBlockingActive && remainingSeconds > 0) {
                            // Blocking active with time remaining - show timer
                            binding.timerText.visibility = View.VISIBLE
                            binding.timerText.text = time
                            binding.skipButton.visibility = View.GONE
                        } else if (isBlockingActive && remainingSeconds <= 0) {
                            // Blocking period expired - hide timer, show skip
                            binding.timerText.visibility = View.GONE
                            binding.skipButton.visibility = View.VISIBLE
                        } else {
                            // Not blocking (voluntary journal) - hide both timer and skip
                            binding.timerText.visibility = View.GONE
                            binding.skipButton.visibility = View.GONE
                        }
                    }
                }

                // Save state
                launch {
                    viewModel.saveState.collectLatest { state ->
                        when (state) {
                            is JournalViewModel.SaveState.Saving -> {
                                binding.saveButton.isEnabled = false
                                binding.saveDraftButton.isEnabled = false
                                binding.saveButton.text = "Saving..."
                            }
                            is JournalViewModel.SaveState.Success -> {
                                showSuccessAndClose()
                            }
                            is JournalViewModel.SaveState.DraftSaved -> {
                                showDraftSavedMessage()
                                binding.saveDraftButton.isEnabled = true
                                binding.saveButton.text = getString(R.string.save_entry)
                            }
                            is JournalViewModel.SaveState.Error -> {
                                binding.saveButton.isEnabled = true
                                binding.saveDraftButton.isEnabled = true
                                binding.saveButton.text = getString(R.string.save_entry)
                                showError(state.message)
                            }
                            else -> {
                                binding.saveButton.text = getString(R.string.save_entry)
                                binding.saveDraftButton.isEnabled = true
                            }
                        }
                    }
                }

                // Load existing entry if any
                launch {
                    viewModel.existingEntry.collectLatest { entry ->
                        if (entry != null && binding.journalEditText.text.isNullOrEmpty()) {
                            binding.journalEditText.setText(entry.content)
                        }
                    }
                }

                // Motivational prompt
                launch {
                    viewModel.journalPrompt.collectLatest { prompt ->
                        binding.promptText.text = prompt
                    }
                }

                // Selected mood (for loading existing entry)
                launch {
                    viewModel.selectedMood.collectLatest { mood ->
                        updateMoodSelection(mood)
                    }
                }
            }
        }
    }

    private fun handleBlockedAppMessage() {
        val blockedApp = intent.getStringExtra(EXTRA_BLOCKED_APP)
        if (blockedApp != null) {
            val appName = BlockedApps.getAppDisplayName(blockedApp)
            binding.blockedAppMessage.visibility = View.VISIBLE
            binding.blockedAppMessage.text = "You tried to open $appName.\nComplete your morning journal first."
        } else {
            binding.blockedAppMessage.visibility = View.GONE
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun showBlockingMessage() {
        val remaining = viewModel.remainingTimeFormatted.value
        MaterialAlertDialogBuilder(this)
            .setTitle("Morning Mindfulness Active")
            .setMessage("Complete your journal entry or wait $remaining to continue.")
            .setPositiveButton("Keep Writing") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSkipConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Skip Journaling?")
            .setMessage("The blocking period has ended. You can skip today's journal, but writing helps start your day mindfully.")
            .setPositiveButton("Skip Today") { _, _ ->
                finish()
            }
            .setNegativeButton("Keep Writing") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSuccessAndClose() {
        binding.successOverlay.visibility = View.VISIBLE
        binding.successOverlay.alpha = 0f
        binding.successOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .withEndAction {
                binding.successOverlay.postDelayed({
                    finish()
                }, 1500)
            }
            .start()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showDraftSavedMessage() {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            getString(R.string.draft_saved),
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }
}
