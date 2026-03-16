package com.owncloud.android.lib.resources.tags

data class RemoteTag(
    var id: String? = null,
    var displayName: String? = null,
    var userVisible: Boolean = true,
    var userAssignable: Boolean = true,
)
