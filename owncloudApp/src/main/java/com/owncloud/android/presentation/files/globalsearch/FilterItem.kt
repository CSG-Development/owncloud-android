package com.owncloud.android.presentation.files.globalsearch

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class FilterItem(
    val id: String,
    val label: String,
    val iconResId: Int? = null,
) : Parcelable
