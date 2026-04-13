package com.owncloud.android.domain.tags.model

data class OCTag(
    val id: String?,
    val localId: Long = 0,
    val displayName: String?,
    val userVisible: Boolean,
    val userAssignable: Boolean,
)
