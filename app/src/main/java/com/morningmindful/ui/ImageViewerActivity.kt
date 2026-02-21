package com.morningmindful.ui

import android.os.Bundle
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.morningmindful.databinding.ActivityImageViewerBinding
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen image viewer for journal photos.
 * Shows the image at full size with zoom/pan support.
 */
class ImageViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageViewerBinding

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_IMAGE_TIMESTAMP = "image_timestamp"
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.BLACK)
        )
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadImage()
    }

    private fun setupUI() {
        // Close button
        binding.closeButton.setOnClickListener {
            finish()
        }

        // Tap anywhere on image to toggle UI visibility
        binding.imageView.setOnClickListener {
            toggleUIVisibility()
        }

        // Make background semi-transparent black
        binding.root.setBackgroundColor(0xE6000000.toInt())
    }

    private fun loadImage() {
        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        val timestamp = intent.getLongExtra(EXTRA_IMAGE_TIMESTAMP, 0)

        if (imagePath == null) {
            finish()
            return
        }

        val imagesDir = File(filesDir, "journal_images")
        val imageFile = File(imagesDir, imagePath)

        if (!imageFile.exists()) {
            finish()
            return
        }

        // Load image with Glide
        Glide.with(this)
            .load(imageFile)
            .into(binding.imageView)

        // Show timestamp if available
        if (timestamp > 0) {
            binding.timestampText.visibility = View.VISIBLE
            binding.timestampText.text = timeFormatter.format(Instant.ofEpochMilli(timestamp))
        } else {
            binding.timestampText.visibility = View.GONE
        }
    }

    private fun toggleUIVisibility() {
        val isVisible = binding.closeButton.visibility == View.VISIBLE
        val newVisibility = if (isVisible) View.GONE else View.VISIBLE

        binding.closeButton.visibility = newVisibility
        binding.timestampText.visibility = if (newVisibility == View.VISIBLE &&
            intent.getLongExtra(EXTRA_IMAGE_TIMESTAMP, 0) > 0) View.VISIBLE else View.GONE
    }
}
