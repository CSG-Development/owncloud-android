package com.owncloud.android.workers.unzip

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UnzipWorkerPersistedState(
    @Json(name = "version") val version: Int = CURRENT_VERSION,
    @Json(name = "layout") val layout: PersistedExtractLayout,
    @Json(name = "baseRemotePath") val baseRemotePath: String,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
