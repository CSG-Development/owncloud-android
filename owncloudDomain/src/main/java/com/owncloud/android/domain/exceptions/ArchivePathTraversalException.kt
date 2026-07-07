package com.owncloud.android.domain.exceptions

class ArchivePathTraversalException(
    val entryName: String,
) : Exception()
