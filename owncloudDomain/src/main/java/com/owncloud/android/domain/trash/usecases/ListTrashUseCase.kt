package com.owncloud.android.domain.trash.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.trash.TrashRepository
import com.owncloud.android.domain.trash.model.HCTrashItem

class ListTrashUseCase(
    private val trashRepository: TrashRepository,
) : BaseUseCaseWithResult<List<HCTrashItem>, ListTrashUseCase.Params>() {

    override fun run(params: Params): List<HCTrashItem> =
        trashRepository.listTrash(params.accountName)

    data class Params(val accountName: String)
}
