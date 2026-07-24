package com.owncloud.android.presentation.files.operations

enum class ArchiveFailureType {
    CORRUPT,
    INSUFFICIENT_STORAGE,
    INVALID_NAMES,
    NETWORK,
    FILE_ACCESS,
    UNEXPECTED,
}
