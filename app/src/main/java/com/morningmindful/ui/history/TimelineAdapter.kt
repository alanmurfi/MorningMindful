package com.morningmindful.ui.history

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.morningmindful.R
import com.morningmindful.data.entity.JournalImage
import com.morningmindful.ui.ImageViewerActivity
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Adapter for displaying journal entries as a timeline with text and images
 * shown chronologically based on their timestamps.
 */
class TimelineAdapter(
    private val imagesDir: File
) : ListAdapter<TimelineItem, RecyclerView.ViewHolder>(TimelineDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_TEXT = 0
        private const val VIEW_TYPE_IMAGE = 1
        private const val VIEW_TYPE_TIMESTAMP_HEADER = 2
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TimelineItem.TextSegment -> VIEW_TYPE_TEXT
            is TimelineItem.ImageItem -> VIEW_TYPE_IMAGE
            is TimelineItem.TimestampHeader -> VIEW_TYPE_TIMESTAMP_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TEXT -> TextViewHolder(
                inflater.inflate(R.layout.item_timeline_text, parent, false)
            )
            VIEW_TYPE_IMAGE -> ImageViewHolder(
                inflater.inflate(R.layout.item_timeline_image, parent, false),
                imagesDir,
                timeFormatter
            )
            VIEW_TYPE_TIMESTAMP_HEADER -> TimestampHeaderViewHolder(
                inflater.inflate(R.layout.item_timeline_timestamp, parent, false),
                timeFormatter
            )
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TimelineItem.TextSegment -> (holder as TextViewHolder).bind(item)
            is TimelineItem.ImageItem -> (holder as ImageViewHolder).bind(item)
            is TimelineItem.TimestampHeader -> (holder as TimestampHeaderViewHolder).bind(item)
        }
    }

    class TextViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(R.id.textContent)

        fun bind(item: TimelineItem.TextSegment) {
            textView.text = item.text
        }
    }

    class ImageViewHolder(
        itemView: View,
        private val imagesDir: File,
        private val timeFormatter: DateTimeFormatter
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)

        fun bind(item: TimelineItem.ImageItem) {
            val imageFile = File(imagesDir, item.image.filePath)

            Glide.with(itemView.context)
                .load(imageFile)
                .transform(CenterCrop(), RoundedCorners(16))
                .placeholder(R.color.divider)
                .into(imageView)

            // Show timestamp
            timestampText.text = timeFormatter.format(Instant.ofEpochMilli(item.image.createdAt))

            // Click to open full-screen view
            imageView.setOnClickListener {
                val context = itemView.context
                val intent = Intent(context, ImageViewerActivity::class.java).apply {
                    putExtra(ImageViewerActivity.EXTRA_IMAGE_PATH, item.image.filePath)
                    putExtra(ImageViewerActivity.EXTRA_IMAGE_TIMESTAMP, item.image.createdAt)
                }
                context.startActivity(intent)
            }
        }
    }

    class TimestampHeaderViewHolder(
        itemView: View,
        private val timeFormatter: DateTimeFormatter
    ) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.timestampText)

        fun bind(item: TimelineItem.TimestampHeader) {
            timestampText.text = timeFormatter.format(Instant.ofEpochMilli(item.timestamp))
        }
    }
}

/**
 * Sealed class representing items in the timeline.
 */
sealed class TimelineItem {
    abstract val timestamp: Long

    data class TextSegment(
        override val timestamp: Long,
        val text: String
    ) : TimelineItem()

    data class ImageItem(
        override val timestamp: Long,
        val image: JournalImage
    ) : TimelineItem()

    data class TimestampHeader(
        override val timestamp: Long
    ) : TimelineItem()
}

class TimelineDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
    override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
        return when {
            oldItem is TimelineItem.TextSegment && newItem is TimelineItem.TextSegment ->
                oldItem.timestamp == newItem.timestamp
            oldItem is TimelineItem.ImageItem && newItem is TimelineItem.ImageItem ->
                oldItem.image.id == newItem.image.id
            oldItem is TimelineItem.TimestampHeader && newItem is TimelineItem.TimestampHeader ->
                oldItem.timestamp == newItem.timestamp
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
        return oldItem == newItem
    }
}

/**
 * Helper to build timeline items from journal content and images.
 * Parses timestamp separators in text (like "--- 6:30 AM ---") and interleaves images.
 */
object TimelineBuilder {

    // Pattern to match timestamp separators: "\n\n--- 6:30 AM ---\n\n" or similar
    private val TIMESTAMP_SEPARATOR_REGEX = Regex("""\n*---\s*(\d{1,2}:\d{2}\s*(?:AM|PM))\s*---\n*""", RegexOption.IGNORE_CASE)

    /**
     * Build a list of timeline items from journal text and images.
     *
     * @param content The journal entry content (may contain timestamp separators)
     * @param images List of images with their timestamps
     * @param entryCreatedAt The timestamp when the entry was first created
     * @return List of timeline items sorted chronologically
     */
    fun buildTimeline(
        content: String,
        images: List<JournalImage>,
        entryCreatedAt: Long
    ): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()

        // Parse text into segments with timestamps
        val textSegments = parseTextWithTimestamps(content, entryCreatedAt)

        // Add text segments
        textSegments.forEach { (timestamp, text) ->
            if (text.isNotBlank()) {
                items.add(TimelineItem.TextSegment(timestamp, text.trim()))
            }
        }

        // Add images
        images.forEach { image ->
            items.add(TimelineItem.ImageItem(image.createdAt, image))
        }

        // Sort by timestamp
        items.sortBy { it.timestamp }

        return items
    }

    /**
     * Parse text content that may contain timestamp separators.
     * Returns a list of (timestamp, text) pairs.
     */
    private fun parseTextWithTimestamps(content: String, defaultTimestamp: Long): List<Pair<Long, String>> {
        val segments = mutableListOf<Pair<Long, String>>()

        // Find all timestamp separators
        val matches = TIMESTAMP_SEPARATOR_REGEX.findAll(content).toList()

        if (matches.isEmpty()) {
            // No separators, entire content is one segment
            return listOf(defaultTimestamp to content)
        }

        var lastEnd = 0
        var currentTimestamp = defaultTimestamp

        for (match in matches) {
            // Text before this separator
            val textBefore = content.substring(lastEnd, match.range.first)
            if (textBefore.isNotBlank()) {
                segments.add(currentTimestamp to textBefore)
            }

            // Parse the timestamp from the separator
            val timeString = match.groupValues[1]
            currentTimestamp = parseTimeString(timeString, defaultTimestamp)

            lastEnd = match.range.last + 1
        }

        // Text after last separator
        if (lastEnd < content.length) {
            val textAfter = content.substring(lastEnd)
            if (textAfter.isNotBlank()) {
                segments.add(currentTimestamp to textAfter)
            }
        }

        return segments
    }

    /**
     * Parse a time string like "6:30 AM" into a timestamp for today.
     */
    private fun parseTimeString(timeString: String, defaultTimestamp: Long): Long {
        return try {
            val formatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
            val time = java.time.LocalTime.parse(timeString.trim().uppercase(), formatter)

            // Use the date from the default timestamp
            val date = Instant.ofEpochMilli(defaultTimestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            date.atTime(time)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (e: Exception) {
            defaultTimestamp
        }
    }
}
