package com.owncloud.android.workers.unzip

import com.owncloud.android.domain.archive.ArchiveExtractLayout
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PersistedExtractLayout(
    @Json(name = "type") val type: PersistedExtractLayoutType,
    @Json(name = "topLevelRoot") val topLevelRoot: String? = null,
    @Json(name = "isTopLevelFolder") val isTopLevelFolder: Boolean? = null,
)

enum class PersistedExtractLayoutType {
    @Json(name = "into_archive_folder")
    INTO_ARCHIVE_FOLDER,

    @Json(name = "direct_to_parent")
    DIRECT_TO_PARENT,
}

fun ArchiveExtractLayout.toPersisted(): PersistedExtractLayout =
    when (this) {
        is ArchiveExtractLayout.DirectToParent -> PersistedExtractLayout(
            type = PersistedExtractLayoutType.DIRECT_TO_PARENT,
            topLevelRoot = topLevelRoot,
            isTopLevelFolder = isTopLevelFolder,
        )

        ArchiveExtractLayout.IntoArchiveFolder -> PersistedExtractLayout(
            type = PersistedExtractLayoutType.INTO_ARCHIVE_FOLDER,
        )
    }

fun PersistedExtractLayout.toDomain(): ArchiveExtractLayout =
    when (type) {
        PersistedExtractLayoutType.INTO_ARCHIVE_FOLDER -> ArchiveExtractLayout.IntoArchiveFolder

        PersistedExtractLayoutType.DIRECT_TO_PARENT -> ArchiveExtractLayout.DirectToParent(
            topLevelRoot = topLevelRoot.orEmpty(),
            isTopLevelFolder = isTopLevelFolder ?: true,
        )
    }
