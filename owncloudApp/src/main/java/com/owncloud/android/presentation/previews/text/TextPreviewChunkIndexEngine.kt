package com.owncloud.android.presentation.previews.text

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream

/**
 * Decides whether to flush the current chunk after a completed line.
 * Owns any format-specific state (e.g. markdown fence tracking).
 */
internal interface TextPreviewChunkLinePolicy {
    /**
     * @param lineBytes bytes of the line excluding the terminating `\n`
     *        (may be truncated for inspection; empty when inspection is disabled)
     * @param linesInChunk number of lines in the pending chunk including this one
     * @param chunkByteCount pending chunk size in bytes including this line's `\n`
     * @return true to flush the chunk ending at the current file position
     */
    fun shouldFlushAfterLine(
        lineBytes: ByteArray,
        linesInChunk: Int,
        chunkByteCount: Long,
        maxLinesPerChunk: Int,
        maxBytesPerChunk: Int,
    ): Boolean
}

/**
 * Shared sequential UTF-8 scan that emits [TextPreviewIndexSnapshot]s.
 * Policies control when chunks end; this engine owns IO, offsets, and batching.
 */
internal class TextPreviewChunkIndexEngine(
    private val kind: TextPreviewChunkKind,
    private val policy: TextPreviewChunkLinePolicy,
    private val maxLinesPerChunk: Int,
    private val maxBytesPerChunk: Int,
    private val emitBatchSize: Int,
    private val maxLineInspectBytes: Int,
) {

    init {
        require(maxLinesPerChunk > 0)
        require(maxBytesPerChunk > 0)
        require(emitBatchSize > 0)
        require(maxLineInspectBytes >= 0)
    }

    fun index(path: String): Flow<TextPreviewIndexSnapshot> = flow {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("Not a readable file: $path")
        }

        val allChunks = ArrayList<TextPreviewChunkRef>()
        val pendingBatch = ArrayList<TextPreviewChunkRef>(emitBatchSize)
        var nextId = 0
        var chunkStart = 0L
        var linesInChunk = 0
        var filePos = 0L
        var lastByteWasNewline = false

        val lineBuffer = ByteArrayOutputStream(256)
        val readBuffer = ByteArray(READ_BUFFER_SIZE)

        fun flushChunk(endExclusive: Long) {
            if (endExclusive <= chunkStart) return
            pendingBatch.add(
                TextPreviewChunkRef(
                    id = nextId++,
                    startByte = chunkStart,
                    endByte = endExclusive,
                    kind = kind,
                )
            )
            chunkStart = endExclusive
            linesInChunk = 0
        }

        suspend fun emitBatch(bytesScanned: Long, force: Boolean, isComplete: Boolean) {
            if (pendingBatch.isEmpty() && !isComplete && !force) return
            if (pendingBatch.isNotEmpty()) {
                allChunks.addAll(pendingBatch)
                pendingBatch.clear()
            }
            emit(
                TextPreviewIndexSnapshot(
                    chunks = allChunks.toList(),
                    bytesScanned = bytesScanned,
                    isComplete = isComplete,
                )
            )
        }

        fun onCompleteLine() {
            val lineBytes = if (maxLineInspectBytes == 0) {
                EmptyLineBytes
            } else {
                lineBuffer.toByteArray()
            }
            lineBuffer.reset()

            linesInChunk++
            val chunkByteCount = filePos - chunkStart
            if (
                policy.shouldFlushAfterLine(
                    lineBytes = lineBytes,
                    linesInChunk = linesInChunk,
                    chunkByteCount = chunkByteCount,
                    maxLinesPerChunk = maxLinesPerChunk,
                    maxBytesPerChunk = maxBytesPerChunk,
                )
            ) {
                flushChunk(filePos)
            }
        }

        FileInputStream(file).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(readBuffer)
                if (read < 0) break

                for (i in 0 until read) {
                    val byte = readBuffer[i]
                    filePos++
                    if (byte == NEWLINE) {
                        lastByteWasNewline = true
                        onCompleteLine()
                        if (pendingBatch.size >= emitBatchSize) {
                            emitBatch(bytesScanned = filePos, force = false, isComplete = false)
                        }
                    } else {
                        lastByteWasNewline = false
                        if (lineBuffer.size() < maxLineInspectBytes) {
                            lineBuffer.write(byte.toInt())
                        }
                    }
                }
            }
        }

        // EOF without a trailing newline: finish the partial line, then flush remainder.
        if (filePos > 0L && !lastByteWasNewline) {
            onCompleteLine()
        }
        flushChunk(filePos)
        emitBatch(bytesScanned = filePos, force = true, isComplete = true)
    }

    companion object {
        private val EmptyLineBytes = ByteArray(0)
        private const val READ_BUFFER_SIZE = 8 * 1024
        private const val NEWLINE: Byte = '\n'.code.toByte()
    }
}

/**
 * Flush when line or byte budget is reached. Ignores line content.
 */
internal class PlainChunkLinePolicy : TextPreviewChunkLinePolicy {
    override fun shouldFlushAfterLine(
        lineBytes: ByteArray,
        linesInChunk: Int,
        chunkByteCount: Long,
        maxLinesPerChunk: Int,
        maxBytesPerChunk: Int,
    ): Boolean = linesInChunk >= maxLinesPerChunk || chunkByteCount >= maxBytesPerChunk
}

/**
 * Fence-aware markdown flush: blank lines or budget outside fences; never mid-fence.
 */
internal class MarkdownChunkLinePolicy : TextPreviewChunkLinePolicy {

    private var inFence = false
    private var fenceChar: Byte = 0
    private var fenceLength = 0

    override fun shouldFlushAfterLine(
        lineBytes: ByteArray,
        linesInChunk: Int,
        chunkByteCount: Long,
        maxLinesPerChunk: Int,
        maxBytesPerChunk: Int,
    ): Boolean {
        val isBlank = isBlankLine(lineBytes)
        val fence = parseFenceLine(lineBytes)
        val overBudget = linesInChunk >= maxLinesPerChunk || chunkByteCount >= maxBytesPerChunk

        if (inFence) {
            if (fence != null && fence.char == fenceChar && fence.length >= fenceLength) {
                inFence = false
                return overBudget
            }
            return false
        }

        if (fence != null) {
            inFence = true
            fenceChar = fence.char
            fenceLength = fence.length
            return false
        }

        return isBlank || overBudget
    }

    private data class FenceMarker(val char: Byte, val length: Int)

    private fun isBlankLine(lineBytes: ByteArray): Boolean {
        if (lineBytes.isEmpty()) return true
        if (lineBytes.size == 1 && lineBytes[0] == CR) return true
        return lineBytes.all { it == SPACE || it == TAB || it == CR }
    }

    private fun parseFenceLine(lineBytes: ByteArray): FenceMarker? {
        var index = 0
        val length = lineBytes.size
        var spaces = 0
        while (index < length && spaces < 3 && lineBytes[index] == SPACE) {
            index++
            spaces++
        }
        if (index >= length) return null

        val char = lineBytes[index]
        if (char != BACKTICK && char != TILDE) return null

        var fenceLen = 0
        while (index + fenceLen < length && lineBytes[index + fenceLen] == char) {
            fenceLen++
        }
        if (fenceLen < 3) return null

        return FenceMarker(char = char, length = fenceLen)
    }

    companion object {
        private const val CR: Byte = '\r'.code.toByte()
        private const val SPACE: Byte = ' '.code.toByte()
        private const val TAB: Byte = '\t'.code.toByte()
        private const val BACKTICK: Byte = '`'.code.toByte()
        private const val TILDE: Byte = '~'.code.toByte()
    }
}
