package com.owncloud.android.presentation.files.details

sealed interface ImageMetadataUiState {
    data object Initial : ImageMetadataUiState
    data object Hidden : ImageMetadataUiState
    data object Loading : ImageMetadataUiState
    data object WaitingForDownload : ImageMetadataUiState
    data class Success(val sections: List<MetadataSectionUi>) : ImageMetadataUiState
}

data class MetadataSectionUi(
    val properties: List<MetadataPropertyUi>,
)

data class MetadataPropertyUi(
    val label: String,
    val value: String,
)

