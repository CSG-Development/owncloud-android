package com.owncloud.android.presentation.files.filelist.compose

import java.util.UUID

object ArchiveActivityUiModelFixtures {

    val singleCompressIndeterminate = ArchiveActivityUiModel(
        operations = listOf(
            ArchiveActivityOperationUiModel(
                workId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                displayName = "Documents.zip",
                isCompress = true,
                progress = null,
            ),
        ),
    )

    val threeOperationsMixed = ArchiveActivityUiModel(
        operations = listOf(
            ArchiveActivityOperationUiModel(
                workId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
                displayName = "Documents.zip",
                isCompress = true,
                progress = 35,
            ),
            ArchiveActivityOperationUiModel(
                workId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
                displayName = "Photos.zip",
                isCompress = true,
                progress = 60,
            ),
            ArchiveActivityOperationUiModel(
                workId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
                displayName = "List.zip",
                isCompress = false,
                progress = 10,
            ),
        ),
    )

    val all = listOf(singleCompressIndeterminate, threeOperationsMixed)
}
