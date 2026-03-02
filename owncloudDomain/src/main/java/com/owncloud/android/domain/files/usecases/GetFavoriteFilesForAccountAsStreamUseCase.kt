package com.owncloud.android.domain.files.usecases

import com.owncloud.android.domain.BaseUseCase
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import kotlinx.coroutines.flow.Flow

class GetFavoriteFilesForAccountAsStreamUseCase(
    private val fileRepository: FileRepository
) : BaseUseCase<Flow<List<OCFileWithSyncInfo>>, GetFavoriteFilesForAccountAsStreamUseCase.Params>() {

    override fun run(params: Params): Flow<List<OCFileWithSyncInfo>> =
        fileRepository.getFavoriteFilesWithSyncInfoForAccountAsFlow(params.owner)

    data class Params(
        val owner: String
    )
}
