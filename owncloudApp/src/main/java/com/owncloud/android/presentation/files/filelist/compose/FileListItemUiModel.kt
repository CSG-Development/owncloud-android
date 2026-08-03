package com.owncloud.android.presentation.files.filelist.compose

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.owncloud.android.R

/**
 * Stable UI model for a file-list row / grid cell.
 * Selection and chrome flags are included so Compose items can render without adapter state.
 *
 * Size and relative timestamps are raw values; format them in the composable with resources/DisplayUtils.
 */
@Immutable
data class FileListItemUiModel(
    val fileId: Long,
    val name: String,
    val length: Long,
    val modificationTimestamp: Long,
    val isFolder: Boolean,
    val isImage: Boolean,
    val mimeType: String,
    @DrawableRes val mimeIconRes: Int,
    /** Cache key for [com.owncloud.android.datamodel.ThumbnailsCacheManager]; null when no thumbnail. */
    val thumbnailRemoteId: String?,
    val needsThumbnail: Boolean,
    val localPin: FileListLocalPin,
    val sharedByLink: Boolean,
    val sharedWithUsers: Boolean,
    val isSelected: Boolean,
    val showCheckbox: Boolean,
    val showThreeDotMenu: Boolean,
    val virtualKind: FileListVirtualKind,
    val uploadProgress: Int?,
    val isProgressIndeterminate: Boolean,
    val spacePath: FileListSpacePathUiModel?,
    /** When true, hide size text and the size/date separator (KW multi-personal folders). */
    val hideSizeAndSeparator: Boolean,
) {
    val isVirtual: Boolean
        get() = virtualKind != FileListVirtualKind.None

    val showShareIcons: Boolean
        get() = !isVirtual && (sharedByLink || sharedWithUsers)
}

enum class FileListLocalPin {
    None,
    Syncing,
    Conflict,
    AvailableOffline,
    Downloaded,
}

enum class FileListVirtualKind {
    None,
    Upload,
}

/**
 * @param spaceName Domain space name when not using the personal label; ignored if [showPersonalLabel] is true.
 * @param showPersonalLabel When true, UI should show the personal-space string resource and folder icon.
 */
@Immutable
data class FileListSpacePathUiModel(
    val parentPath: String,
    val spaceName: String?,
    val showPersonalLabel: Boolean,
)

@DrawableRes
fun FileListLocalPin.toDrawableRes(): Int? = when (this) {
    FileListLocalPin.None -> null
    FileListLocalPin.Syncing -> R.drawable.sync_pin
    FileListLocalPin.Conflict -> R.drawable.error_pin
    FileListLocalPin.AvailableOffline -> R.drawable.offline_available_pin
    FileListLocalPin.Downloaded -> R.drawable.downloaded_pin
}
