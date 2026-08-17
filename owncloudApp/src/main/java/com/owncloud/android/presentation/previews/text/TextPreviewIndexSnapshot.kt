package com.owncloud.android.presentation.previews.text

/**
 * Progressive snapshot of a text-preview index pass.
 * [chunks] is the cumulative list of ranges found so far (no file text).
 */
data class TextPreviewIndexSnapshot(
    val chunks: List<TextPreviewChunkRef>,
    val bytesScanned: Long,
    val isComplete: Boolean,
)
