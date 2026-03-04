package com.owncloud.android.data.tags.repository

import com.owncloud.android.data.tags.datasources.RemoteTagDataSource
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class OCTagRepository(
    private val remoteTagDataSource: RemoteTagDataSource,
) : TagRepository {

    override fun getTagsForAccount(accountName: String): List<OCTag> =
        remoteTagDataSource.getSystemTags(accountName)
}
