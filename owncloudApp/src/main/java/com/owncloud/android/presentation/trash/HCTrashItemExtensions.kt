package com.owncloud.android.presentation.trash

import com.owncloud.android.domain.files.model.LIST_MIME_DIR
import com.owncloud.android.domain.files.model.MIME_PREFIX_IMAGE
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.utils.MimetypeIconUtil

val HCTrashItem.isFolder: Boolean
    get() {
        val serverMimeType = mimeType?.substringBefore(';')?.trim()
        return serverMimeType != null && serverMimeType in LIST_MIME_DIR
    }

val HCTrashItem.isImage: Boolean
    get() {
        if (isFolder) {
            return false
        }
        val serverMimeType = mimeType?.substringBefore(';')?.trim()
        if (serverMimeType?.startsWith(MIME_PREFIX_IMAGE) == true) {
            return true
        }
        return MimetypeIconUtil.getBestMimeTypeByFilename(originalFilename).startsWith(MIME_PREFIX_IMAGE)
    }
