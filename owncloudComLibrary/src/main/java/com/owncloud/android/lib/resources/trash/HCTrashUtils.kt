package com.owncloud.android.lib.resources.trash

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.accounts.AccountUtils
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HCTrashUtils {
    private const val WEBDAV_TRASH_BIN_PATH = "/remote.php/dav/trash-bin/"
    private const val TRASH_PREVIEW_PATH = "/index.php/apps/files_trashbin/ajax/preview.php"
    private const val MILLIS_THRESHOLD = 1_000_000_000_000L

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

    fun getTrashPreviewFileParam(originalFilename: String, deletedTimestampSeconds: Long): String? {
        if (deletedTimestampSeconds <= 0) {
            return null
        }
        return "/$originalFilename.d$deletedTimestampSeconds"
    }

    fun getTrashPreviewUrl(
        baseUri: String,
        originalFilename: String,
        deletedTimestamp: Long?,
        widthPx: Int,
        heightPx: Int,
    ): String? {
        val timestampSeconds = deletedTimestamp?.takeIf { it > 0 } ?: return null
        val normalizedSeconds = if (timestampSeconds > MILLIS_THRESHOLD) {
            timestampSeconds / 1000
        } else {
            timestampSeconds
        }
        val fileParam = getTrashPreviewFileParam(originalFilename, normalizedSeconds) ?: return null
        val cacheBuster = normalizedSeconds * 1000
        return buildString {
            append(baseUri.trimEnd('/'))
            append(TRASH_PREVIEW_PATH)
            append("?file=")
            append(encodeTrashPreviewFileParam(fileParam))
            append("&x=")
            append(widthPx)
            append("&y=")
            append(heightPx)
            append("&c=")
            append(cacheBuster)
        }
    }

    private fun encodeTrashPreviewFileParam(fileParam: String): String =
        URLEncoder.encode(fileParam, StandardCharsets.UTF_8.name())
}
