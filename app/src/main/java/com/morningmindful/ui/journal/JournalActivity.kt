package com.morningmindful.ui.journal

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.morningmindful.R
import com.morningmindful.data.entity.JournalImage
import com.morningmindful.data.entity.Moods
import com.morningmindful.databinding.ActivityJournalBinding
import com.morningmindful.ui.ImageViewerActivity
import com.morningmindful.util.BlockedApps
import com.morningmindful.util.BlockingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * Journal entry screen where users write their morning reflection.
 * This screen appears when trying to open a blocked app during the morning period.
 */
@AndroidEntryPoint
class JournalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityJournalBinding

    // ViewModel is now injected by Hilt - edit_date is pulled from SavedStateHandle
    private val viewModel: JournalViewModel by viewModels()

    // Images adapter
    private var imagesAdapter: JournalImagesAdapter? = null

    // Photo picker launcher
    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.addImage(uri)
        }
    }

    companion object {
        const val EXTRA_BLOCKED_APP = "blocked_app"
        const val EXTRA_EDIT_DATE = "edit_date"
    }

    private val moodViews = mutableMapOf<String, MaterialCardView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityJournalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupMoodSelector()
        setupImagesRecyclerView()
        observeViewModel()
        handleBlockedAppMessage()
        setupBackPressHandler()
        updateHeaderForEditMode()
        setupKeyboardScrolling()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // If ViewModel has a stale date (e.g. yesterday), recreate the activity
        // so a fresh ViewModel is created with today's date
        if (!viewModel.isEditingPastEntry && viewModel.targetDate != LocalDate.now()) {
            recreate()
            return
        }

        // Update blocked app message for new intent
        handleBlockedAppMessage()
    }

    private fun updateHeaderForEditMode() {
        if (viewModel.isEditingPastEntry) {
            binding.headerText.text = getString(R.string.edit_entry)
            binding.blockedAppMessage.visibility = View.GONE
        }
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

        // Add photo button
        binding.addPhotoButton.setOnClickListener {
            // Check if entry exists (auto-save creates it)
            if (viewModel.existingEntry.value == null) {
                // Entry doesn't exist yet, need to type something first
                Toast.makeText(this, R.string.error_save_before_photo, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun setupImagesRecyclerView() {
        val imagesDir = File(filesDir, "journal_images")
        imagesAdapter = JournalImagesAdapter(
            imagesDir = imagesDir,
            onDeleteClick = { image ->
                showDeleteImageConfirmation(image)
            },
            onImageClick = { image ->
                openFullScreenImage(image)
            }
        )

        binding.imagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@JournalActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = imagesAdapter
        }
    }

    private fun showDeleteImageConfirmation(image: JournalImage) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_photo)
            .setMessage(R.string.delete_photo_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.deleteImage(image)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openFullScreenImage(image: JournalImage) {
        val intent = Intent(this, ImageViewerActivity::class.java).apply {
            putExtra(ImageViewerActivity.EXTRA_IMAGE_PATH, image.filePath)
            putExtra(ImageViewerActivity.EXTRA_IMAGE_TIMESTAMP, image.createdAt)
        }
        startActivity(intent)
    }

    private fun handleBackPress() {
        // Check for unsaved changes first
        if (viewModel.hasUnsavedChanges.value) {
            showUnsavedChangesDialog()
            return
        }

        // If editing a past entry, always allow closing
        if (viewModel.isEditingPastEntry) {
            finish()
            return
        }
        // If blocking is still active, show message instead of closing
        if (BlockingState.shouldBlock()) {
            showBlockingMessage()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.save_and_exit) { _, _ ->
                // Save draft and exit
                viewModel.saveDraft()
                // Wait a moment for save to complete, then exit
                binding.root.postDelayed({ finish() }, 500)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                finish()
            }
            .setNeutralButton(R.string.keep_writing) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
                        binding.wordCountText.text = getString(R.string.word_count_format, count, required)

                        val progress = (count.toFloat() / required * 100).toInt().coerceIn(0, 100)
                        binding.wordCountProgress.progress = progress

                        // Update button state
                        binding.saveButton.isEnabled = count >= required
                        binding.saveButton.alpha = if (count >= required) 1f else 0.5f
                    }
                }

                // Timer - show when there's remaining blocking time (not when editing past entries)
                launch {
                    viewModel.remainingTimeFormatted.collectLatest { time ->
                        // Hide timer when editing past entries
                        if (viewModel.isEditingPastEntry) {
                            binding.timerText.visibility = View.GONE
                            binding.skipButton.visibility = View.GONE
                            return@collectLatest
                        }

                        val remainingSeconds = BlockingState.getRemainingSeconds()

                        if (remainingSeconds > 0) {
                            // Time remaining - show timer
                            binding.timerText.visibility = View.VISIBLE
                            binding.timerText.text = time
                            binding.skipButton.visibility = View.GONE
                        } else if (BlockingState.shouldBlock()) {
                            // Blocking period expired but still blocking - show skip
                            binding.timerText.visibility = View.GONE
                            binding.skipButton.visibility = View.VISIBLE
                        } else {
                            // Not blocking - hide both
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
                                binding.saveButton.text = getString(R.string.saving)
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

                // Load existing entry content from ViewModel (may include timestamp separator)
                launch {
                    viewModel.journalText.collectLatest { text ->
                        // Only set text if EditText is empty and there's content to load
                        if (binding.journalEditText.text.isNullOrEmpty() && text.isNotEmpty()) {
                            binding.journalEditText.setText(text)
                            // Move cursor to end so user can continue writing
                            binding.journalEditText.setSelection(text.length)
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

                // Auto-save status
                launch {
                    viewModel.autoSaveStatus.collectLatest { status ->
                        when (status) {
                            is JournalViewModel.AutoSaveStatus.Saving -> {
                                binding.autoSaveStatus.visibility = View.VISIBLE
                                binding.autoSaveStatus.text = getString(R.string.auto_saving)
                            }
                            is JournalViewModel.AutoSaveStatus.Saved -> {
                                binding.autoSaveStatus.visibility = View.VISIBLE
                                binding.autoSaveStatus.text = getString(R.string.auto_saved)
                            }
                            is JournalViewModel.AutoSaveStatus.Error -> {
                                binding.autoSaveStatus.visibility = View.VISIBLE
                                binding.autoSaveStatus.text = getString(R.string.auto_save_failed)
                            }
                            else -> {
                                binding.autoSaveStatus.visibility = View.GONE
                            }
                        }
                    }
                }

                // Images list
                launch {
                    viewModel.images.collectLatest { images ->
                        imagesAdapter?.submitList(images)
                        // Show/hide RecyclerView based on whether there are images
                        binding.imagesRecyclerView.visibility = if (images.isNotEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Image status
                launch {
                    viewModel.imageStatus.collectLatest { status ->
                        when (status) {
                            is JournalViewModel.ImageStatus.Adding -> {
                                binding.addPhotoButton.isEnabled = false
                            }
                            is JournalViewModel.ImageStatus.Added -> {
                                binding.addPhotoButton.isEnabled = true
                                Toast.makeText(this@JournalActivity, R.string.photo_added, Toast.LENGTH_SHORT).show()
                                viewModel.clearImageStatus()
                            }
                            is JournalViewModel.ImageStatus.Deleted -> {
                                Toast.makeText(this@JournalActivity, R.string.photo_deleted, Toast.LENGTH_SHORT).show()
                                viewModel.clearImageStatus()
                            }
                            is JournalViewModel.ImageStatus.MaxReached -> {
                                Toast.makeText(
                                    this@JournalActivity,
                                    getString(R.string.max_photos_reached, JournalImage.MAX_IMAGES_PER_ENTRY),
                                    Toast.LENGTH_SHORT
                                ).show()
                                viewModel.clearImageStatus()
                            }
                            is JournalViewModel.ImageStatus.Error -> {
                                binding.addPhotoButton.isEnabled = true
                                Toast.makeText(this@JournalActivity, status.message, Toast.LENGTH_SHORT).show()
                                viewModel.clearImageStatus()
                            }
                            else -> {
                                binding.addPhotoButton.isEnabled = true
                            }
                        }
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
            binding.blockedAppMessage.text = getString(R.string.blocked_app_message, appName)
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
            .setTitle(R.string.morning_mindfulness_active)
            .setMessage(getString(R.string.complete_or_wait_message, remaining))
            .setPositiveButton(R.string.keep_writing) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showSkipConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.skip_journaling_title)
            .setMessage(R.string.skip_journaling_message)
            .setPositiveButton(R.string.skip_today) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.keep_writing) { dialog, _ ->
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
                // Check for review prompt opportunity
                checkForReviewPrompt()

                binding.successOverlay.postDelayed({
                    finish()
                }, 1500)
            }
            .start()
    }

    /**
     * Check if we should show an in-app review prompt.
     * Triggered after successfully saving a journal entry.
     */
    private fun checkForReviewPrompt() {
        lifecycleScope.launch {
            try {
                val app = com.morningmindful.MorningMindfulApp.getInstance()

                // Get current stats
                val totalEntries = app.journalRepository.getAllEntries().first().size
                val currentStreak = app.journalRepository.getCurrentStreak().first()

                // Check for review opportunities
                com.morningmindful.util.InAppReviewManager.checkAndRequestReviewForStreak(
                    this@JournalActivity,
                    currentStreak
                )
                com.morningmindful.util.InAppReviewManager.checkAndRequestReviewForEntries(
                    this@JournalActivity,
                    totalEntries
                )
            } catch (e: Exception) {
                android.util.Log.e("JournalActivity", "Error checking for review prompt", e)
            }
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun showDraftSavedMessage() {
        com.google.android.material.snackbar.Snackbar.make(
            binding.root,
            getString(R.string.draft_saved),
            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun setupKeyboardScrolling() {
        // Use WindowInsets to detect keyboard and add padding
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            val navigationBarHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom

            // Add padding to bottom when keyboard is showing
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                if (imeHeight > 0) imeHeight else navigationBarHeight
            )

            // Scroll to bottom when keyboard appears
            if (imeHeight > 0) {
                (view as? android.widget.ScrollView)?.post {
                    view.fullScroll(View.FOCUS_DOWN)
                }
            }

            insets
        }
    }
}
