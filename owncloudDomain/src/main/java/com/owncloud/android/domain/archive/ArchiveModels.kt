package com.owncloud.android.domain.archive

import com.owncloud.android.domain.files.model.OCFile
import java.io.File

data class ArchiveEntry(
    val ocFile: OCFile,
    val zipEntryPath: String,
)

data class ArchiveEntryWithLocalPath(
    val zipEntryPath: String,
    val localFile: File,
)

data class ArchiveCollectionResult(
    val fileEntries: List<ArchiveEntry>,
    val emptyDirectoryPaths: Set<String>,
)
