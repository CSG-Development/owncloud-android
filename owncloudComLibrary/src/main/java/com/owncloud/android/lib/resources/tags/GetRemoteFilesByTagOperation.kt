package com.owncloud.android.lib.resources.tags

import com.owncloud.android.lib.common.OwnCloudClient
import com.owncloud.android.lib.common.http.HttpConstants
import com.owncloud.android.lib.common.http.methods.nonwebdav.ReportMethod
import com.owncloud.android.lib.common.operations.RemoteOperation
import com.owncloud.android.lib.common.operations.RemoteOperationResult
import com.owncloud.android.lib.common.operations.RemoteOperationResult.ResultCode
import com.owncloud.android.lib.common.utils.isOneOf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit

class GetRemoteFilesByTagOperation(
    private val tagId: String,
) : RemoteOperation<List<String>>() {

    override fun run(client: OwnCloudClient): RemoteOperationResult<List<String>> {
        val webDavUrl = client.userFilesWebDavUri.toString()
        val requestBody = buildFilterFilesXml(tagId)

        return try {
            val reportMethod = ReportMethod(
                URL(webDavUrl),
                requestBody.toRequestBody("text/xml".toMediaType()),
            ).apply {
                setReadTimeout(TIMEOUT, TimeUnit.SECONDS)
                setConnectionTimeout(TIMEOUT, TimeUnit.SECONDS)
            }

            val status = client.executeHttpMethod(reportMethod)

            if (isSuccess(status)) {
                val responseBody = reportMethod.getResponseBodyAsString()
                val fileIds = MultiStatusParser.parseFileIds(responseBody)
                RemoteOperationResult<List<String>>(ResultCode.OK).apply {
                    data = fileIds
                    Timber.i("Found ${fileIds.size} files for tag $tagId - HTTP status code: $status")
                }
            } else {
                RemoteOperationResult<List<String>>(reportMethod).also {
                    Timber.w("Get files by tag failed: ${it.logMessage}")
                }
            }
        } catch (e: Exception) {
            RemoteOperationResult<List<String>>(e).also {
                Timber.e(it.exception, "Exception while getting files by tag $tagId")
            }
        }
    }

    private fun isSuccess(status: Int) =
        status.isOneOf(HttpConstants.HTTP_OK, HttpConstants.HTTP_MULTI_STATUS)

    companion object {
        private const val TIMEOUT = 10_000L

        private fun buildFilterFilesXml(tagId: String): String =
            """<?xml version="1.0" encoding="utf-8" ?>
              |<oc:filter-files xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              |  <d:prop>
              |    <oc:id/>
              |  </d:prop>
              |  <oc:filter-rules>
              |    <oc:systemtag>$tagId</oc:systemtag>
              |  </oc:filter-rules>
              |</oc:filter-files>""".trimMargin()
    }
}
