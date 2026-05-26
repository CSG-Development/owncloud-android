package com.owncloud.android.ui.preview

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import timber.log.Timber
import java.io.File
import java.io.IOException

class PdfRendererManager(
    private val filePath: String,
    private val bitmapCache: BitmapLruCache,
) {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private val renderLock = Any()

    private var pageDisplayWidths: IntArray = intArrayOf()
    private var pageDisplayHeights: IntArray = intArrayOf()
    private var cachedDisplayTargetWidth: Int = 0

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    @Throws(IOException::class)
    fun open() {
        close()
        val localFile = File(filePath)
        if (!localFile.exists()) {
            throw IOException("PDF file does not exist at $filePath")
        }
        fileDescriptor = ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(fileDescriptor!!)
        pageDisplayWidths = IntArray(pdfRenderer!!.pageCount)
        pageDisplayHeights = IntArray(pdfRenderer!!.pageCount)
        cachedDisplayTargetWidth = 0
    }

    fun precomputeAllPageDisplayDimensions(targetWidth: Int): List<PageDisplayDimensions> {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return emptyList()
            ensureDisplayDimensionCache(renderer.pageCount, targetWidth)
            return List(renderer.pageCount) { pageIndex ->
                getOrComputePageDisplayDimensions(renderer, pageIndex, targetWidth)
            }
        }
    }

    fun getOrComputePageDisplayDimensions(pageIndex: Int, targetWidth: Int): PageDisplayDimensions {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return PageDisplayDimensions(0, 0)
            ensureDisplayDimensionCache(renderer.pageCount, targetWidth)
            return getOrComputePageDisplayDimensions(renderer, pageIndex, targetWidth)
        }
    }

    fun measurePageDisplayHeight(pageIndex: Int, targetWidth: Int): Int {
        synchronized(renderLock) {
            val renderer = pdfRenderer ?: return 0
            return renderer.openPage(pageIndex).use { page ->
                computeScaledDimensions(page.width, page.height, targetWidth).height
            }
        }
    }

    fun invalidatePageDisplayHeights() {
        synchronized(renderLock) {
            cachedDisplayTargetWidth = 0
            if (pageDisplayHeights.isNotEmpty()) {
                pageDisplayWidths = IntArray(pageDisplayWidths.size)
                pageDisplayHeights = IntArray(pageDisplayHeights.size)
            }
        }
    }

    fun clearBitmapCache() {
        synchronized(renderLock) {
            bitmapCache.evictAll()
        }
    }

    private fun ensureDisplayDimensionCache(pageCount: Int, targetWidth: Int) {
        if (pageDisplayHeights.size != pageCount) {
            pageDisplayWidths = IntArray(pageCount)
            pageDisplayHeights = IntArray(pageCount)
        }
        if (cachedDisplayTargetWidth != targetWidth) {
            pageDisplayWidths = IntArray(pageCount)
            pageDisplayHeights = IntArray(pageCount)
            cachedDisplayTargetWidth = targetWidth
        }
    }

    private fun getOrComputePageDisplayDimensions(
        renderer: PdfRenderer,
        pageIndex: Int,
        targetWidth: Int,
    ): PageDisplayDimensions {
        if (pageIndex in pageDisplayHeights.indices && pageDisplayHeights[pageIndex] > 0) {
            return PageDisplayDimensions(pageDisplayWidths[pageIndex], pageDisplayHeights[pageIndex])
        }

        val dimensions = renderer.openPage(pageIndex).use { page ->
            computeScaledDimensions(page.width, page.height, targetWidth)
        }
        if (pageIndex in pageDisplayHeights.indices) {
            pageDisplayWidths[pageIndex] = dimensions.width
            pageDisplayHeights[pageIndex] = dimensions.height
        }
        return PageDisplayDimensions(dimensions.width, dimensions.height)
    }

    fun close() {
        synchronized(renderLock) {
            bitmapCache.releaseAndClear()
            pdfRenderer?.close()
            pdfRenderer = null
            try {
                fileDescriptor?.close()
            } catch (exception: IOException) {
                Timber.w(exception, "Failed to close PDF file descriptor")
            }
            fileDescriptor = null
            pageDisplayWidths = intArrayOf()
            pageDisplayHeights = intArrayOf()
            cachedDisplayTargetWidth = 0
        }
    }

    fun getCachedPage(pageIndex: Int, targetWidth: Int): Bitmap? {
        synchronized(renderLock) {
            val bitmap = bitmapCache.get(pageIndex) ?: return null
            if (bitmap.isRecycled) {
                bitmapCache.remove(pageIndex)
                return null
            }
            val renderer = pdfRenderer ?: return null
            ensureDisplayDimensionCache(renderer.pageCount, targetWidth)
            val expectedDimensions = getOrComputePageDisplayDimensions(renderer, pageIndex, targetWidth)
            if (!isBitmapMatchingDimensions(bitmap, expectedDimensions)) {
                return null
            }
            return bitmap
        }
    }

    fun renderPage(pageIndex: Int, targetWidth: Int): Bitmap {
        getCachedPage(pageIndex, targetWidth)?.let { return it }

        synchronized(renderLock) {
            getCachedPage(pageIndex, targetWidth)?.let { return it }

            val renderer = pdfRenderer ?: throw IllegalStateException("PdfRenderer is not opened")
            renderer.openPage(pageIndex).use { page ->
                val dimensions = computeScaledDimensions(page.width, page.height, targetWidth)
                val bitmap = Bitmap.createBitmap(dimensions.width, dimensions.height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmapCache.put(pageIndex, bitmap)
                return bitmap
            }
        }
    }

    private fun isBitmapMatchingDimensions(bitmap: Bitmap, expected: PageDisplayDimensions): Boolean {
        return kotlin.math.abs(bitmap.width - expected.width) <= BITMAP_DIMENSION_TOLERANCE_PX &&
            kotlin.math.abs(bitmap.height - expected.height) <= BITMAP_DIMENSION_TOLERANCE_PX
    }

    data class PageDisplayDimensions(val width: Int, val height: Int)

    private data class ScaledDimensions(val width: Int, val height: Int)

    private fun computeScaledDimensions(pageWidth: Int, pageHeight: Int, targetWidth: Int): ScaledDimensions {
        if (pageWidth <= 0 || pageHeight <= 0 || targetWidth <= 0) {
            throw IllegalArgumentException("Invalid PDF page or target dimensions")
        }

        var scale = targetWidth.toFloat() / pageWidth
        var bitmapWidth = targetWidth
        var bitmapHeight = (pageHeight * scale).toInt().coerceAtLeast(1)

        while (bitmapWidth.toLong() * bitmapHeight > MAX_BITMAP_PIXELS) {
            scale *= BITMAP_DOWNSCALE_FACTOR
            bitmapWidth = (pageWidth * scale).toInt().coerceAtLeast(1)
            bitmapHeight = (pageHeight * scale).toInt().coerceAtLeast(1)
        }

        return ScaledDimensions(bitmapWidth, bitmapHeight)
    }

    companion object {
        private const val MAX_BITMAP_PIXELS = 16L * 1024L * 1024L
        private const val BITMAP_DOWNSCALE_FACTOR = 0.75f
        private const val BITMAP_DIMENSION_TOLERANCE_PX = 2

        fun createBitmapCache(): BitmapLruCache {
            val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            val cacheSizeKb = maxMemoryKb / 8
            return BitmapLruCache(cacheSizeKb)
        }
    }
}

class BitmapLruCache(maxSizeKb: Int) : android.util.LruCache<Int, Bitmap>(maxSizeKb) {
    private var isReleasing = false

    override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024

    override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
        // Only recycle when the renderer is closing. LRU evictions must not recycle:
        // ImageViews in the RecyclerView cache may still reference the bitmap.
        if (isReleasing && !oldValue.isRecycled) {
            oldValue.recycle()
        }
    }

    fun releaseAndClear() {
        isReleasing = true
        try {
            evictAll()
        } finally {
            isReleasing = false
        }
    }
}
