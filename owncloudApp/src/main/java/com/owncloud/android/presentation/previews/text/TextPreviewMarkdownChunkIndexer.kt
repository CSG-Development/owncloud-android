package com.owncloud.android.presentation.previews.text

import kotlinx.coroutines.flow.Flow

/**
 * Builds a compact byte-range index for markdown without retaining file contents.
 *
 * Flush rules (outside fenced code only):
 * - blank line
 * - soft line / byte budget for the pending chunk
 *
 * Fenced blocks (`` ``` `` / `~~~`, CommonMark-style at line start) are never split mid-fence.
 * Collect on an IO dispatcher — the flow performs blocking reads.
 */
class TextPreviewMarkdownChunkIndexer(
    maxLinesPerChunk: Int = DEFAULT_MAX_LINES_PER_CHUNK,
    maxBytesPerChunk: Int = DEFAULT_MAX_BYTES_PER_CHUNK,
    emitBatchSize: Int = DEFAULT_EMIT_BATCH_SIZE,
    maxLineInspectBytes: Int = DEFAULT_MAX_LINE_INSPECT_BYTES,
) {

    private val engine = TextPreviewChunkIndexEngine(
        kind = TextPreviewChunkKind.Markdown,
        policy = MarkdownChunkLinePolicy(),
        maxLinesPerChunk = maxLinesPerChunk,
        maxBytesPerChunk = maxBytesPerChunk,
        emitBatchSize = emitBatchSize,
        maxLineInspectBytes = maxLineInspectBytes,
    )

    fun index(path: String): Flow<TextPreviewIndexSnapshot> = engine.index(path)

    companion object {
        const val DEFAULT_MAX_LINES_PER_CHUNK = 40
        const val DEFAULT_MAX_BYTES_PER_CHUNK = 12 * 1024
        const val DEFAULT_EMIT_BATCH_SIZE = 8
        const val DEFAULT_MAX_LINE_INSPECT_BYTES = 8 * 1024
    }
}
