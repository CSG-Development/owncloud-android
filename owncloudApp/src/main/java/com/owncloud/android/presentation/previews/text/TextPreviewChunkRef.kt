package com.owncloud.android.presentation.previews.text

import androidx.compose.runtime.Immutable

/**
 * Compact on-disk range for one LazyColumn item in text preview.
 * Does not hold file text — content is loaded later via [startByte], [endByte].
 *
 * @param id Stable list key (typically sequential index from the indexer).
 * @param startByte Inclusive UTF-8 byte offset in the local file.
 * @param endByte Exclusive UTF-8 byte offset; must be greater than [startByte] and newline/codepoint aligned by the indexer.
 */
@Immutable
data class TextPreviewChunkRef(
    val id: Int,
    val startByte: Long,
    val endByte: Long,
    val kind: TextPreviewChunkKind = TextPreviewChunkKind.Plain,
) {

    val lengthBytes: Long
        get() = endByte - startByte
}

enum class TextPreviewChunkKind {
    Plain,
    Markdown,
}
