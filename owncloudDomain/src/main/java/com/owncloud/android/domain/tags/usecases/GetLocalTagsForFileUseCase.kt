package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class GetLocalTagsForFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, GetLocalTagsForFileUseCase.Params>() {

    override fun run(params: Params): List<OCTag> =
        tagRepository.getLocalTagsForFile(params.fileLocalId)

    data class Params(
        val fileLocalId: Long,
    )
}
