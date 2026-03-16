package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class GetTagsForFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, GetTagsForFileUseCase.Params>() {

    override fun run(params: Params): List<OCTag> =
        tagRepository.getTagsForFile(params.fileId)

    data class Params(val fileId: Long)
}
