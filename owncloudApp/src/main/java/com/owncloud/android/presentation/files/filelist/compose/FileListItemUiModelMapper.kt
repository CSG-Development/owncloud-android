package com.owncloud.android.presentation.files.filelist.compose

import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isUploadVirtualFile
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.utils.MimetypeIconUtil

fun OCFileWithSyncInfo.toFileListItemUiModel(
    isSelected: Boolean = false,
    selectionModeActive: Boolean = false,
    showThreeDotMenu: Boolean = true,
    showSpacePath: Boolean = false,
    isMultiPersonal: Boolean = false,
): FileListItemUiModel {
    val file = file
    val fileId = file.id ?: 0L
    val isVirtual = file.isVirtualFile()
    val isFolder = file.isFolder
    val hideSizeAndSeparator = isMultiPersonal && isFolder

    val virtualKind = if (file.isUploadVirtualFile()) {
        FileListVirtualKind.Upload
    } else {
        FileListVirtualKind.None
    }

    val localPin = when {
        isVirtual -> FileListLocalPin.None
        isSynchronizing -> FileListLocalPin.Syncing
        file.etagInConflict != null -> FileListLocalPin.Conflict
        file.isAvailableOffline -> FileListLocalPin.AvailableOffline
        file.isAvailableLocally -> FileListLocalPin.Downloaded
        else -> FileListLocalPin.None
    }

    val spacePath = if (showSpacePath && !isVirtual) {
        val space = space
        val showPersonalLabel = space?.isPersonal == true && !isMultiPersonal
        FileListSpacePathUiModel(
            parentPath = file.getParentRemotePath(),
            spaceName = if (showPersonalLabel) null else space?.name,
            showPersonalLabel = showPersonalLabel,
        )
    } else {
        null
    }

    return FileListItemUiModel(
        fileId = fileId,
        name = file.fileName,
        length = file.length,
        modificationTimestamp = file.modificationTimestamp,
        isFolder = isFolder,
        isImage = file.isImage,
        mimeType = file.mimeType,
        mimeIconRes = if (isFolder) {
            R.drawable.ic_homecloud_folder
        } else {
            MimetypeIconUtil.getFileTypeIconId(file.mimeType, file.fileName)
        },
        thumbnailRemoteId = if (isVirtual || isFolder) null else file.remoteId,
        needsThumbnail = !isVirtual && !isFolder && file.needsToUpdateThumbnail,
        localPin = localPin,
        sharedByLink = !isVirtual && file.sharedByLink,
        sharedWithUsers = !isVirtual && (file.sharedWithSharee == true || file.isSharedWithMe),
        isSelected = isSelected,
        showCheckbox = !isVirtual && selectionModeActive,
        showThreeDotMenu = !isVirtual && showThreeDotMenu,
        virtualKind = virtualKind,
        uploadProgress = if (isVirtual) uploadProgress else null,
        isProgressIndeterminate = isVirtual && isProgressIndeterminate,
        spacePath = spacePath,
        hideSizeAndSeparator = hideSizeAndSeparator,
    )
}
