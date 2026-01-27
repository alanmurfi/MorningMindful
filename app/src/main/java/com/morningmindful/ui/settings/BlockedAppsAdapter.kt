package com.morningmindful.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.morningmindful.R

class BlockedAppsAdapter(
    private val onAppToggled: (packageName: String, isBlocked: Boolean) -> Unit
) : ListAdapter<BlockedAppItem, BlockedAppsAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val packageName: TextView = itemView.findViewById(R.id.packageName)
        private val notInstalledLabel: TextView = itemView.findViewById(R.id.notInstalledLabel)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)

        fun bind(item: BlockedAppItem) {
            appName.text = item.appName
            packageName.text = item.packageName
            notInstalledLabel.visibility = if (item.isInstalled) View.GONE else View.VISIBLE

            // Temporarily remove listener to prevent triggering during bind
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = item.isBlocked
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                onAppToggled(item.packageName, isChecked)
            }

            // Alpha for uninstalled apps
            itemView.alpha = if (item.isInstalled) 1f else 0.5f
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<BlockedAppItem>() {
        override fun areItemsTheSame(oldItem: BlockedAppItem, newItem: BlockedAppItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: BlockedAppItem, newItem: BlockedAppItem): Boolean {
            return oldItem == newItem
        }
    }
}
