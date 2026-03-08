package com.owncloud.android.data.tags.datasources

import com.owncloud.android.domain.tags.model.OCTag

interface RemoteTagDataSource {
    fun getSystemTags(accountName: String): List<OCTag>
    fun getFileRemoteIdsByTag(accountName: String, tagId: String): List<String>
}
