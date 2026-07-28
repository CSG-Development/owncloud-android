package com.owncloud.android.presentation.files.operations

import androidx.annotation.StringRes
import com.owncloud.android.R

data class ArchiveFailureUiModel(
    @StringRes val messageRes: Int,
    val showRetry: Boolean,
)

fun ArchiveFailureType.toUiModel(isCompress: Boolean): ArchiveFailureUiModel =
    ArchiveFailureUiModel(
        messageRes = messageRes(isCompress),
        showRetry = showRetry,
    )

val ArchiveFailureType.showRetry: Boolean
    get() = when (this) {
        ArchiveFailureType.NETWORK,
        ArchiveFailureType.INSUFFICIENT_STORAGE,
        ArchiveFailureType.UNEXPECTED,
        ArchiveFailureType.FILE_ACCESS,
        -> true

        ArchiveFailureType.CORRUPT,
        ArchiveFailureType.PASSWORD_PROTECTED,
        ArchiveFailureType.INVALID_NAMES,
        -> false
    }

@StringRes
fun ArchiveFailureType.messageRes(isCompress: Boolean): Int =
    if (isCompress) {
        when (this) {
            ArchiveFailureType.CORRUPT -> R.string.homecloud_filelist_compress_error_corrupt
            ArchiveFailureType.PASSWORD_PROTECTED -> R.string.homecloud_filelist_unsupported_archive_message
            ArchiveFailureType.INSUFFICIENT_STORAGE -> R.string.homecloud_filelist_compress_error_storage_limit
            ArchiveFailureType.INVALID_NAMES -> R.string.homecloud_filelist_compress_error_invalid_names
            ArchiveFailureType.NETWORK -> R.string.homecloud_filelist_compress_error_network_timeout
            ArchiveFailureType.FILE_ACCESS -> R.string.homecloud_filelist_compress_error_file_access
            ArchiveFailureType.UNEXPECTED -> R.string.homecloud_filelist_compress_error_generic
        }
    } else {
        when (this) {
            ArchiveFailureType.CORRUPT -> R.string.homecloud_filelist_extract_error_corrupt
            ArchiveFailureType.PASSWORD_PROTECTED -> R.string.homecloud_filelist_unsupported_archive_message
            ArchiveFailureType.INSUFFICIENT_STORAGE -> R.string.homecloud_filelist_extract_error_insufficient_storage
            ArchiveFailureType.INVALID_NAMES -> R.string.homecloud_filelist_extract_error_invalid_names
            ArchiveFailureType.NETWORK -> R.string.homecloud_filelist_extract_error_network
            ArchiveFailureType.FILE_ACCESS,
            ArchiveFailureType.UNEXPECTED,
            -> R.string.homecloud_filelist_extract_error_unexpected
        }
    }
