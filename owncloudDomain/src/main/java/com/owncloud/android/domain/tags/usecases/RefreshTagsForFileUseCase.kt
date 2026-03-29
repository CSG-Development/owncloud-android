package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class RefreshTagsForFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, RefreshTagsForFileUseCase.Params>() {

    override fun run(params: Params): List<OCTag> =
        tagRepository.refreshTagsForFile(
            accountName = params.accountName,
            fileRemoteId = params.fileRemoteId,
            fileLocalId = params.fileLocalId,
        )

    data class Params(
        val accountName: String,
        val fileRemoteId: Long,
        val fileLocalId: Long,
    )
}
