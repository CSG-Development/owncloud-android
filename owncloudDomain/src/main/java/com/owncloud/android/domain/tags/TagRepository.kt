package com.owncloud.android.domain.tags

import com.owncloud.android.domain.tags.model.OCTag

interface TagRepository {
    fun getTagsForAccount(accountName: String): List<OCTag>
    fun getTagsForFile(fileId: Long): List<OCTag>
    fun getFileIdsByTag(tagId: Long): List<Long>
    fun assignTagToFile(fileId: Long, tagId: Long)
    fun removeTagFromFile(fileId: Long, tagId: Long)
    fun refreshTagsForAccount(accountName: String): List<OCTag>
    fun refreshFilesByTag(accountName: String, serverTagId: String): List<String>
}
