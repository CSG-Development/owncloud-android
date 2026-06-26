package com.owncloud.android.domain.trash.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.trash.TrashRepository

class DeleteTrashItemUseCase(
    private val trashRepository: TrashRepository,
) : BaseUseCaseWithResult<Unit, DeleteTrashItemUseCase.Params>() {

    override fun run(params: Params) {
        trashRepository.deleteTrashItemPermanently(
            accountName = params.accountName,
            fileId = params.fileId,
        )
    }

    data class Params(
        val accountName: String,
        val fileId: String,
    )
}
