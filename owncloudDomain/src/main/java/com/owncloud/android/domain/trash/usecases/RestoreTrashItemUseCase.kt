package com.owncloud.android.domain.trash.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.trash.TrashRepository

class RestoreTrashItemUseCase(
    private val trashRepository: TrashRepository,
) : BaseUseCaseWithResult<Unit, RestoreTrashItemUseCase.Params>() {

    override fun run(params: Params) {
        trashRepository.restoreTrashItem(
            accountName = params.accountName,
            fileId = params.fileId,
            originalLocation = params.originalLocation,
            forceOverride = params.forceOverride,
        )
    }

    data class Params(
        val accountName: String,
        val fileId: String,
        val originalLocation: String,
        val forceOverride: Boolean = false,
    )
}
