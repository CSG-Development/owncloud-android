package com.owncloud.android.presentation.files.globalsearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.databinding.FilterBottomSheetItemBinding

class FilterItemAdapter(
    private val allItems: List<FilterItem>,
    private val selectedIds: MutableSet<String>,
    private val isMultiSelect: Boolean,
    private val onItemClick: (FilterItem, Boolean) -> Unit
) : RecyclerView.Adapter<FilterItemAdapter.ViewHolder>() {

    private var displayedItems: List<FilterItem> = allItems

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun filter(query: String) {
        displayedItems = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { it.label.contains(query, ignoreCase = true) }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FilterBottomSheetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(displayedItems[position])
    }

    override fun getItemCount(): Int = displayedItems.size

    inner class ViewHolder(
        private val binding: FilterBottomSheetItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = displayedItems[position]
                if (isMultiSelect) {
                    val newState = !selectedIds.contains(item.id)
                    if (newState) {
                        selectedIds.add(item.id)
                    } else {
                        selectedIds.remove(item.id)
                    }
                    notifyItemChanged(position)
                    onItemClick(item, newState)
                } else {
                    val previousSelected = selectedIds.toList()
                    selectedIds.clear()
                    selectedIds.add(item.id)
                    previousSelected.forEach { prevId ->
                        val prevIndex = displayedItems.indexOfFirst { it.id == prevId }
                        if (prevIndex >= 0) notifyItemChanged(prevIndex)
                    }
                    notifyItemChanged(position)
                    onItemClick(item, true)
                }
            }
        }

        fun bind(item: FilterItem) {
            binding.filterItemTitle.text = item.label

            item.iconResId?.let { iconRes ->
                binding.filterItemIcon.setImageResource(iconRes)
                binding.filterItemIcon.visibility = View.VISIBLE
            } ?: run {
                binding.filterItemIcon.visibility = View.GONE
            }

            val isSelected = selectedIds.contains(item.id)

            if (isMultiSelect) {
                binding.filterItemCheckbox.visibility = View.VISIBLE
                binding.filterItemRadio.visibility = View.GONE
                binding.filterItemCheckbox.isChecked = isSelected
            } else {
                binding.filterItemCheckbox.visibility = View.GONE
                binding.filterItemRadio.visibility = View.VISIBLE
                binding.filterItemRadio.isChecked = isSelected
            }
        }
    }
}