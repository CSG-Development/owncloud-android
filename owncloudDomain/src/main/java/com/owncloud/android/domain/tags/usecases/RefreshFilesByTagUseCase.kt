package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.tags.TagRepository

class RefreshFilesByTagUseCase(
    private val tagRepository: TagRepository,
    private val filesRepository: FileRepository,
) : BaseUseCaseWithResult<List<OCFile>, RefreshFilesByTagUseCase.Params>() {

    override fun run(params: Params): List<OCFile> {
        return tagRepository.refreshFilesByTag(params.accountName, params.serverTagId).mapNotNull {
            filesRepository.getFileByRemoteId(it)
        }
    }

    data class Params(val accountName: String, val serverTagId: String)
}
