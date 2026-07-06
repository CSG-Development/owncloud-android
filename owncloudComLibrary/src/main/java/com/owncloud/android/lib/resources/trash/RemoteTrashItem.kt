package com.owncloud.android.lib.resources.trash

data class RemoteTrashItem(
    val fileId: String,
    val trashDavPath: String,
    val originalFilename: String,
    val originalLocation: String,
    val deletedAt: String?,
    val deletedTimestamp: Long?,
    val contentLength: Long,
    val mimeType: String?,
    val lastModified: String?,
)
