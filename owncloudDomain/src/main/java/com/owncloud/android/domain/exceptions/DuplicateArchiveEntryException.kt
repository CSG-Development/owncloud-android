package com.owncloud.android.domain.exceptions

class DuplicateArchiveEntryException(
    val path: String,
) : Exception()
