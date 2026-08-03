package com.owncloud.android.presentation.files.operations

import androidx.annotation.StringRes
import java.util.UUID

/**
 * In-app archive failure surface: display fields plus the [failure] payload for Retry.
 */
data class ArchiveErrorUiModel(
    val failure: ArchiveWorkFailed,
    @StringRes val messageRes: Int,
    val showRetry: Boolean,
    val id: UUID = UUID.randomUUID(),
)
