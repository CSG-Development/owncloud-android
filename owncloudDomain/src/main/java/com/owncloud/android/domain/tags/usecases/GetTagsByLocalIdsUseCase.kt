package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag

class GetTagsByLocalIdsUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<List<OCTag>, GetTagsByLocalIdsUseCase.Params>() {

    override fun run(params: Params): List<OCTag> =
        tagRepository.getTagsByLocalIds(params.localIds)

    data class Params(val localIds: List<Long>)
}
