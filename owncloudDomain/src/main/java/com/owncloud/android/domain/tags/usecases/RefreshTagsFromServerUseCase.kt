package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class RefreshTagsFromServerUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, RefreshTagsFromServerUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.refreshTagsForAccount(params.accountName)

    data class Params(val accountName: String)
}
