package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.BaseUseCaseWithResult
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag
import timber.log.Timber

class SyncTagsAndFilesForAccountUseCase(
    private val tagRepository: TagRepository,
    private val filesRepository: FileRepository,
) : BaseUseCaseWithResult<List<OCTag>, SyncTagsAndFilesForAccountUseCase.Params>() {

    override fun run(params: Params): List<OCTag> {
        val tags = tagRepository.refreshTagsForAccount(params.accountName)
        tags.forEach { tag ->
            val serverTagId = tag.id
            if (serverTagId == null) {
                Timber.w("Skipping file sync for tag '${tag.displayName}' — id is null")
                return@forEach
            }
            try {
                tagRepository.refreshFilesByTag(params.accountName, serverTagId).forEach { remoteId ->
                    // Called to populate local storage as a side effect; the return value is intentionally ignored.
                    filesRepository.getFileByRemoteId(remoteId)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync files for tag ${tag.displayName}")
            }
        }
        return tags
    }

    data class Params(val accountName: String)
}
