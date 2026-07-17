/**
 * ownCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * @author Juan Carlos Garrote Gascón
 * @author Manuel Plazas Palacio
 * @author Aitor Ballesteros Pavón
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2025 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.presentation.files.filelist

import android.accounts.Account
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.OCFooterFile
import com.owncloud.android.domain.files.model.isUploadVirtualFile
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.files.filelist.compose.FileGridItem
import com.owncloud.android.presentation.files.filelist.compose.FileListFooter
import com.owncloud.android.presentation.files.filelist.compose.FileListRow
import com.owncloud.android.presentation.files.filelist.compose.rememberFileListThumbnail
import com.owncloud.android.presentation.files.filelist.compose.toFileListItemUiModel
import com.owncloud.android.utils.PreferenceUtils

class FileListAdapter(
    private val context: Context,
    private val isPickerMode: Boolean,
    private val layoutManager: StaggeredGridLayoutManager,
    private val listener: FileListAdapterListener,
    private val isMultiPersonal: Boolean,
) : SelectableAdapter<RecyclerView.ViewHolder>() {

    private var files = mutableListOf<Any>()
    private var account: Account? = AccountUtils.getCurrentOwnCloudAccount(context)
    private var fileListOption: FileListOption = FileListOption.ALL_FILES

    fun updateFileList(filesToAdd: List<OCFileWithSyncInfo>, fileListOption: FileListOption, onSortChanged: () -> Unit) {

        val listWithFooter = mutableListOf<Any>()
        listWithFooter.addAll(filesToAdd)

        if (listWithFooter.isNotEmpty()) {
            listWithFooter.add(OCFooterFile(manageListOfFilesAndGenerateText(filesToAdd)))
        }

        val diffUtilCallback = FileListDiffCallback(
            oldList = files.toList(),
            newList = listWithFooter,
            oldFileListOption = this.fileListOption,
            newFileListOption = fileListOption,
        )
        val diffResult = DiffUtil.calculateDiff(diffUtilCallback)

        files.clear()
        files.addAll(listWithFooter)
        this.fileListOption = fileListOption

        diffResult.dispatchUpdatesTo(this)
        if (diffUtilCallback.isOnlySortOrderChanged()) {
            onSortChanged()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        when (viewType) {
            ViewType.LIST_ITEM.ordinal -> {
                ListComposeViewHolder(createItemComposeView(parent, ViewType.LIST_ITEM))
            }

            ViewType.GRID_IMAGE.ordinal -> {
                GridComposeViewHolder(createItemComposeView(parent, ViewType.GRID_IMAGE))
            }

            ViewType.GRID_ITEM.ordinal -> {
                GridComposeViewHolder(createItemComposeView(parent, ViewType.GRID_ITEM))
            }

            else -> {
                FooterComposeViewHolder(createItemComposeView(parent, ViewType.FOOTER))
            }
        }

    private fun createItemComposeView(parent: ViewGroup, viewType: ViewType): ComposeView =
        ComposeView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            tag = viewType
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        }

    override fun getItemCount(): Int = files.size

    override fun getItemId(position: Int): Long = position.toLong()

    private fun isFooter(position: Int) = files[position] is OCFooterFile

    override fun getItemViewType(position: Int): Int =

        if (isFooter(position)) {
            ViewType.FOOTER.ordinal
        } else {
            when {
                layoutManager.spanCount == 1 -> {
                    ViewType.LIST_ITEM.ordinal
                }

                (files[position] as OCFileWithSyncInfo).file.isImage -> {
                    ViewType.GRID_IMAGE.ordinal
                }

                else -> {
                    ViewType.GRID_ITEM.ordinal
                }
            }
        }

    fun getCheckedItems(): List<OCFileWithSyncInfo> {
        val checkedItems = mutableListOf<OCFileWithSyncInfo>()
        val checkedPositions = getSelectedItems()

        for (i in checkedPositions) {
            val checkedFile: Any? = files.getOrNull(i)
            if (checkedFile is OCFileWithSyncInfo) {
                checkedItems.add(checkedFile)
            }
        }

        return checkedItems
    }

    fun selectAll() {
        // Last item on list is the footer, so that element must be excluded from selection
        selectAll(totalItems = files.size - 1)
    }

    fun selectInverse() {
        // Last item on list is the footer, so that element must be excluded from selection
        toggleSelectionInBulk(totalItems = files.size - 1)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads[0] is VirtualFileProgressPayload) {
            val payload = payloads[0] as VirtualFileProgressPayload
            bindUploadProgress(holder, payload.progress, payload.isIndeterminate)
            return
        }
        onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val viewType = getItemViewType(position)

        if (viewType == ViewType.FOOTER.ordinal) {
            bindFooterComposeItem(holder as FooterComposeViewHolder, position)
            return
        }

        val fileWithSyncInfo = files[position] as OCFileWithSyncInfo

        when (viewType) {
            ViewType.LIST_ITEM.ordinal -> {
                bindListComposeItem(holder as ListComposeViewHolder, fileWithSyncInfo, position)
            }
            ViewType.GRID_IMAGE.ordinal -> {
                bindGridComposeItem(
                    holder = holder as GridComposeViewHolder,
                    fileWithSyncInfo = fileWithSyncInfo,
                    position = position,
                    isImageMode = true,
                )
            }
            else -> {
                bindGridComposeItem(
                    holder = holder as GridComposeViewHolder,
                    fileWithSyncInfo = fileWithSyncInfo,
                    position = position,
                    isImageMode = false,
                )
            }
        }
    }

    private fun bindListComposeItem(
        holder: ListComposeViewHolder,
        fileWithSyncInfo: OCFileWithSyncInfo,
        position: Int,
    ) {
        val file = fileWithSyncInfo.file
        val isVirtual = file.isVirtualFile()
        val selectionModeActive = getCheckedItems().isNotEmpty()
        val showThreeDotMenu = !selectionModeActive && !fileListOption.isFavorites()
        val showSpacePath = fileListOption.isAvailableOffline() ||
                fileListOption.isFavorites() ||
                (fileListOption.isSharedByLink() && fileWithSyncInfo.space == null)

        val item = fileWithSyncInfo.toFileListItemUiModel(
            isSelected = isSelected(position),
            selectionModeActive = selectionModeActive,
            showThreeDotMenu = showThreeDotMenu,
            showSpacePath = showSpacePath,
            isMultiPersonal = isMultiPersonal,
        )

        holder.composeView.apply {
            contentDescription = "LinearLayout-${file.fileName}"
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        }

        holder.composeView.setContent {
            HomeCloudTheme {
                val thumbnail = rememberFileListThumbnail(
                    file = file.takeUnless { isVirtual || file.isFolder },
                    account = account,
                )
                FileListRow(
                    item = item,
                    thumbnail = thumbnail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
                    onClick = {
                        if (isVirtual) {
                            if (file.isUploadVirtualFile()) {
                                listener.onVirtualFileClick(fileWithSyncInfo, holder.itemView)
                            }
                        } else {
                            listener.onItemClick(
                                ocFileWithSyncInfo = fileWithSyncInfo,
                                position = position,
                            )
                        }
                    },
                    onLongClick = {
                        if (!isVirtual) {
                            listener.onLongItemClick(position = position)
                        }
                    },
                    onThreeDotClick = {
                        listener.onThreeDotButtonClick(fileWithSyncInfo = fileWithSyncInfo)
                    },
                )
            }
        }
    }

    private fun bindGridComposeItem(
        holder: GridComposeViewHolder,
        fileWithSyncInfo: OCFileWithSyncInfo,
        position: Int,
        isImageMode: Boolean,
    ) {
        val file = fileWithSyncInfo.file
        val isVirtual = file.isVirtualFile()
        val selectionModeActive = getCheckedItems().isNotEmpty()
        val showSpacePath = fileListOption.isAvailableOffline() ||
                fileListOption.isFavorites() ||
                (fileListOption.isSharedByLink() && fileWithSyncInfo.space == null)

        val item = fileWithSyncInfo.toFileListItemUiModel(
            isSelected = isSelected(position),
            selectionModeActive = selectionModeActive,
            showThreeDotMenu = false,
            showSpacePath = showSpacePath,
            isMultiPersonal = isMultiPersonal,
        )

        holder.composeView.apply {
            contentDescription = "LinearLayout-${file.fileName}"
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        }

        holder.composeView.setContent {
            HomeCloudTheme {
                val thumbnail = rememberFileListThumbnail(
                    file = file.takeUnless { isVirtual || file.isFolder },
                    account = account,
                )
                FileGridItem(
                    item = item,
                    thumbnail = thumbnail,
                    expandedThumbnail = isImageMode && thumbnail != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (isVirtual) {
                            if (file.isUploadVirtualFile()) {
                                listener.onVirtualFileClick(fileWithSyncInfo, holder.itemView)
                            }
                        } else {
                            listener.onItemClick(
                                ocFileWithSyncInfo = fileWithSyncInfo,
                                position = position,
                            )
                        }
                    },
                    onLongClick = {
                        if (!isVirtual) {
                            listener.onLongItemClick(position = position)
                        }
                    },
                )
            }
        }
    }

    private fun bindFooterComposeItem(
        holder: FooterComposeViewHolder,
        position: Int,
    ) {
        (holder.itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.apply {
            isFullSpan = true
        }
        if (isPickerMode) {
            return
        }
        val footer = files[position] as OCFooterFile
        holder.composeView.filterTouchesWhenObscured =
            PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        holder.composeView.setContent {
            HomeCloudTheme {
                FileListFooter(
                    text = footer.text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private fun bindUploadProgress(
        holder: RecyclerView.ViewHolder,
        progress: Int,
        isIndeterminate: Boolean = false,
    ) {
        // Progress payload rebinding for Compose items is handled in Step 9.
    }


    private fun manageListOfFilesAndGenerateText(list: List<OCFileWithSyncInfo>): String {
        var filesCount = 0
        var foldersCount = 0
        for (fileWithSyncInfo in list) {
            if (fileWithSyncInfo.file.isFolder) {
                foldersCount++
            } else {
                if (!fileWithSyncInfo.file.isHidden) {
                    filesCount++
                }
            }
        }

        return generateFooterText(filesCount, foldersCount)
    }


    private fun generateFooterText(filesCount: Int, foldersCount: Int): String =
        when {
            filesCount <= 0 -> {
                when {
                    foldersCount <= 0 -> {
                        ""
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__folder)
                    }

                    else -> { // foldersCount > 1
                        context.getString(R.string.file_list__footer__folders, foldersCount)
                    }
                }
            }

            filesCount == 1 -> {
                when {
                    foldersCount <= 0 -> {
                        context.getString(R.string.file_list__footer__file)
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__file_and_folder)
                    }

                    else -> { // foldersCount > 1
                        context.getString(R.string.file_list__footer__file_and_folders, foldersCount)
                    }
                }
            }

            else -> {    // filesCount > 1
                when {
                    foldersCount <= 0 -> {
                        context.getString(R.string.file_list__footer__files, filesCount)
                    }

                    foldersCount == 1 -> {
                        context.getString(R.string.file_list__footer__files_and_folder, filesCount)
                    }

                    else -> { // foldersCount > 1
                        context.getString(
                            R.string.file_list__footer__files_and_folders, filesCount, foldersCount
                        )
                    }
                }
            }
        }

    interface FileListAdapterListener {
        fun onItemClick(ocFileWithSyncInfo: OCFileWithSyncInfo, position: Int)
        fun onLongItemClick(position: Int): Boolean = true
        fun onThreeDotButtonClick(fileWithSyncInfo: OCFileWithSyncInfo)
        fun onVirtualFileClick(fileWithSyncInfo: OCFileWithSyncInfo, anchorView: View) {}
    }

    inner class ListComposeViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)
    inner class GridComposeViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)
    inner class FooterComposeViewHolder(val composeView: ComposeView) : RecyclerView.ViewHolder(composeView)

    enum class ViewType {
        LIST_ITEM, GRID_IMAGE, GRID_ITEM, FOOTER
    }
}
