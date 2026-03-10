package com.owncloud.android.presentation.tags

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.databinding.ItemTagBinding
import com.owncloud.android.domain.tags.model.OCTag

class TagsAdapter(
    private val listener: TagsAdapterListener,
) : ListAdapter<OCTag, TagsAdapter.TagViewHolder>(TagDiffCallback()) {

    interface TagsAdapterListener {
        fun onEditTag(tag: OCTag)
        fun onDeleteTag(tag: OCTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val binding = ItemTagBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TagViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TagViewHolder(
        private val binding: ItemTagBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(tag: OCTag) {
            binding.tagName.text = tag.displayName.orEmpty()
            binding.btnEditTag.setOnClickListener { listener.onEditTag(tag) }
            binding.btnDeleteTag.setOnClickListener { listener.onDeleteTag(tag) }
        }
    }

    private class TagDiffCallback : DiffUtil.ItemCallback<OCTag>() {
        override fun areItemsTheSame(oldItem: OCTag, newItem: OCTag): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: OCTag, newItem: OCTag): Boolean =
            oldItem == newItem
    }
}
