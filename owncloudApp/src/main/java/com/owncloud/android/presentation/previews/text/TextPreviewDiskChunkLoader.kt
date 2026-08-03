package com.owncloud.android.presentation.previews.text

import java.io.IOException
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/**
 * Reads UTF-8 ranges from a local file for [TextPreviewChunkRef]s, backed by [TextPreviewLruCache].
 * Blocking — call from an IO dispatcher.
 */
class TextPreviewDiskChunkLoader(
    private val cache: TextPreviewLruCache<String> = TextPreviewLruCache.ofCharSequence(),
) {

    fun getCached(chunkId: Int): String? = cache.get(chunkId)

    /**
     * Returns cached text or reads `[startByte, endByte)` from [path], then stores it in the LRU.
     */
    @Throws(IOException::class)
    fun load(path: String, ref: TextPreviewChunkRef): String {
        cache.get(ref.id)?.let { return it }

        val length = ref.lengthBytes
        require(length > 0L) { "Chunk ${ref.id} has empty range" }
        require(length <= Int.MAX_VALUE.toLong()) {
            "Chunk ${ref.id} is too large to load into memory: $length bytes"
        }

        val bytes = ByteArray(length.toInt())
        RandomAccessFile(path, "r").use { raf ->
            val fileLength = raf.length()
            require(ref.endByte <= fileLength) {
                "Chunk ${ref.id} endByte ${ref.endByte} exceeds file length $fileLength"
            }
            raf.seek(ref.startByte)
            raf.readFully(bytes)
        }

        val text = String(bytes, StandardCharsets.UTF_8)
        val estimatedBytes = text.length * Char.SIZE_BYTES
        if (estimatedBytes <= cache.maxBytesForCaching) {
            cache.put(ref.id, text)
        }
        return text
    }

    fun clearCache() {
        cache.clear()
    }

    fun cacheSize(): Int = cache.size()

    fun cacheBytes(): Int = cache.bytes()
}
