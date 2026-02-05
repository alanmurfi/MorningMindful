package com.morningmindful.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.morningmindful.data.entity.JournalImage
import com.morningmindful.databinding.ItemJournalImageViewBinding
import java.io.File

/**
 * Adapter for displaying journal images in a view-only mode (no delete button)
 */
class JournalImagesViewAdapter(
    private val imagesDir: File,
    private val onImageClick: ((JournalImage) -> Unit)? = null
) : ListAdapter<JournalImage, JournalImagesViewAdapter.ImageViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemJournalImageViewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ImageViewHolder(
        private val binding: ItemJournalImageViewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(image: JournalImage) {
            val imageFile = File(imagesDir, image.filePath)

            Glide.with(binding.imageView)
                .load(imageFile)
                .centerCrop()
                .into(binding.imageView)

            binding.root.setOnClickListener {
                onImageClick?.invoke(image)
            }
        }
    }

    class ImageDiffCallback : DiffUtil.ItemCallback<JournalImage>() {
        override fun areItemsTheSame(oldItem: JournalImage, newItem: JournalImage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JournalImage, newItem: JournalImage): Boolean {
            return oldItem == newItem
        }
    }
}
