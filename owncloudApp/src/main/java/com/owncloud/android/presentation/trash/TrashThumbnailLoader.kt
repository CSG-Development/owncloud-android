package com.owncloud.android.presentation.trash

object TrashThumbnailLoader {
    private const val CACHE_KEY_PREFIX = "trash-"

    fun cacheKey(fileId: String): String = "$CACHE_KEY_PREFIX$fileId"
}
