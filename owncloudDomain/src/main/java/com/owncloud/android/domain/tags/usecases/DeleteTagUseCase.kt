package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class DeleteTagUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, DeleteTagUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.deleteTag(params.accountName, params.tagId)

    data class Params(
        val accountName: String,
        val tagId: String,
    )
}
