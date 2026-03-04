package com.owncloud.android.domain.tags

import com.owncloud.android.domain.tags.model.OCTag

interface TagRepository {
    fun getTagsForAccount(accountName: String): List<OCTag>
}
