package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class GetTagsForAccountUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, GetTagsForAccountUseCase.Params>() {

    override fun run(params: Params): List<OCTag> {
        val localTags = tagRepository.getLocalTagsForAccount(params.accountName)
        if (localTags.isNotEmpty()) return localTags
        return tagRepository.refreshTagsForAccount(params.accountName)
    }

    data class Params(val accountName: String)
}
