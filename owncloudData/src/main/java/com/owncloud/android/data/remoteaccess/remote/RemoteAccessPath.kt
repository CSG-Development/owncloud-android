package com.owncloud.android.data.remoteaccess.remote

import com.owncloud.android.domain.device.model.DevicePath
import com.owncloud.android.domain.device.model.DevicePathType
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RemoteAccessPath(
    @Json(name = "type")
    val type: RemoteAccessPathType,
    @Json(name = "address")
    val address: String,
    @Json(name = "port")
    val port: Int? = null
) {
    fun mapToDomain(): DevicePath {
        return DevicePath(
            hostUrl = getDeviceBaseUrl(this),
            devicePathType = type.mapToDomain(),
        )
    }

    private fun getDeviceBaseUrl(remoteAccessPath: RemoteAccessPath): String {
        val address = remoteAccessPath.address
        val port = if (remoteAccessPath.port == null) "" else ":${remoteAccessPath.port}"
        return "https://${address}${port}/files"
    }
}

enum class RemoteAccessPathType {
    @Json(name = "local")
    LOCAL,
    @Json(name = "public")
    PUBLIC,
    @Json(name = "remote")
    REMOTE;

    fun mapToDomain(): DevicePathType = when (this) {
        LOCAL -> DevicePathType.LOCAL
        PUBLIC -> DevicePathType.PUBLIC
        REMOTE -> DevicePathType.REMOTE
    }

}

