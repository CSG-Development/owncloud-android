package com.owncloud.android.presentation.previews.text

import android.content.Context
import android.text.Spanned
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin

/**
 * Shared Markwon instance matching the legacy preview plugins
 * (tables, strikethrough, task list, HTML).
 */
object TextPreviewMarkwonFactory {
    fun create(context: Context): Markwon {
        return Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .build()
    }
}

/**
 * Renders markdown chunk strings to [Spanned] via Markwon, with an LRU cache.
 * Blocking parse/render — call from an IO / background dispatcher when used from the VM.
 */
class TextPreviewMarkdownChunkRenderer(
    context: Context,
    private val cache: TextPreviewLruCache<Spanned> = TextPreviewLruCache.ofCharSequence(),
    private val markwon: Markwon = TextPreviewMarkwonFactory.create(context),
) {

    fun markwon(): Markwon = markwon

    fun getCached(chunkId: Int): Spanned? = cache.get(chunkId)

    fun render(chunkId: Int, markdown: String): Spanned {
        cache.get(chunkId)?.let { return it }

        val spanned = markwon.toMarkdown(markdown)
        val estimatedBytes = spanned.length * Char.SIZE_BYTES
        if (estimatedBytes <= cache.maxBytesForCaching) {
            cache.put(chunkId, spanned)
        }
        return spanned
    }

    fun clearCache() {
        cache.clear()
    }
}
