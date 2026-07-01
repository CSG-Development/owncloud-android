package com.owncloud.android.presentation.trash

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import coil.dispose
import coil.load
import com.owncloud.android.R
import com.owncloud.android.databinding.GridItemBinding
import com.owncloud.android.databinding.ItemFileListBinding
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.files.model.LIST_MIME_DIR
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.presentation.thumbnails.ThumbnailsRequester
import com.owncloud.android.utils.MimetypeIconUtil
import com.owncloud.android.utils.PreferenceUtils
import kotlin.math.roundToInt

class TrashListAdapter(
    private val context: Context,
    private val layoutManager: StaggeredGridLayoutManager,
    private val listener: TrashListAdapterListener,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items: List<TrashItemUi> = emptyList()
    private val gridThumbnailSizePx = context.resources.getDimension(R.dimen.file_icon_size_grid).roundToInt()

    fun updateItems(newItems: List<TrashItemUi>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): TrashItemUi = items[position]

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when {
            layoutManager.spanCount == 1 -> VIEW_TYPE_LIST
            items[position].item.isImage -> VIEW_TYPE_GRID_IMAGE
            else -> VIEW_TYPE_GRID
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            VIEW_TYPE_LIST -> {
                val binding = ItemFileListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.filterTouchesWhenObscured =
                    PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
                ListViewHolder(binding)
            }

            VIEW_TYPE_GRID_IMAGE -> {
                val binding = GridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.filterTouchesWhenObscured =
                    PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
                GridImageViewHolder(binding)
            }

            else -> {
                val binding = GridItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                binding.root.filterTouchesWhenObscured =
                    PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
                GridViewHolder(binding)
            }
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val uiItem = items[position]
        val item = uiItem.item
        val daysLeft = TrashDateUtils.daysLeft(item.deletedTimestamp)

        when (holder) {
            is ListViewHolder -> bindListItem(holder, uiItem, item, position, daysLeft)
            is GridImageViewHolder -> bindGridImageItem(holder, uiItem, item, position)
            is GridViewHolder -> bindGridItem(holder, uiItem, item, position)
        }
    }

    private fun bindListItem(
        holder: ListViewHolder,
        uiItem: TrashItemUi,
        item: HCTrashItem,
        position: Int,
        daysLeft: Int?,
    ) {
        holder.binding.apply {
            thumbnail.bindThumbnail(item)
            localFileIndicator.isVisible = false
            Filename.text = item.originalFilename
            shareIconsLayout.isVisible = false
            fileListSize.isVisible = false
            fileListSeparator.isVisible = false
            spacePathLine.root.isVisible = false
            uploadProgressIndicator.isVisible = false
            threeDotMenu.isVisible = false

            if (daysLeft != null) {
                fileListLastMod.isVisible = true
                fileListLastMod.text = context.getString(R.string.trash_item_days_left, daysLeft)
            } else {
                fileListLastMod.isVisible = false
            }

            bindSelectionState(customCheckbox, uiItem.isSelected)
            fileListConstraintLayout.setOnClickListener { listener.onItemClick(position) }
            customCheckbox.setOnClickListener { listener.onItemClick(position) }
            updateSelectedBackground(fileListConstraintLayout, uiItem.isSelected)
        }
    }

    private fun bindGridItem(
        holder: GridViewHolder,
        uiItem: TrashItemUi,
        item: HCTrashItem,
        position: Int,
    ) {
        holder.binding.apply {
            resetGridThumbnailLayout(thumbnail)
            thumbnail.bindThumbnail(item)
            shareIconsLayout.isVisible = false
            localFileIndicator.isVisible = false
            uploadProgressIndicator.isVisible = false
            Filename.text = item.originalFilename

            bindSelectionState(customCheckbox, uiItem.isSelected)
            ListItemLayout.setOnClickListener { listener.onItemClick(position) }
            customCheckbox.setOnClickListener { listener.onItemClick(position) }
            updateSelectedBackground(ListItemLayout, uiItem.isSelected)
        }
    }

    private fun bindGridImageItem(
        holder: GridImageViewHolder,
        uiItem: TrashItemUi,
        item: HCTrashItem,
        position: Int,
    ) {
        holder.binding.apply {
            resetGridThumbnailLayout(thumbnail)
            thumbnail.bindThumbnail(item)
            Filename.text = item.originalFilename
            shareIconsLayout.isVisible = false
            localFileIndicator.isVisible = false
            uploadProgressIndicator.isVisible = false

            bindSelectionState(customCheckbox, uiItem.isSelected)
            ListItemLayout.setOnClickListener { listener.onItemClick(position) }
            customCheckbox.setOnClickListener { listener.onItemClick(position) }
            updateSelectedBackground(ListItemLayout, uiItem.isSelected)
        }
    }

    private fun ImageView.bindThumbnail(item: HCTrashItem) {
        val iconResId = item.getIconResId()

        if (tag != item.fileId) {
            dispose()
            tag = item.fileId
        }

        scaleType = ImageView.ScaleType.FIT_CENTER
        background = null

        if (!item.isImage) {
            setImageResource(iconResId)
            return
        }

        val cacheKey = TrashThumbnailLoader.cacheKey(item.fileId)
        val cachedThumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(cacheKey)
        if (cachedThumbnail != null) {
            setImageBitmap(cachedThumbnail)
            scaleType = ImageView.ScaleType.CENTER_CROP
            applyPngBackgroundIfNeeded(item)
            return
        }

        setImageResource(iconResId)

        val previewUri = ThumbnailsRequester.getPreviewUriForTrashItem(item, gridThumbnailSizePx) ?: return
        load(previewUri, ThumbnailsRequester.getCoilImageLoader()) {
            placeholder(iconResId)
            error(iconResId)
            memoryCacheKey(cacheKey)
            diskCacheKey(cacheKey)
            crossfade(false)
            listener(
                onSuccess = { _, result ->
                    if (tag != item.fileId) {
                        return@listener
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val bitmap = when (val drawable = result.drawable) {
                        is BitmapDrawable -> drawable.bitmap
                        else -> drawable.toBitmap()
                    }
                    ThumbnailsCacheManager.addBitmapToCache(cacheKey, bitmap)
                },
            )
        }

        applyPngBackgroundIfNeeded(item)
    }

    private fun ImageView.applyPngBackgroundIfNeeded(item: HCTrashItem) {
        if (item.mimeType?.equals("image/png", ignoreCase = true) == true) {
            setBackgroundColor(ContextCompat.getColor(context, R.color.background_color))
        }
    }

    private fun HCTrashItem.getIconResId(): Int {
        val serverMimeType = mimeType?.substringBefore(';')?.trim()
        val mimeType = if (serverMimeType != null && serverMimeType in LIST_MIME_DIR) {
            serverMimeType
        } else {
            MimetypeIconUtil.getBestMimeTypeByFilename(originalFilename)
        }
        return MimetypeIconUtil.getFileTypeIconId(mimeType, originalFilename)
    }

    private fun resetGridThumbnailLayout(thumbnail: ImageView) {
        val layoutParams = thumbnail.layoutParams as ViewGroup.MarginLayoutParams
        manageGridLayoutParams(
            layoutParams = layoutParams,
            marginVertical = 0,
            height = context.resources.getDimensionPixelSize(R.dimen.item_file_grid_height),
            width = context.resources.getDimensionPixelSize(R.dimen.item_file_grid_width),
        )
        thumbnail.layoutParams = layoutParams
    }

    private fun manageGridLayoutParams(
        layoutParams: ViewGroup.MarginLayoutParams,
        marginVertical: Int,
        height: Int,
        width: Int,
    ) {
        val marginHorizontal = context.resources.getDimensionPixelSize(R.dimen.item_file_image_grid_margin)
        layoutParams.setMargins(marginHorizontal, marginVertical, marginHorizontal, marginVertical)
        layoutParams.height = height
        layoutParams.width = width
    }

    private fun bindSelectionState(checkbox: ImageView, isSelected: Boolean) {
        checkbox.isVisible = true
        checkbox.setImageResource(
            if (isSelected) R.drawable.ic_checkbox_marked else R.drawable.ic_checkbox_blank_outline,
        )
    }

    private fun updateSelectedBackground(view: View, isSelected: Boolean) {
        view.isSelected = isSelected
        view.setBackgroundResource(R.drawable.list_selector)
    }

    private class ListViewHolder(val binding: ItemFileListBinding) : RecyclerView.ViewHolder(binding.root)

    private class GridViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root)

    private class GridImageViewHolder(val binding: GridItemBinding) : RecyclerView.ViewHolder(binding.root)

    interface TrashListAdapterListener {
        fun onItemClick(position: Int)
    }

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_GRID_IMAGE = 2
    }
}
