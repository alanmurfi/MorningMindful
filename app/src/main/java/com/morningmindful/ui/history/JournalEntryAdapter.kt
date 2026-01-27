package com.morningmindful.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.morningmindful.data.entity.JournalEntry
import com.morningmindful.databinding.ItemJournalEntryBinding
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class JournalEntryAdapter(
    private val onEntryClick: (JournalEntry) -> Unit
) : ListAdapter<JournalEntry, JournalEntryAdapter.EntryViewHolder>(EntryDiffCallback()) {

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        val binding = ItemJournalEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return EntryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EntryViewHolder(
        private val binding: ItemJournalEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onEntryClick(getItem(position))
                }
            }
        }

        fun bind(entry: JournalEntry) {
            binding.entryDate.text = entry.date.format(dateFormatter)
            binding.entryPreview.text = entry.content
            binding.entryWordCount.text = "${entry.wordCount} words"
            binding.entryMood.text = entry.mood ?: ""
            binding.entryMood.visibility = if (entry.mood != null) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }
    }

    private class EntryDiffCallback : DiffUtil.ItemCallback<JournalEntry>() {
        override fun areItemsTheSame(oldItem: JournalEntry, newItem: JournalEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: JournalEntry, newItem: JournalEntry): Boolean {
            return oldItem == newItem
        }
    }
}
