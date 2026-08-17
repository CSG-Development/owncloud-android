package com.owncloud.android.presentation.files.operations

enum class ArchiveFailureType {
    CORRUPT,
    PASSWORD_PROTECTED,
    INSUFFICIENT_STORAGE,
    INVALID_NAMES,
    NETWORK,
    FILE_ACCESS,
    CONFLICT,
    UNEXPECTED,
}
