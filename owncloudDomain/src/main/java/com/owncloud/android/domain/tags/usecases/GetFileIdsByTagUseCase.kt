package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class GetFileIdsByTagUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<Long>, GetFileIdsByTagUseCase.Params>() {

    override fun run(params: Params): List<Long> =
        tagRepository.getFileIdsByTag(params.tagId)

    data class Params(val tagId: Long)
}
