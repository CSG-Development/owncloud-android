package com.owncloud.android.presentation.common

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.owncloud.android.R
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.extensions.toDrawableResId
import com.owncloud.android.extensions.toResId
import com.owncloud.android.extensions.toStringResId
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.files.filelist.MainFileListFragment
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperation.SetFileFavoriteStatus
import com.owncloud.android.presentation.files.operations.FileOperation.SetFilesAsAvailableOffline
import com.owncloud.android.presentation.files.operations.FileOperation.SynchronizeFileOperation
import com.owncloud.android.presentation.files.operations.FileOperation.SynchronizeFolderOperation
import com.owncloud.android.presentation.files.operations.FileOperation.UnsetFilesAsAvailableOffline
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.renamefile.RenameFileDialogFragment
import com.owncloud.android.presentation.files.renamefile.RenameFileDialogFragment.Companion.FRAGMENT_TAG_RENAME_FILE
import com.owncloud.android.presentation.tags.ManageTagsFragment
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.activity.FolderPickerActivity
import com.owncloud.android.utils.DisplayUtils
import com.owncloud.android.utils.MimetypeIconUtil

object FileOptionsBottomSheetHelper {

    fun show(
        fragment: Fragment,
        file: OCFile,
        menuOptions: List<FileMenuOption>,
        fileOperationsViewModel: FileOperationsViewModel,
        fileActions: MainFileListFragment.FileActions?,
        isMultiPersonal: Boolean = false,
        onRemoveSelected: ((OCFile) -> Unit)? = null,
    ) {
        val context = fragment.requireContext()
        val activity = fragment.requireActivity()
        val bottomSheetView = fragment.layoutInflater.inflate(R.layout.file_options_bottom_sheet_fragment, null)
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(bottomSheetView)
        val behavior = BottomSheetBehavior.from(bottomSheetView.parent as View)

        // Favorite button
        val favoriteButton = bottomSheetView.findViewById<ImageView>(R.id.favorite_bottom_sheet)
        var isFavorite = file.isFavorite
        favoriteButton.setImageResource(
            if (isFavorite) R.drawable.ic_star_filled_blue else R.drawable.ic_star_outlined_blue
        )
        favoriteButton.setOnClickListener {
            isFavorite = !isFavorite
            favoriteButton.setImageResource(
                if (isFavorite) R.drawable.ic_star_filled_blue else R.drawable.ic_star_outlined_blue
            )
            file.id?.let { fileId ->
                fileOperationsViewModel.performOperation(
                    FileOperation.SetFileFavoriteStatus(fileId = fileId, isFavorite = isFavorite)
                )
            }
        }

        // Thumbnail
        val thumbnailBottomSheet = bottomSheetView.findViewById<ImageView>(R.id.thumbnail_bottom_sheet)
        if (file.isFolder) {
            thumbnailBottomSheet.setImageResource(R.drawable.ic_homecloud_folder)
        } else {
            thumbnailBottomSheet.setImageResource(MimetypeIconUtil.getFileTypeIconId(file.mimeType, file.fileName))
            if (file.remoteId != null) {
                val thumbnail = ThumbnailsCacheManager.getBitmapFromDiskCache(file.remoteId)
                if (thumbnail != null) {
                    thumbnailBottomSheet.setImageBitmap(thumbnail)
                }
                if (file.needsToUpdateThumbnail && ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, thumbnailBottomSheet)) {
                    val task = ThumbnailsCacheManager.ThumbnailGenerationTask(
                        thumbnailBottomSheet,
                        AccountUtils.getCurrentOwnCloudAccount(context)
                    )
                    val asyncDrawable = ThumbnailsCacheManager.AsyncThumbnailDrawable(activity.resources, thumbnail, task)
                    if (asyncDrawable.minimumHeight > 0 && asyncDrawable.minimumWidth > 0) {
                        thumbnailBottomSheet.setImageDrawable(asyncDrawable)
                    }
                    task.execute(file)
                }
                if (file.mimeType == "image/png") {
                    thumbnailBottomSheet.setBackgroundColor(ContextCompat.getColor(context, R.color.background_color))
                }
            }
        }

        // File info
        val isFolderInKw = isMultiPersonal && file.isFolder
        bottomSheetView.findViewById<TextView>(R.id.file_name_bottom_sheet).text = file.fileName

        bottomSheetView.findViewById<TextView>(R.id.file_size_bottom_sheet).text =
            if (isFolderInKw) "" else DisplayUtils.bytesToHumanReadable(file.length, context, true)

        bottomSheetView.findViewById<TextView>(R.id.file_separator_bottom_sheet).visibility =
            if (isFolderInKw) View.GONE else View.VISIBLE

        val fileLastModBottomSheet = bottomSheetView.findViewById<TextView>(R.id.file_last_mod_bottom_sheet)
        fileLastModBottomSheet.text = DisplayUtils.getRelativeTimestamp(context, file.modificationTimestamp)
        fileLastModBottomSheet.layoutParams = (fileLastModBottomSheet.layoutParams as ViewGroup.MarginLayoutParams).also { params ->
            params.marginStart = if (isFolderInKw) 0 else
                context.resources.getDimensionPixelSize(R.dimen.standard_quarter_margin)
        }

