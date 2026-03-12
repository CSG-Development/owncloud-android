package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class UpdateTagUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, UpdateTagUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.updateTag(params.accountName, params.tagId, params.displayName, params.userVisible, params.userAssignable)

    data class Params(
        val accountName: String,
        val tagId: String,
        val displayName: String? = null,
        val userVisible: Boolean? = null,
        val userAssignable: Boolean? = null,
    )
}
