package com.owncloud.android.presentation.previews.text

import kotlinx.coroutines.flow.Flow

/**
 * Builds a compact byte-range index for plain text without retaining file contents.
 *
 * Chunks end only on `\n` (or EOF), when line count or byte size thresholds are met.
 * Collect on an IO dispatcher — the flow performs blocking reads.
 */
class TextPreviewPlainChunkIndexer(
    maxLinesPerChunk: Int = DEFAULT_MAX_LINES_PER_CHUNK,
    maxBytesPerChunk: Int = DEFAULT_MAX_BYTES_PER_CHUNK,
    emitBatchSize: Int = DEFAULT_EMIT_BATCH_SIZE,
) {

    private val engine = TextPreviewChunkIndexEngine(
        kind = TextPreviewChunkKind.Plain,
        policy = PlainChunkLinePolicy(),
        maxLinesPerChunk = maxLinesPerChunk,
        maxBytesPerChunk = maxBytesPerChunk,
        emitBatchSize = emitBatchSize,
        maxLineInspectBytes = 0,
    )

    /**
     * Emits cumulative [TextPreviewIndexSnapshot]s as chunks are discovered, then a final
     * [TextPreviewIndexSnapshot.isComplete] = true emission.
     */
    fun index(path: String): Flow<TextPreviewIndexSnapshot> = engine.index(path)

    companion object {
        const val DEFAULT_MAX_LINES_PER_CHUNK = 80
        const val DEFAULT_MAX_BYTES_PER_CHUNK = 12 * 1024
        const val DEFAULT_EMIT_BATCH_SIZE = 8
    }
}
