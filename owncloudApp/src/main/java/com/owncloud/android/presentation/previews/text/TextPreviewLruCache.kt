package com.owncloud.android.presentation.previews.text

/**
 * Thread-safe LRU keyed by chunk id.
 * Evicts by access order when [maxEntries] or [maxBytes] is exceeded.
 */
class TextPreviewLruCache<T>(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
    private val sizeOf: (T) -> Int,
) {

    init {
        require(maxEntries > 0)
        require(maxBytes > 0)
    }

    /** Exposed so loaders can skip caching oversized entries. */
    val maxBytesForCaching: Int
        get() = maxBytes

    private val lock = Any()
    private var currentBytes = 0

    private val map = object : LinkedHashMap<Int, T>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, T>?): Boolean = false
    }

    fun get(chunkId: Int): T? = synchronized(lock) {
        map[chunkId]
    }

    fun put(chunkId: Int, value: T) {
        val entryBytes = sizeOf(value)
        synchronized(lock) {
            map.remove(chunkId)?.let { previous ->
                currentBytes -= sizeOf(previous)
            }
            map[chunkId] = value
            currentBytes += entryBytes
            evictWhileOverLimit()
        }
    }

    fun remove(chunkId: Int) {
        synchronized(lock) {
            map.remove(chunkId)?.let { removed ->
                currentBytes -= sizeOf(removed)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            map.clear()
            currentBytes = 0
        }
    }

    fun size(): Int = synchronized(lock) { map.size }

    fun bytes(): Int = synchronized(lock) { currentBytes }

    private fun evictWhileOverLimit() {
        val iterator = map.entries.iterator()
        while (iterator.hasNext() && (map.size > maxEntries || currentBytes > maxBytes)) {
            val eldest = iterator.next()
            currentBytes -= sizeOf(eldest.value)
            iterator.remove()
        }
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 32
        /** ~1.5 MiB of UTF-16 payload across cached CharSequence entries. */
        const val DEFAULT_MAX_BYTES = (1.5 * 1024 * 1024).toInt()

        fun <T : CharSequence> ofCharSequence(
            maxEntries: Int = DEFAULT_MAX_ENTRIES,
            maxBytes: Int = DEFAULT_MAX_BYTES,
        ): TextPreviewLruCache<T> = TextPreviewLruCache(
            maxEntries = maxEntries,
            maxBytes = maxBytes,
            sizeOf = { it.length * Char.SIZE_BYTES },
        )
    }
}
