package com.owncloud.android.presentation.files.filelist

import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isUploadVirtualFile

internal fun OCFileWithSyncInfo.equalsForFileListDiff(other: OCFileWithSyncInfo): Boolean {
    if (!file.isUploadVirtualFile()) {
        return this == other
    }
    return copy(
        file = file.copy(modificationTimestamp = other.file.modificationTimestamp),
    ) == other
}

internal fun OCFileWithSyncInfo.isUploadProgressOnlyChange(other: OCFileWithSyncInfo): Boolean {
    if (!file.isUploadVirtualFile() || !other.file.isUploadVirtualFile()) return false
    if (file.id != other.file.id) return false
    if (uploadProgress == other.uploadProgress) return false
    return copy(uploadProgress = other.uploadProgress).equalsForFileListDiff(other)
}
