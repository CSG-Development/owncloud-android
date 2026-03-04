package com.owncloud.android.domain.tags.model

data class OCTag(
    val id: String?,
    val displayName: String?,
    val userVisible: Boolean,
    val userAssignable: Boolean,
)
