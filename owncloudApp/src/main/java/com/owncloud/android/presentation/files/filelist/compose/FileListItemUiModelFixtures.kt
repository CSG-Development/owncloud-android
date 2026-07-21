package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.owncloud.android.R

/**
 * Fake [FileListItemUiModel] instances for Compose previews.
 * No network, account, or domain objects required.
 */
object FileListItemUiModelFixtures {

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private val now = System.currentTimeMillis()

    val folder = FileListItemUiModel(
        fileId = 1L,
        name = "Documents",
        length = 0L,
        modificationTimestamp = now - 2 * DAY_MS,
        isFolder = true,
        isImage = false,
        mimeType = "DIR",
        mimeIconRes = R.drawable.ic_homecloud_folder,
        thumbnailRemoteId = null,
        needsThumbnail = false,
        localPin = FileListLocalPin.None,
        sharedByLink = false,
        sharedWithUsers = false,
        isSelected = false,
        showCheckbox = false,
        showThreeDotMenu = true,
        virtualKind = FileListVirtualKind.None,
        uploadProgress = null,
        isProgressIndeterminate = false,
        spacePath = null,
        hideSizeAndSeparator = false,
    )

    val file = FileListItemUiModel(
        fileId = 2L,
        name = "report.pdf",
        length = 1_258_291L,
        modificationTimestamp = now - DAY_MS,
        isFolder = false,
        isImage = false,
        mimeType = "application/pdf",
        mimeIconRes = R.drawable.file_pdf,
        thumbnailRemoteId = null,
        needsThumbnail = false,
        localPin = FileListLocalPin.None,
        sharedByLink = false,
        sharedWithUsers = false,
        isSelected = false,
        showCheckbox = false,
        showThreeDotMenu = true,
        virtualKind = FileListVirtualKind.None,
        uploadProgress = null,
        isProgressIndeterminate = false,
        spacePath = null,
        hideSizeAndSeparator = false,
    )

    val fileWithThumbnail = file.copy(
        fileId = 3L,
        name = "photo.jpg",
        length = 3_563_520L,
        mimeType = "image/jpeg",
        isImage = true,
        mimeIconRes = R.drawable.file_image,
        thumbnailRemoteId = "remote-thumb-3",
        needsThumbnail = true,
        localPin = FileListLocalPin.Downloaded,
    )

    val longName = file.copy(
        fileId = 4L,
        name = "very_long_file_name_that_should_ellipsize_in_the_list_row_layout.pdf",
    )

    val sharedByLink = file.copy(
        fileId = 5L,
        name = "shared-link.docx",
        sharedByLink = true,
    )

    val sharedWithUsers = file.copy(
        fileId = 6L,
        name = "team-notes.txt",
        sharedWithUsers = true,
    )

    val syncing = file.copy(
        fileId = 7L,
        name = "syncing.bin",
        localPin = FileListLocalPin.Syncing,
    )

    val conflict = file.copy(
        fileId = 8L,
        name = "conflict.xls",
        localPin = FileListLocalPin.Conflict,
    )

    val availableOffline = file.copy(
        fileId = 9L,
        name = "offline.pdf",
        localPin = FileListLocalPin.AvailableOffline,
    )

    val downloaded = file.copy(
        fileId = 10L,
        name = "downloaded.pdf",
        localPin = FileListLocalPin.Downloaded,
    )

    val selected = file.copy(
        fileId = 11L,
        name = "selected.pdf",
        isSelected = true,
        showCheckbox = true,
        showThreeDotMenu = false,
    )

    val unselectedInSelectionMode = file.copy(
        fileId = 12L,
        name = "unselected.pdf",
        isSelected = false,
        showCheckbox = true,
        showThreeDotMenu = false,
    )

    val withSpacePath = file.copy(
        fileId = 13L,
        name = "space-file.pdf",
        spacePath = FileListSpacePathUiModel(
            parentPath = "/Projects/2024/",
            spaceName = "Engineering",
            showPersonalLabel = false,
        ),
    )

    val withPersonalSpacePath = file.copy(
        fileId = 14L,
        name = "personal-file.pdf",
        spacePath = FileListSpacePathUiModel(
            parentPath = "/Photos/",
            spaceName = null,
            showPersonalLabel = true,
        ),
    )

    val virtualUploadIndeterminate = file.copy(
        fileId = 15L,
        name = "uploading.mp4",
        length = 125_829_120L,
        virtualKind = FileListVirtualKind.Upload,
        uploadProgress = null,
        isProgressIndeterminate = true,
        showThreeDotMenu = false,
        showCheckbox = false,
        thumbnailRemoteId = null,
        needsThumbnail = false,
    )

    val virtualUploadProgress = virtualUploadIndeterminate.copy(
        fileId = 16L,
        name = "uploading-half.mp4",
        uploadProgress = 45,
        isProgressIndeterminate = false,
    )

    val virtualArchive = file.copy(
        fileId = 17L,
        name = "archive.zip",
        virtualKind = FileListVirtualKind.Archive,
        uploadProgress = 70,
        isProgressIndeterminate = false,
        showThreeDotMenu = false,
        showCheckbox = false,
        thumbnailRemoteId = null,
        needsThumbnail = false,
    )

    val folderMultiPersonal = folder.copy(
        fileId = 18L,
        name = "KW Folder",
        hideSizeAndSeparator = true,
        modificationTimestamp = now,
    )

    /** Representative set for [FileListItemUiModelPreviewParameterProvider] / multi-preview. */
    val all: List<FileListItemUiModel> = listOf(
        folder,
        file,
        fileWithThumbnail,
        longName,
        sharedByLink,
        sharedWithUsers,
        syncing,
        conflict,
        availableOffline,
        downloaded,
        selected,
        unselectedInSelectionMode,
        withSpacePath,
        withPersonalSpacePath,
        virtualUploadIndeterminate,
        virtualUploadProgress,
        virtualArchive,
        folderMultiPersonal,
    )
}

/** Supplies [FileListItemUiModelFixtures.all] for Compose `@Preview(parameterProvider = ...)`. */
class FileListItemUiModelPreviewParameterProvider : PreviewParameterProvider<FileListItemUiModel> {
    override val values: Sequence<FileListItemUiModel> =
        FileListItemUiModelFixtures.all.asSequence()
}
