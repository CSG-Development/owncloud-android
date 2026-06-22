package com.owncloud.android.domain.files.model

data class MetadataSection(
    val type: MetadataSectionType,
    val properties: List<MetadataProperty>,
)
