package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class CreateTagUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, CreateTagUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.createTag(params.accountName, params.name, params.userVisible, params.userAssignable)

    data class Params(
        val accountName: String,
        val name: String,
        val userVisible: Boolean = true,
        val userAssignable: Boolean = true,
    )
}
