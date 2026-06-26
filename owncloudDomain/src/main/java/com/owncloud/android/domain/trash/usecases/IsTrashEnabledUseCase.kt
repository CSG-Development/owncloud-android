package com.owncloud.android.domain.trash.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.trash.TrashRepository

class IsTrashEnabledUseCase(
    private val trashRepository: TrashRepository,
) : BaseUseCaseWithResult<Boolean, IsTrashEnabledUseCase.Params>() {

    override fun run(params: Params): Boolean =
        trashRepository.isTrashEnabled(params.accountName)

    data class Params(val accountName: String)
}
