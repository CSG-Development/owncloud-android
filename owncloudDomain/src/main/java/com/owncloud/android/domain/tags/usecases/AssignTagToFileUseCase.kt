package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class AssignTagToFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, AssignTagToFileUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.assignTagToFile(params.accountName, params.fileLocalId, params.fileRemoteId, params.tagId)

    data class Params(
        val accountName: String,
        val fileLocalId: Long,
        val fileRemoteId: Long,
        val tagId: String,
    )
}
