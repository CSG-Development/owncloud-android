package com.owncloud.android.data.appupdate.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Response model for app update check API
 */
@JsonClass(generateAdapter = true)
data class AppUpdateResponse(
    @Json(name = "applicationId")
    val applicationId: String,
    @Json(name = "versionCode")
    val versionCode: Int,
    @Json(name = "versionName")
    val versionName: String? = null,
    @Json(name = "minSdk")
    val minSdk: Int? = null,
    @Json(name = "url")
    val updateUrl: String? = null,
    @Json(name = "releaseDate")
    val releaseDate: String? = null,
)
