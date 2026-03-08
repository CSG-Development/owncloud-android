package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.tags.TagRepository

class RemoveTagFromFileUseCase(
    private val tagRepository: TagRepository,
) : BaseUseCaseWithResult<Unit, RemoveTagFromFileUseCase.Params>() {

    override fun run(params: Params) =
        tagRepository.removeTagFromFile(params.fileId, params.tagId)

    data class Params(val fileId: Long, val tagId: Long)
}
