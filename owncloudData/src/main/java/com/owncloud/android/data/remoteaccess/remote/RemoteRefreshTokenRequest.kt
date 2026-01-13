package com.owncloud.android.data.remoteaccess.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteRefreshTokenRequest(
    @Json(name = "refreshToken")
    val refreshToken: String,
    @Json(name = "clientId")
    val clientId: String
)
