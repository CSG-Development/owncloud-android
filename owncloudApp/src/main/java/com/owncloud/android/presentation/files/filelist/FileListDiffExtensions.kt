package com.owncloud.android.presentation.files.filelist

import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isVirtualFile

internal fun OCFileWithSyncInfo.equalsForFileListDiff(other: OCFileWithSyncInfo): Boolean {
    if (!file.isVirtualFile()) {
        return this == other
    }
    return copy(
        file = file.copy(modificationTimestamp = other.file.modificationTimestamp),
    ) == other
}

internal fun OCFileWithSyncInfo.isVirtualProgressOnlyChange(other: OCFileWithSyncInfo): Boolean {
    if (!file.isVirtualFile() || !other.file.isVirtualFile()) return false
    if (file.id != other.file.id) return false
    if (uploadProgress == other.uploadProgress && isProgressIndeterminate == other.isProgressIndeterminate) return false
    return copy(
        uploadProgress = other.uploadProgress,
        isProgressIndeterminate = other.isProgressIndeterminate,
    ).equalsForFileListDiff(other)
}
