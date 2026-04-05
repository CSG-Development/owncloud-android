package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class RemoveTagFromFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, RemoveTagFromFileUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.removeTagFromFile(
            accountName = params.accountName,
            fileLocalId = params.fileLocalId,
            fileRemoteId = params.fileRemoteId,
            tagId = params.tagId,
        )

    data class Params(
        val accountName: String,
        val fileLocalId: Long,
        val fileRemoteId: Long,
        val tagId: String,
    )
}
