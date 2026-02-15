package com.owncloud.android.domain.files.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.files.FileRepository

class SetFileFavoriteStatusUseCase(
    private val fileRepository: FileRepository,
) : BaseUseCaseWithResult<Unit, SetFileFavoriteStatusUseCase.Params>() {

    override fun run(params: Params) {
        fileRepository.setFileFavoriteStatus(params.fileId, params.isFavorite)
    }

    data class Params(
        val fileId: Long,
        val isFavorite: Boolean,
    )
}