        // Menu options
        val optionsLayout = bottomSheetView.findViewById<LinearLayout>(R.id.file_options_bottom_sheet_layout)
        menuOptions.forEach { menuOption ->
            val itemView = BottomSheetFragmentItemView(context)
            itemView.title = if (menuOption.toResId() == R.id.action_open_file_with && !file.hasWritePermission) {
                context.getString(R.string.actionbar_open_with_read_only)
            } else {
                context.getString(menuOption.toStringResId())
            }
            itemView.itemIcon = ResourcesCompat.getDrawable(activity.resources, menuOption.toDrawableResId(), null)
            itemView.setOnClickListener {
                if (menuOption == FileMenuOption.REMOVE) onRemoveSelected?.invoke(file)
                handleMenuOption(menuOption, file, fileOperationsViewModel, fileActions, fragment)
                dialog.hide()
                dialog.dismiss()
            }
            optionsLayout.addView(itemView)
        }

        dialog.setOnShowListener { behavior.peekHeight = bottomSheetView.measuredHeight }
        dialog.show()
    }

    private fun handleMenuOption(
        menuOption: FileMenuOption,
        file: OCFile,
        fileOperationsViewModel: FileOperationsViewModel,
        fileActions: MainFileListFragment.FileActions?,
        fragment: Fragment,
    ) {
        val activity = fragment.requireActivity()
        when (menuOption) {
            FileMenuOption.SELECT_ALL, FileMenuOption.SELECT_INVERSE -> { /* Not applicable in single-file context */
            }

            FileMenuOption.DOWNLOAD, FileMenuOption.SYNC -> {
                if (file.isFolder) {
                    fileOperationsViewModel.performOperation(
                        SynchronizeFolderOperation(
                            folderToSync = file,
                            accountName = file.owner,
                            isActionSetFolderAvailableOfflineOrSynchronize = true,
                        )
                    )
                } else {
                    fileOperationsViewModel.performOperation(SynchronizeFileOperation(file, file.owner))
                }
            }

            FileMenuOption.RENAME -> {
                RenameFileDialogFragment.newInstance(file)
                    .show(activity.supportFragmentManager, FRAGMENT_TAG_RENAME_FILE)
            }

            FileMenuOption.MOVE -> {
                val action = Intent(activity, FolderPickerActivity::class.java).apply {
                    putParcelableArrayListExtra(FolderPickerActivity.EXTRA_FILES, arrayListOf(file))
                    putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.MOVE)
                }
                activity.startActivityForResult(action, FileDisplayActivity.REQUEST_CODE__MOVE_FILES)
            }

            FileMenuOption.COPY -> {
                val action = Intent(activity, FolderPickerActivity::class.java).apply {
                    putParcelableArrayListExtra(FolderPickerActivity.EXTRA_FILES, arrayListOf(file))
                    putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.COPY)
                }
                activity.startActivityForResult(action, FileDisplayActivity.REQUEST_CODE__COPY_FILES)
            }

            FileMenuOption.REMOVE -> fileOperationsViewModel.showRemoveDialog(listOf(file))

            FileMenuOption.OPEN_WITH -> fileActions?.openFile(file)

            FileMenuOption.CANCEL_SYNC -> fileActions?.cancelFileTransference(listOf(file))

            FileMenuOption.SHARE -> fileActions?.onShareFileClicked(file)

            FileMenuOption.DETAILS -> fileActions?.showDetails(file)

            FileMenuOption.SEND -> {
                if (!file.isAvailableLocally) {
                    fileActions?.initDownloadForSending(file)
                } else {
                    fileActions?.sendDownloadedFile(file)
                }
            }

            FileMenuOption.SET_AV_OFFLINE -> {
                fileOperationsViewModel.performOperation(SetFilesAsAvailableOffline(listOf(file)))
                if (file.isFolder) {
                    fileOperationsViewModel.performOperation(
                        SynchronizeFolderOperation(
                            folderToSync = file,
                            accountName = file.owner,
                            isActionSetFolderAvailableOfflineOrSynchronize = true,
                        )
                    )
                } else {
                    fileOperationsViewModel.performOperation(SynchronizeFileOperation(file, file.owner))
                }
            }

            FileMenuOption.UNSET_AV_OFFLINE ->
                fileOperationsViewModel.performOperation(UnsetFilesAsAvailableOffline(listOf(file)))

            FileMenuOption.SET_FAVORITE ->
                file.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(SetFileFavoriteStatus(fileId, isFavorite = true))
                }

            FileMenuOption.UNSET_FAVORITE ->
                file.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(SetFileFavoriteStatus(fileId, isFavorite = false))
                }

            FileMenuOption.MANAGE_TAGS -> {
                if (activity is FileDisplayActivity) {
                    activity.setSecondFragment(ManageTagsFragment.newInstance(file))
                }
            }
        }
    }
}
