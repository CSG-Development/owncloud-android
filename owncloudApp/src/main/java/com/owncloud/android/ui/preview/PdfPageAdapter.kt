package com.owncloud.android.ui.preview

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.owncloud.android.R

class PdfPageAdapter : ListAdapter<PdfPageUiModel, PdfPageAdapter.PdfPageViewHolder>(PdfPageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfPageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.preview_pdf_page_item, parent, false)
        return PdfPageViewHolder(view as ViewGroup)
    }

    override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PdfPageViewHolder(
        private val container: ViewGroup,
    ) : RecyclerView.ViewHolder(container) {

        private val pageContent: ViewGroup = container.findViewById(R.id.pdf_page_content)
        private val pageImage: ImageView = container.findViewById(R.id.pdf_page_image)
        private val pageLoading: ProgressBar = container.findViewById(R.id.pdf_page_loading)

        fun bind(model: PdfPageUiModel) {
            applyPageSize(model.displayWidthPx, model.displayHeightPx)
            when (val content = model.content) {
                is PdfPageContent.Loading -> {
                    val hasPlaceholder = pageImage.drawable != null
                    if (!hasPlaceholder) {
                        pageImage.setImageDrawable(null)
                    }
                    pageImage.contentDescription = container.context.getString(R.string.homecloud_pdf_preview_page_description)
                    pageLoading.isVisible = !hasPlaceholder
                }

                is PdfPageContent.Rendered -> {
                    if (content.bitmap.isRecycled) {
                        pageImage.setImageDrawable(null)
                        pageLoading.isVisible = true
                    } else {
                        pageImage.setImageBitmap(content.bitmap)
                        pageImage.contentDescription =
                            container.context.getString(R.string.homecloud_pdf_preview_page_description)
                        pageLoading.isVisible = false
                    }
                }

                is PdfPageContent.Failed -> {
                    pageImage.setImageDrawable(null)
                    pageImage.contentDescription = container.context.getString(R.string.homecloud_pdf_preview_page_failed)
                    pageLoading.isVisible = false
                }
            }
        }

        private fun applyPageSize(displayWidthPx: Int, displayHeightPx: Int) {
            if (displayWidthPx <= 0 || displayHeightPx <= 0) {
                return
            }
            // The item always spans the full viewport width while its height matches the
            // zoomed page height. The page content keeps the zoomed dimensions and may be
            // wider than the item, in which case PdfViewer translates it for centering/panning.
            container.layoutParams = container.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = displayHeightPx
            }
            pageContent.layoutParams = pageContent.layoutParams.apply {
                width = displayWidthPx
                height = displayHeightPx
            }
            pageImage.layoutParams = pageImage.layoutParams.apply {
                width = displayWidthPx
                height = displayHeightPx
            }
        }
    }

    private class PdfPageDiffCallback : DiffUtil.ItemCallback<PdfPageUiModel>() {
        override fun areItemsTheSame(oldItem: PdfPageUiModel, newItem: PdfPageUiModel): Boolean =
            oldItem.pageIndex == newItem.pageIndex

        override fun areContentsTheSame(oldItem: PdfPageUiModel, newItem: PdfPageUiModel): Boolean {
            if (oldItem.displayWidthPx != newItem.displayWidthPx || oldItem.displayHeightPx != newItem.displayHeightPx) {
                return false
            }
            return when {
                oldItem.content is PdfPageContent.Loading && newItem.content is PdfPageContent.Loading -> true
                oldItem.content is PdfPageContent.Failed && newItem.content is PdfPageContent.Failed -> true
                oldItem.content is PdfPageContent.Rendered && newItem.content is PdfPageContent.Rendered ->
                    oldItem.content.bitmap == newItem.content.bitmap

                else -> false
            }
        }
    }
}
