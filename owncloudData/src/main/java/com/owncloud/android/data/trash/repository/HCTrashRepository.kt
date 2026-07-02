package com.owncloud.android.data.trash.repository

import com.owncloud.android.data.files.datasources.RemoteFileDataSource
import com.owncloud.android.data.trash.datasources.RemoteTrashDataSource
import com.owncloud.android.domain.capabilities.CapabilityRepository
import com.owncloud.android.domain.capabilities.model.CapabilityBooleanType
import com.owncloud.android.domain.exceptions.InvalidOverwriteException
import com.owncloud.android.domain.exceptions.TrashNotSupportedException
import com.owncloud.android.domain.trash.TrashRepository
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.lib.resources.trash.HCTrashUtils

class HCTrashRepository(
    private val remoteTrashDataSource: RemoteTrashDataSource,
    private val capabilityRepository: CapabilityRepository,
    private val remoteFileDataSource: RemoteFileDataSource,
) : TrashRepository {

    override fun isTrashEnabled(accountName: String): Boolean =
        capabilityRepository.getStoredCapabilities(accountName)?.filesUndelete == CapabilityBooleanType.TRUE

    override fun listTrash(accountName: String): List<HCTrashItem> {
        requireTrashEnabled(accountName)
        return remoteTrashDataSource.listTrash(accountName)
    }

    override fun restoreTrashItem(
        accountName: String,
        fileId: String,
        originalLocation: String,
        forceOverride: Boolean,
    ) {
        requireTrashEnabled(accountName)
        if (forceOverride) {
            restoreTrashItemAtLocation(
                accountName = accountName,
                fileId = fileId,
                originalLocation = originalLocation,
                forceOverride = true,
            )
            return
        }
        try {
            restoreTrashItemAtLocation(
                accountName = accountName,
                fileId = fileId,
                originalLocation = originalLocation,
                forceOverride = false,
            )
        } catch (_: InvalidOverwriteException) {
            val remotePath = HCTrashUtils.originalLocationToRemotePath(originalLocation)
            val availablePath = remoteFileDataSource.getAvailableRemotePath(
                remotePath = remotePath,
                accountName = accountName,
                spaceWebDavUrl = null,
                isUserLogged = true,
            )
            val renamedLocation = HCTrashUtils.remotePathToOriginalLocation(availablePath)
            restoreTrashItemAtLocation(
                accountName = accountName,
                fileId = fileId,
                originalLocation = renamedLocation,
                forceOverride = false,
            )
        }
    }

    private fun restoreTrashItemAtLocation(
        accountName: String,
        fileId: String,
        originalLocation: String,
        forceOverride: Boolean,
    ) {
        remoteTrashDataSource.restoreTrashItem(
            accountName = accountName,
            fileId = fileId,
            originalLocation = originalLocation,
            forceOverride = forceOverride,
        )
    }

    override fun deleteTrashItemPermanently(accountName: String, fileId: String) {
        requireTrashEnabled(accountName)
        remoteTrashDataSource.deleteTrashItemPermanently(accountName, fileId)
    }

    private fun requireTrashEnabled(accountName: String) {
        if (!isTrashEnabled(accountName)) {
            throw TrashNotSupportedException()
        }
    }
}
