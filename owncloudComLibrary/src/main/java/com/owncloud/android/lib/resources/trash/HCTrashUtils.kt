package com.owncloud.android.lib.resources.trash

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.accounts.AccountUtils
import java.io.File
import java.net.URL

object HCTrashUtils {
    private const val WEBDAV_TRASH_BIN_PATH = "/remote.php/dav/trash-bin/"

    fun getTrashBinWebDavUrl(client: OwnCloudClient): URL {
        val userId = AccountUtils.getUserId(client.account.savedAccount, client.context)
        val baseUri = client.baseUri.toString().trimEnd('/')
        return URL("$baseUri$WEBDAV_TRASH_BIN_PATH$userId/")
    }

    fun getTrashItemWebDavUrl(client: OwnCloudClient, fileId: String): URL {
        val baseUri = client.baseUri.toString().trimEnd('/')
        val userId = AccountUtils.getUserId(client.account.savedAccount, client.context)
        return URL("$baseUri$WEBDAV_TRASH_BIN_PATH$userId/$fileId")
    }

    /**
     * Converts server [originalLocation] (e.g. "Documents/file.pdf")
     * to a remote path suitable for [com.owncloud.android.lib.common.network.WebdavUtils.encodePath].
     */
    fun originalLocationToRemotePath(originalLocation: String): String {
        val spaceSlashIndex = originalLocation.indexOf(" /")
        if (spaceSlashIndex >= 0) {
            val folder = originalLocation.substring(0, spaceSlashIndex + 1)
            val fileName = originalLocation.substring(spaceSlashIndex + 2)
            return File.separator + folder + File.separator + fileName
        }
        return if (originalLocation.startsWith(File.separator)) {
            originalLocation
        } else {
            File.separator + originalLocation
        }
    }
}
