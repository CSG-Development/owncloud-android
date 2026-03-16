package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class RefreshTagsForAccountUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, RefreshTagsForAccountUseCase.Params>() {

    override fun run(params: Params): List<OCTag> =
        tagRepository.refreshTagsForAccount(params.accountName)

    data class Params(val accountName: String)
}
