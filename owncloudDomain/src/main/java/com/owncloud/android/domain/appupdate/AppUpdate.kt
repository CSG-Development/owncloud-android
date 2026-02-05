package com.owncloud.android.domain.appupdate

data class AppUpdate(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val releaseDate: String,
    val updateUrl: String
)
