package com.owncloud.android.data.remoteaccess.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteAccessTokenErrorResponse(
    @Json(name = "name")
    val errorType: String
)
