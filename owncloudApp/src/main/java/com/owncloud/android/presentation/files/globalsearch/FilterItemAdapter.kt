package com.owncloud.android.presentation.files.globalsearch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.databinding.FilterBottomSheetItemBinding

class FilterItemAdapter(
    private val items: List<FilterItem>,
    private val selectedIds: MutableSet<String>,
    private val isMultiSelect: Boolean,
    private val onItemClick: (FilterItem, Boolean) -> Unit
) : RecyclerView.Adapter<FilterItemAdapter.ViewHolder>() {

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FilterBottomSheetItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: FilterBottomSheetItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

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

            binding.root.setOnClickListener {
                if (isMultiSelect) {
                    val newState = !selectedIds.contains(item.id)
                    if (newState) {
                        selectedIds.add(item.id)
                    } else {
                        selectedIds.remove(item.id)
                    }
                    notifyItemChanged(bindingAdapterPosition)
                    onItemClick(item, newState)
                } else {
                    val previousSelected = selectedIds.toList()
                    selectedIds.clear()
                    selectedIds.add(item.id)

                    previousSelected.forEach { prevId ->
                        val prevIndex = items.indexOfFirst { it.id == prevId }
                        if (prevIndex >= 0) {
                            notifyItemChanged(prevIndex)
                        }
                    }
                    notifyItemChanged(bindingAdapterPosition)
                    onItemClick(item, true)
                }
            }

            binding.filterItemCheckbox.setOnClickListener {
                binding.root.performClick()
            }
            binding.filterItemRadio.setOnClickListener {
                binding.root.performClick()
            }
        }
    }
}