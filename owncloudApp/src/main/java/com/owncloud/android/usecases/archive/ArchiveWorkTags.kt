package com.owncloud.android.usecases.archive

object ArchiveWorkTags {

    const val DISPLAY_NAME_PREFIX = "archive_file:"
    const val PARENT_PREFIX = "archive_parent:"
    const val REMOTE_PATH_PREFIX = "archive_remote:"
    const val ITEM_COUNT_PREFIX = "archive_items:"

    fun displayNameTag(displayName: String): String = "$DISPLAY_NAME_PREFIX$displayName"

    fun parentTag(parentFolderId: Long): String = "$PARENT_PREFIX$parentFolderId"

    fun remotePathTag(remotePath: String): String = "$REMOTE_PATH_PREFIX$remotePath"

    fun itemCountTag(itemCount: Int): String = "$ITEM_COUNT_PREFIX$itemCount"

    fun parseDisplayName(tags: Set<String>): String? =
        tags.firstOrNull { it.startsWith(DISPLAY_NAME_PREFIX) }
            ?.removePrefix(DISPLAY_NAME_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun parseParentFolderId(tags: Set<String>): Long? =
        tags.firstOrNull { it.startsWith(PARENT_PREFIX) }
            ?.removePrefix(PARENT_PREFIX)
            ?.toLongOrNull()

    fun parseRemotePath(tags: Set<String>): String? =
        tags.firstOrNull { it.startsWith(REMOTE_PATH_PREFIX) }
            ?.removePrefix(REMOTE_PATH_PREFIX)
            ?.takeIf { it.isNotEmpty() }

    fun parseItemCount(tags: Set<String>): Int? =
        tags.firstOrNull { it.startsWith(ITEM_COUNT_PREFIX) }
            ?.removePrefix(ITEM_COUNT_PREFIX)
            ?.toIntOrNull()

    fun parseSourceFileIds(tags: Set<String>, parentFolderId: Long): List<Long> =
        tags.mapNotNull { it.toLongOrNull() }
            .filter { it != parentFolderId }

    fun parseZipFileId(tags: Set<String>, parentFolderId: Long): Long? =
        parseSourceFileIds(tags, parentFolderId).singleOrNull()
}
