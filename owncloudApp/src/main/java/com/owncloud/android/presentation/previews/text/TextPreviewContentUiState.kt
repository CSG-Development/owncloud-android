package com.owncloud.android.presentation.previews.text

/**
 * Index / windowing state for plain text preview (Compose path).
 * Independent from menu / file metadata flows on [com.owncloud.android.presentation.previews.PreviewTextViewModel].
 */
sealed interface TextPreviewContentUiState {
    data object Idle : TextPreviewContentUiState

    data object Loading : TextPreviewContentUiState

    data class Indexing(
        val chunks: List<TextPreviewChunkRef>,
        val bytesScanned: Long,
    ) : TextPreviewContentUiState

    data class Ready(
        val chunks: List<TextPreviewChunkRef>,
    ) : TextPreviewContentUiState

    data class Error(
        val throwable: Throwable,
    ) : TextPreviewContentUiState
}

fun TextPreviewContentUiState.chunksOrEmpty(): List<TextPreviewChunkRef> = when (this) {
    is TextPreviewContentUiState.Indexing -> chunks
    is TextPreviewContentUiState.Ready -> chunks
    else -> emptyList()
}
