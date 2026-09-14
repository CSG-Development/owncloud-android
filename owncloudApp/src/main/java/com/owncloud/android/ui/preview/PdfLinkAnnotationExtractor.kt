package com.owncloud.android.ui.preview

import android.content.Context
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class PdfLinkAnnotationExtractor(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun extract(filePath: String, expectedPageCount: Int): Array<List<PdfPageLink>> {
        if (expectedPageCount <= 0) {
            return emptyArray()
        }
        val file = File(filePath)
        if (!file.exists()) {
            Timber.w("PDF link extraction skipped; file missing: %s", file.name)
            return emptyArray()
        }

        ensureInitialized()
        val startedAtMs = SystemClock.elapsedRealtime()
        return try {
            PDDocument.load(file).use { document ->
                extractFromDocument(document, expectedPageCount, file.name, startedAtMs)
            }
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to extract PDF links from %s", file.name)
            emptyArray()
        }
    }

    private fun extractFromDocument(
        document: PDDocument,
        expectedPageCount: Int,
        fileName: String,
        startedAtMs: Long,
    ): Array<List<PdfPageLink>> {
        val pageCount = document.numberOfPages
        if (pageCount != expectedPageCount) {
            Timber.w(
                "PDF link extraction aborted for %s; page count mismatch extractor=%d renderer=%d",
                fileName,
                pageCount,
                expectedPageCount,
            )
            return emptyArray()
        }

        var keptCount = 0
        var skippedCount = 0
        val pages = Array(pageCount) { pageIndex ->
            val extracted = extractPageLinks(document.getPage(pageIndex), pageIndex)
            keptCount += extracted.links.size
            skippedCount += extracted.skipped
            extracted.links
        }

        Timber.d(
            "Extracted %d http(s) PDF links (%d skipped) from %s (%d pages) in %dms",
            keptCount,
            skippedCount,
            fileName,
            pageCount,
            SystemClock.elapsedRealtime() - startedAtMs,
        )
        return pages
    }

    private fun extractPageLinks(page: PDPage, pageIndex: Int): PageLinkExtraction {
        val cropBox = page.cropBox
        val cropWidth = cropBox.width
        val cropHeight = cropBox.height
        if (cropWidth <= 0f || cropHeight <= 0f) {
            Timber.w("PDF page %d has invalid crop box; skipping link extraction", pageIndex)
            return PageLinkExtraction(emptyList(), skipped = 0)
        }
        val rotation = normalizeRotation(page.rotation)
        val links = mutableListOf<PdfPageLink>()
        var skipped = 0

        val annotations = try {
            page.annotations
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to read annotations on PDF page %d", pageIndex)
            return PageLinkExtraction(emptyList(), skipped = 0)
        }

        for (annotation in annotations) {
            val linkAnnotation = annotation as? PDAnnotationLink ?: continue
            val uriAction = linkAnnotation.action as? PDActionURI
            if (uriAction == null) {
                skipped++
                continue
            }
            val rawUri = uriAction.getURI()
            val uri = parseHttpUri(rawUri)
            if (uri == null) {
                skipped++
                Timber.d("Skipping non-http PDF link on page %d: %s", pageIndex, rawUri)
                continue
            }
            val bounds = mapAnnotationBounds(linkAnnotation, cropBox, rotation)
            if (bounds.isEmpty()) {
                skipped++
                Timber.d("Skipping PDF link with empty bounds on page %d: %s", pageIndex, uri)
                continue
            }
            links += PdfPageLink(uri = uri, normalizedBounds = bounds)
            Timber.d("PDF link page=%d uri=%s bounds=%d", pageIndex, uri, bounds.size)
        }
        return PageLinkExtraction(links, skipped)
    }

    private fun mapAnnotationBounds(
        annotation: PDAnnotationLink,
        cropBox: PDRectangle,
        rotation: Int,
    ): List<RectF> {
        val quadPoints = annotation.quadPoints
        if (quadPoints != null && quadPoints.size >= QUAD_POINT_VALUES && quadPoints.size % QUAD_POINT_VALUES == 0) {
            val quads = ArrayList<RectF>(quadPoints.size / QUAD_POINT_VALUES)
            var offset = 0
            while (offset < quadPoints.size) {
                mapPdfPointsToNormalizedRect(
                    floatArrayOf(
                        quadPoints[offset], quadPoints[offset + 1],
                        quadPoints[offset + 2], quadPoints[offset + 3],
                        quadPoints[offset + 4], quadPoints[offset + 5],
                        quadPoints[offset + 6], quadPoints[offset + 7],
                    ),
                    cropBox,
                    rotation,
                )?.let { quads += it }
                offset += QUAD_POINT_VALUES
            }
            if (quads.isNotEmpty()) {
                return quads
            }
        }

        val rectangle = annotation.rectangle ?: return emptyList()
        return listOfNotNull(
            mapPdfPointsToNormalizedRect(
                floatArrayOf(
                    rectangle.lowerLeftX, rectangle.lowerLeftY,
                    rectangle.upperRightX, rectangle.lowerLeftY,
                    rectangle.upperRightX, rectangle.upperRightY,
                    rectangle.lowerLeftX, rectangle.upperRightY,
                ),
                cropBox,
                rotation,
            ),
        )
    }

    private fun mapPdfPointsToNormalizedRect(
        xyPairs: FloatArray,
        cropBox: PDRectangle,
        rotation: Int,
    ): RectF? {
        val cropWidth = cropBox.width
        val cropHeight = cropBox.height
        val displayWidth = displayWidth(cropWidth, cropHeight, rotation)
        val displayHeight = displayHeight(cropWidth, cropHeight, rotation)
        if (displayWidth <= 0f || displayHeight <= 0f) {
            return null
        }

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var index = 0
        while (index < xyPairs.size) {
            val x = xyPairs[index] - cropBox.lowerLeftX
            val y = xyPairs[index + 1] - cropBox.lowerLeftY
            val displayX = toDisplayX(x, y, cropWidth, cropHeight, rotation)
            val displayY = toDisplayY(x, y, cropWidth, cropHeight, rotation)
            minX = minOf(minX, displayX)
            minY = minOf(minY, displayY)
            maxX = maxOf(maxX, displayX)
            maxY = maxOf(maxY, displayY)
            index += 2
        }
        if (minX == Float.POSITIVE_INFINITY) {
            return null
        }

        val normalized = RectF(
            minX / displayWidth,
            minY / displayHeight,
            maxX / displayWidth,
            maxY / displayHeight,
        )
        val left = normalized.left.coerceIn(0f, 1f)
        val top = normalized.top.coerceIn(0f, 1f)
        val right = normalized.right.coerceIn(0f, 1f)
        val bottom = normalized.bottom.coerceIn(0f, 1f)
        if (right <= left || bottom <= top) {
            return null
        }
        return RectF(left, top, right, bottom)
    }

    private fun ensureInitialized() {
        if (isResourceLoaderInitialized.get()) {
            return
        }
        synchronized(initLock) {
            if (isResourceLoaderInitialized.get()) {
                return
            }
            PDFBoxResourceLoader.init(appContext)
            isResourceLoaderInitialized.set(true)
            Timber.d("Initialized PDFBox resource loader")
        }
    }

    private data class PageLinkExtraction(
        val links: List<PdfPageLink>,
        val skipped: Int,
    )

    companion object {
        private const val QUAD_POINT_VALUES = 8
        private val initLock = Any()
        private val isResourceLoaderInitialized = AtomicBoolean(false)

        private fun normalizeRotation(rotation: Int): Int {
            val normalized = ((rotation % 360) + 360) % 360
            return when (normalized) {
                90, 180, 270 -> normalized
                else -> 0
            }
        }

        private fun displayWidth(cropWidth: Float, cropHeight: Float, rotation: Int): Float =
            if (rotation == 90 || rotation == 270) cropHeight else cropWidth

        private fun displayHeight(cropWidth: Float, cropHeight: Float, rotation: Int): Float =
            if (rotation == 90 || rotation == 270) cropWidth else cropHeight

        private fun toDisplayX(x: Float, y: Float, cropWidth: Float, cropHeight: Float, rotation: Int): Float =
            when (rotation) {
                90 -> y
                180 -> cropWidth - x
                270 -> cropHeight - y
                else -> x
            }

        private fun toDisplayY(x: Float, y: Float, cropWidth: Float, cropHeight: Float, rotation: Int): Float =
            when (rotation) {
                90 -> x
                180 -> y
                270 -> cropWidth - x
                else -> cropHeight - y
            }

        private fun parseHttpUri(rawUri: String?): Uri? {
            val trimmed = rawUri?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return null
            }
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US)
            if (scheme != "http" && scheme != "https") {
                return null
            }
            if (uri.host.isNullOrBlank()) {
                return null
            }
            return uri
        }
    }
}
