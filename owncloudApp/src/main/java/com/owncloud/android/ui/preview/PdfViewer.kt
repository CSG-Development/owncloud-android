package com.owncloud.android.ui.preview

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.owncloud.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class PdfViewer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onLoadStateChanged(state: PdfLoadState)
        fun onPageChanged(navigation: PdfPageNavigationState)
        fun onZoomChanged(zoom: PdfZoomState)
        fun onLoadError()
        fun onExternalLinkClicked(uri: Uri) = Unit
    }

    var listener: Listener? = null

    private val pdfPages: RecyclerView
    private val pdfLoading: ProgressBar
    private val layoutManager: LinearLayoutManager?
        get() = pdfPages.layoutManager as? LinearLayoutManager

    private val pdfPageAdapter = PdfPageAdapter()
    private var viewScope: CoroutineScope? = null

    private var storagePath: String? = null
    private var pdfRendererManager: PdfRendererManager? = null
    private var loadJob: Job? = null
    private var extractLinksJob: Job? = null
    private var currentLoadGeneration = 0
    private var targetWidthPx = 0
    private var pageCount = 0

    private val pages = mutableListOf<PdfPageUiModel>()
    private var pageLinks: Array<List<PdfPageLink>> = emptyArray()
    private var baseHeightsPx = IntArray(0)
    private val renderJobs = mutableMapOf<Int, Job>()
    private val renderSemaphore = Semaphore(MAX_CONCURRENT_RENDERS)
    private val linkExtractor by lazy { PdfLinkAnnotationExtractor(context) }

    private var appliedZoomScale = 1f
    private var appliedZoomMode = PdfZoomMode.FitWidth
    private var horizontalOffsetPx = 0f
    private var pendingZoomVerticalScroll = 0f

    private var pageNavigation = PdfPageNavigationState()
    private var zoomState = PdfZoomState()
    private var loadState: PdfLoadState = PdfLoadState.Idle

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var isDragging = false
    private var tapInvalid = false
    private var pinchTargetScale = 1f
    private var velocityTracker: VelocityTracker? = null
    private var linkHighlightJob: Job? = null

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            applyHorizontalOffsetToVisibleChildren()
            notifyVisiblePagesChanged()
        }
    }

    private val touchListener = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(rv: RecyclerView, event: MotionEvent): Boolean {
            scaleGestureDetector.onTouchEvent(event)
            trackVelocity(event)
            if (scaleGestureDetector.isInProgress) {
                tapInvalid = true
                rv.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastX = event.x
                    lastY = event.y
                    isDragging = false
                    tapInvalid = false
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    tapInvalid = true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!tapInvalid && hasMovedPastTapSlop(event)) {
                        tapInvalid = true
                    }
                    if (!isDragging && isHorizontalPanAvailable()) {
                        val movedX = abs(event.x - downX)
                        val movedY = abs(event.y - downY)
                        if (movedX > movedY) {
                            isDragging = true
                            tapInvalid = true
                            lastX = event.x
                            lastY = event.y
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    val isTap = !tapInvalid && !isDragging
                    if (isTap && handleLinkTap(rv, event)) {
                        return true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    tapInvalid = true
                }
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, event: MotionEvent) {
            scaleGestureDetector.onTouchEvent(event)
            trackVelocity(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_POINTER_UP,
                    -> {
                    lastX = event.x
                    lastY = event.y
                }

                MotionEvent.ACTION_MOVE -> {
                    if (scaleGestureDetector.isInProgress) {
                        lastX = event.x
                        lastY = event.y
                    } else {
                        if (!isDragging && isHorizontalPanAvailable()) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val dx = event.x - lastX
                            val dy = event.y - lastY
                            lastX = event.x
                            lastY = event.y
                            applyHorizontalPan(dx)
                            rv.scrollBy(0, (-dy).roundToInt())
                        } else {
                            lastX = event.x
                            lastY = event.y
                        }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        flingVerticallyFromTracker(rv)
                    }
                    isDragging = false
                    releaseVelocityTracker()
                }

                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    releaseVelocityTracker()
                }
            }
        }

        private fun trackVelocity(event: MotionEvent) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                releaseVelocityTracker()
                velocityTracker = VelocityTracker.obtain()
            }
            velocityTracker?.addMovement(event)
        }

        private fun flingVerticallyFromTracker(rv: RecyclerView) {
            val tracker = velocityTracker ?: return
            tracker.computeCurrentVelocity(VELOCITY_UNITS_MS, maxFlingVelocity.toFloat())
            val velocityY = tracker.yVelocity
            if (abs(velocityY) > minFlingVelocity) {
                // The drag was driven manually via scrollBy, so continue the momentum
                // with the RecyclerView's native fling to preserve fling at any zoom level.
                rv.fling(0, -velocityY.toInt())
            }
        }

        private fun releaseVelocityTracker() {
            velocityTracker?.recycle()
            velocityTracker = null
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_pdf_viewer, this, true)
        pdfPages = findViewById(R.id.pdf_pages)
        pdfLoading = findViewById(R.id.pdf_loading)

        pdfPages.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = pdfPageAdapter
            setItemViewCacheSize(RECYCLER_VIEW_CACHE_SIZE)
            (layoutManager as LinearLayoutManager).initialPrefetchItemCount = RECYCLER_VIEW_PREFETCH_COUNT
        }

        scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    if (loadState !is PdfLoadState.Ready) {
                        return false
                    }
                    pinchTargetScale = appliedZoomScale
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    pinchTargetScale = (pinchTargetScale * detector.scaleFactor).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                    applyZoom(pinchTargetScale, PdfZoomMode.Custom, detector.focusX, detector.focusY)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    resetVisualZoomScale()
                    applyZoom(pinchTargetScale, PdfZoomMode.Custom)
                }
            },
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        pdfPages.addOnScrollListener(scrollListener)
        pdfPages.addOnItemTouchListener(touchListener)
    }

    override fun onDetachedFromWindow() {
        loadJob?.cancel()
        extractLinksJob?.cancel()
        extractLinksJob = null
        pageLinks = emptyArray()
        cancelLinkHighlight()
        cancelAllRenderJobs()
        pdfRendererManager?.close()
        pdfRendererManager = null
        viewScope?.cancel()
        viewScope = null
        pdfPages.removeOnScrollListener(scrollListener)
        pdfPages.removeOnItemTouchListener(touchListener)
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    fun loadPdf(storagePath: String) {
        this.storagePath = storagePath
        if (storagePath.isBlank()) {
            notifyLoadError()
            return
        }
        val width = resolveTargetWidth()
        if (width <= 0) {
            post { loadPdf(storagePath) }
            return
        }
        reloadPreview(storagePath, width)
    }

    fun reload() {
        val path = storagePath
        if (path.isNullOrBlank()) {
            notifyLoadError()
            return
        }
        val width = resolveTargetWidth()
        if (width <= 0) {
            post { reload() }
            return
        }
        reloadPreview(path, width)
    }

    fun previousPage() {
        if (!pageNavigation.canGoPrevious) {
            return
        }
        val currentPage = layoutManager?.findFirstCompletelyVisibleItemPosition().takeIf { it != NO_POSITION }
            ?: layoutManager?.findFirstVisibleItemPosition().takeIf { it != NO_POSITION }
            ?: pageNavigation.currentPage
        val previousPage = minOf(currentPage - 1, pageNavigation.currentPage - 1).coerceAtLeast(0)
        scrollToPage(previousPage)
    }

    fun nextPage() {
        if (!pageNavigation.canGoNext) {
            return
        }
        val currentPage = layoutManager?.findLastCompletelyVisibleItemPosition().takeIf { it != NO_POSITION }
            ?: layoutManager?.findLastVisibleItemPosition().takeIf { it != NO_POSITION }
            ?: pageNavigation.currentPage
        val nextPage = maxOf(currentPage + 1, pageNavigation.currentPage + 1).coerceAtMost(pageNavigation.pageCount - 1)
        scrollToPage(nextPage)
    }

    fun zoomIn() {
        val currentSnapped = snapToStep(zoomState.displayPercent)
        val nextPercent = (currentSnapped + ZOOM_STEP)
            .coerceAtMost(MAX_ZOOM_SCALE * PERCENT_DIVISOR)

        applyZoom(nextPercent / PERCENT_DIVISOR, PdfZoomMode.Custom)
    }

    fun zoomOut() {
        val currentSnapped = snapToStep(zoomState.displayPercent)
        val previousPercent = (currentSnapped - ZOOM_STEP)
            .coerceAtLeast(MIN_ZOOM_SCALE * PERCENT_DIVISOR)

        applyZoom(previousPercent / PERCENT_DIVISOR, PdfZoomMode.Custom)
    }

    private fun snapToStep(percent: Int): Float {
        return (percent + ZOOM_STEP / 2) / ZOOM_STEP * ZOOM_STEP.toFloat()
    }

    fun setZoomPreset(percent: Int) {
        applyZoom(percent / PERCENT_DIVISOR, PdfZoomMode.Custom)
    }

    fun fitWidth() {
        applyZoom(1f, PdfZoomMode.FitWidth)
    }

    fun fitPage() {
        if (loadState !is PdfLoadState.Ready) {
            return
        }
        val viewportHeight = viewportHeightPx
        val baseHeight = baseHeightsPx.getOrNull(dominantVisiblePageIndex) ?: return
        if (viewportHeight <= 0 || baseHeight <= 0) {
            return
        }
        applyZoom(viewportHeight.toFloat() / baseHeight, PdfZoomMode.FitPage)
    }

    private fun scrollToPage(pageIndex: Int) {
        pdfPages.post {
            pdfPages.smoothScrollToPosition(pageIndex)
        }
    }

    private fun reloadPreview(path: String, width: Int) {
        val scope = viewScope ?: return
        loadJob?.cancel()
        extractLinksJob?.cancel()
        extractLinksJob = null
        cancelAllRenderJobs()
        cancelLinkHighlight()
        currentLoadGeneration++
        pageLinks = emptyArray()
        resetZoom()
        targetWidthPx = width

        loadJob = scope.launch {
            setLoadState(PdfLoadState.Loading)
            resetPageNavigation()
            pdfRendererManager?.close()
            pdfRendererManager = null

            try {
                val openedManager = PdfRendererManager(path, PdfRendererManager.createBitmapCache())
                try {
                    withContext(Dispatchers.IO) {
                        openedManager.open()
                    }
                } catch (throwable: Throwable) {
                    openedManager.close()
                    throw throwable
                }
                // From here the field owns the renderer; it is closed on the next reload or detach.
                pdfRendererManager = openedManager

                pageCount = openedManager.pageCount
                val generation = currentLoadGeneration
                startLinkExtraction(path, pageCount, generation)
                val pageDisplayDimensions = withContext(Dispatchers.IO) {
                    openedManager.precomputeAllPageDisplayDimensions(width)
                }
                baseHeightsPx = IntArray(pageCount) { pageIndex ->
                    pageDisplayDimensions.getOrNull(pageIndex)?.height ?: 0
                }
                pages.clear()
                pages.addAll(
                    List(pageCount) { pageIndex ->
                        PdfPageUiModel(
                            pageIndex = pageIndex,
                            displayWidthPx = width,
                            displayHeightPx = baseHeightsPx[pageIndex],
                            content = PdfPageContent.Loading,
                        )
                    },
                )
                setLoadState(PdfLoadState.Ready(pageCount, width))
                initializePageNavigation(pageCount)
                submitPagesToAdapter()
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to load PDF preview")
                extractLinksJob?.cancel()
                extractLinksJob = null
                currentLoadGeneration++
                pageLinks = emptyArray()
                setLoadState(PdfLoadState.Error)
                resetPageNavigation()
                notifyLoadError()
            }
        }
    }

    private fun startLinkExtraction(path: String, expectedPageCount: Int, generation: Int) {
        val scope = viewScope ?: return
        extractLinksJob?.cancel()
        Timber.d("Starting PDF link extraction for %d pages (generation=%d)", expectedPageCount, generation)
        extractLinksJob = scope.launch(Dispatchers.IO) {
            val extracted = try {
                linkExtractor.extract(path, expectedPageCount)
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                Timber.e(exception, "PDF link extraction failed")
                emptyArray()
            }
            if (generation != currentLoadGeneration) {
                Timber.d("Discarding stale PDF link extraction (generation=%d current=%d)", generation, currentLoadGeneration)
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                if (generation != currentLoadGeneration) {
                    Timber.d(
                        "Discarding PDF links; generation changed (%d -> %d)",
                        generation,
                        currentLoadGeneration,
                    )
                    return@withContext
                }
                pageLinks = extracted
                val pagesWithLinks = pageLinks.count { it.isNotEmpty() }
                Timber.d(
                    "Cached PDF links for %d pages (%d with links, generation=%d)",
                    pageLinks.size,
                    pagesWithLinks,
                    generation,
                )
            }
        }
    }

    private fun notifyVisiblePagesChanged() {
        val ready = loadState as? PdfLoadState.Ready ?: return
        val manager = pdfRendererManager ?: return
        if (pages.isEmpty()) {
            return
        }
        val layoutManager = layoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
            return
        }
        val dominantPageIndex = resolveDominantVisiblePageIndex(layoutManager, firstVisible, lastVisible)

        val generation = currentLoadGeneration
        val baseWidth = ready.targetWidthPx
        val startIndex = firstVisible.coerceAtLeast(0)
        val endIndex = (lastVisible + PREFETCH_AHEAD).coerceAtMost(pages.lastIndex)
        val scope = viewScope ?: return

        for (pageIndex in startIndex..endIndex) {
            val page = pages.getOrNull(pageIndex) ?: continue
            val content = page.content
            if (content is PdfPageContent.Rendered && !content.bitmap.isRecycled) {
                continue
            }
            if (renderJobs[pageIndex]?.isActive == true) {
                continue
            }
            renderJobs[pageIndex] = scope.launch(Dispatchers.IO) {
                try {
                    val bitmap = renderSemaphore.withPermit {
                        manager.renderPage(pageIndex, baseWidth)
                    }
                    if (generation == currentLoadGeneration) {
                        updatePageContent(pageIndex, PdfPageContent.Rendered(bitmap))
                    }
                } catch (cancellationException: CancellationException) {
                    throw cancellationException
                } catch (exception: Exception) {
                    Timber.e(exception, "Failed to render PDF page $pageIndex")
                    if (generation == currentLoadGeneration) {
                        updatePageContent(pageIndex, PdfPageContent.Failed)
                    }
                } finally {
                    renderJobs.remove(pageIndex)
                }
            }
        }

        updatePageNavigationFromScroll(dominantPageIndex, ready.pageCount)
    }

    private fun applyZoom(
        newScale: Float,
        mode: PdfZoomMode,
        focusX: Float = viewportWidthPx / 2f,
        focusY: Float = viewportHeightPx / 2f,
    ) {
        val clampedScale = newScale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
        loadState as? PdfLoadState.Ready ?: return

        if (abs(clampedScale - appliedZoomScale) < ZOOM_SCALE_EPSILON && mode == appliedZoomMode) {
            updateZoomState(clampedScale, mode)
            return
        }

        val oldScale = appliedZoomScale
        val ratio = if (oldScale > 0f) clampedScale / oldScale else 1f
        val oldContentWidth = (targetWidthPx * oldScale).roundToInt().coerceAtLeast(1)
        val anchorTop = layoutManager?.findFirstVisibleItemPosition()
            ?.takeIf { it != NO_POSITION }
            ?.let { layoutManager?.findViewByPosition(it)?.top } ?: 0

        appliedZoomScale = clampedScale
        appliedZoomMode = mode

        val contentWidth = (targetWidthPx * clampedScale).roundToInt().coerceAtLeast(1)
        val viewport = viewportWidthPx
        // Keep the content point under the focal X fixed instead of recentering on every zoom.
        val contentLeft = if (oldContentWidth <= viewport) (viewport - oldContentWidth) / 2f else -horizontalOffsetPx
        val newContentLeft = focusX - ratio * (focusX - contentLeft)
        horizontalOffsetPx = if (contentWidth <= viewport) {
            0f
        } else {
            (-newContentLeft).coerceIn(0f, (contentWidth - viewport).toFloat())
        }

        // The vertical anchor (first visible item top) is preserved by LinearLayoutManager across the
        // relayout, so scroll afterwards to keep the focal Y point fixed under the fingers.
        pendingZoomVerticalScroll = (ratio - 1f) * (focusY - anchorTop)

        for (index in pages.indices) {
            val page = pages[index]
            val displayHeight = (baseHeightsPx.getOrElse(index) { 0 } * clampedScale).roundToInt().coerceAtLeast(1)
            pages[index] = page.copy(displayWidthPx = contentWidth, displayHeightPx = displayHeight)
        }

        updateZoomState(clampedScale, mode)
        submitPagesToAdapter()
    }

    private fun submitPagesToAdapter() {
        // The current scroll position is intentionally preserved: LinearLayoutManager keeps the
        // first visible item anchored across the relayout, so zooming does not reposition pages.
        pdfPageAdapter.submitList(pages.toList()) {
            resetVisualZoomScale()
            if (pendingZoomVerticalScroll != 0f) {
                pdfPages.scrollBy(0, pendingZoomVerticalScroll.roundToInt())
                pendingZoomVerticalScroll = 0f
            }
            applyHorizontalOffsetToVisibleChildren()
            notifyVisiblePagesChanged()
        }
    }

    private fun updatePageContent(pageIndex: Int, content: PdfPageContent) {
        post {
            if (pageIndex !in pages.indices) {
                return@post
            }
            pages[pageIndex] = pages[pageIndex].copy(content = content)
            pdfPageAdapter.submitList(pages.toList()) {
                applyHorizontalOffsetToVisibleChildren()
            }
        }
    }

    private fun applyHorizontalPan(dx: Float) {
        val contentWidth = currentContentWidthPx
        val maxPan = max(0f, (contentWidth - viewportWidthPx).toFloat())
        if (maxPan <= 0f) {
            return
        }
        horizontalOffsetPx = (horizontalOffsetPx - dx).coerceIn(0f, maxPan)
        applyHorizontalOffsetToVisibleChildren()
    }

    private fun applyHorizontalOffsetToVisibleChildren() {
        val viewport = viewportWidthPx
        val contentWidth = currentContentWidthPx
        val translationX = if (contentWidth <= viewport) {
            (viewport - contentWidth) / 2f
        } else {
            -horizontalOffsetPx.coerceIn(0f, (contentWidth - viewport).toFloat())
        }
        pdfPages.post {
            for (index in 0 until pdfPages.childCount) {
                val child = pdfPages.getChildAt(index)
                val pageContent = child.findViewById<View>(R.id.pdf_page_content) ?: continue
                pageContent.translationX = translationX
            }
        }
    }

    private fun resetVisualZoomScale() {
        pdfPages.scaleX = 1f
        pdfPages.scaleY = 1f
    }

    private fun hasMovedPastTapSlop(event: MotionEvent): Boolean {
        return abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop
    }

    private fun handleLinkTap(rv: RecyclerView, event: MotionEvent): Boolean {
        val child = rv.findChildViewUnder(event.x, event.y) ?: return false
        val pageIndex = rv.getChildAdapterPosition(child)
        if (pageIndex == NO_POSITION) {
            return false
        }
        val links = pageLinks.getOrNull(pageIndex).orEmpty()
        if (links.isEmpty()) {
            return false
        }

        val pageContent = child.findViewById<View>(R.id.pdf_page_content) ?: return false
        val contentWidth = pageContent.width.toFloat()
        val contentHeight = pageContent.height.toFloat()
        if (contentWidth <= 0f || contentHeight <= 0f) {
            return false
        }

        val xInContent = event.x - child.left - pageContent.left - pageContent.translationX
        val yInContent = event.y - child.top - pageContent.top
        if (xInContent < 0f || yInContent < 0f || xInContent > contentWidth || yInContent > contentHeight) {
            Timber.d("PDF tap outside page content page=%d", pageIndex)
            return false
        }

        val normalizedX = xInContent / contentWidth
        val normalizedY = yInContent / contentHeight
        val hit = links.firstOrNull { link ->
            link.normalizedBounds.any { bounds -> bounds.contains(normalizedX, normalizedY) }
        }
        if (hit == null) {
            Timber.d("PDF tap missed links page=%d x=%.3f y=%.3f", pageIndex, normalizedX, normalizedY)
            return false
        }

        Timber.d("PDF link hit page=%d uri=%s", pageIndex, hit.uri)
        showLinkHighlightAndOpen(pageContent, hit)
        return true
    }

    private fun showLinkHighlightAndOpen(pageContent: View, link: PdfPageLink) {
        val drawable = PdfLinkHighlightDrawable(
            normalizedBounds = link.normalizedBounds,
            color = ColorUtils.setAlphaComponent(
                ContextCompat.getColor(context, R.color.homecloud_link),
                LINK_HIGHLIGHT_ALPHA,
            ),
        )
        Timber.d("Showing PDF link highlight overlays=%d uri=%s", link.normalizedBounds.size, link.uri)
        cancelLinkHighlight()
        linkHighlightJob = viewScope?.launch {
            pageContent.foreground = drawable
            val fade = ObjectAnimator.ofInt(drawable, "alpha", 255, 0).apply {
                duration = LINK_HIGHLIGHT_FADE_MS
            }
            try {
                delay(LINK_HIGHLIGHT_HOLD_MS)
                fade.start()
                listener?.onExternalLinkClicked(link.uri)
                delay(LINK_HIGHLIGHT_FADE_MS)
            } finally {
                fade.cancel()
                if (pageContent.foreground === drawable) {
                    pageContent.foreground = null
                }
            }
        }
    }

    private fun cancelLinkHighlight() {
        linkHighlightJob?.cancel()
        linkHighlightJob = null
    }

    private fun isHorizontalPanAvailable(): Boolean = currentContentWidthPx > viewportWidthPx

    private val currentContentWidthPx: Int
        get() = (targetWidthPx * appliedZoomScale).roundToInt().coerceAtLeast(1)

    private val viewportWidthPx: Int
        get() = pdfPages.width - pdfPages.paddingLeft - pdfPages.paddingRight

    private val viewportHeightPx: Int
        get() = pdfPages.height - pdfPages.paddingTop - pdfPages.paddingBottom

    private val dominantVisiblePageIndex: Int
        get() {
            val layoutManager = layoutManager ?: return 0
            val firstVisible = layoutManager.findFirstVisibleItemPosition()
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (firstVisible == RecyclerView.NO_POSITION || lastVisible == RecyclerView.NO_POSITION) {
                return 0
            }
            return resolveDominantVisiblePageIndex(layoutManager, firstVisible, lastVisible)
        }

    private fun resolveDominantVisiblePageIndex(
        layoutManager: LinearLayoutManager,
        firstVisible: Int,
        lastVisible: Int,
    ): Int {
        if (firstVisible == lastVisible) {
            return firstVisible
        }
        val viewportTop = pdfPages.paddingTop
        val viewportBottom = pdfPages.height - pdfPages.paddingBottom
        val viewportCenter = (viewportTop + viewportBottom) / 2

        var dominantPageIndex = firstVisible
        var closestDistance = Int.MAX_VALUE
        for (index in firstVisible..lastVisible) {
            val child = layoutManager.findViewByPosition(index) ?: continue
            val childCenter = (child.top + child.bottom) / 2
            val distance = abs(childCenter - viewportCenter)
            if (distance < closestDistance) {
                closestDistance = distance
                dominantPageIndex = index
            }
        }
        return dominantPageIndex
    }

    private fun resolveTargetWidth(): Int {
        val measuredWidth = pdfPages.width - pdfPages.paddingLeft - pdfPages.paddingRight
        if (measuredWidth > 0) {
            return measuredWidth
        }
        val horizontalMargins = resources.getDimensionPixelSize(R.dimen.standard_margin) * 2
        return resources.displayMetrics.widthPixels - horizontalMargins
    }

    private fun updateZoomState(zoomScale: Float, mode: PdfZoomMode) {
        val clampedScale = zoomScale.coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
        val displayPercent = (clampedScale * PERCENT_DIVISOR).roundToInt()
        zoomState = PdfZoomState(
            zoomScale = clampedScale,
            displayPercent = displayPercent,
            mode = mode,
            canZoomIn = displayPercent < (MAX_ZOOM_SCALE * PERCENT_DIVISOR).roundToInt(),
            canZoomOut = displayPercent > (MIN_ZOOM_SCALE * PERCENT_DIVISOR).roundToInt(),
        )
        Timber.d("updateZoomState: $zoomState")
        listener?.onZoomChanged(zoomState)
    }

    private fun resetZoom() {
        appliedZoomScale = 1f
        appliedZoomMode = PdfZoomMode.FitWidth
        horizontalOffsetPx = 0f
        resetVisualZoomScale()
        zoomState = PdfZoomState()
        listener?.onZoomChanged(zoomState)
    }

    private fun cancelAllRenderJobs() {
        renderJobs.values.forEach { it.cancel() }
        renderJobs.clear()
    }

    private fun resetPageNavigation() {
        pageNavigation = PdfPageNavigationState()
        listener?.onPageChanged(pageNavigation)
    }

    private fun initializePageNavigation(count: Int) {
        pageNavigation = PdfPageNavigationState(
            currentPage = 0,
            pageCount = count,
            canGoPrevious = false,
            canGoNext = count > 1,
        )
        listener?.onPageChanged(pageNavigation)
    }

    private fun updatePageNavigationFromScroll(dominantPageIndex: Int, count: Int) {
        if (dominantPageIndex < 0 || count <= 0) {
            return
        }
        val currentPage = dominantPageIndex.coerceIn(0, count - 1)
        val newNavigation = pageNavigation.copy(
            currentPage = currentPage,
            pageCount = count,
            canGoPrevious = currentPage > 0,
            canGoNext = currentPage < count - 1,
        )
        if (newNavigation != pageNavigation) {
            pageNavigation = newNavigation
            listener?.onPageChanged(pageNavigation)
        }
    }

    private fun setLoadState(state: PdfLoadState) {
        loadState = state
        when (state) {
            PdfLoadState.Idle -> {
                pdfLoading.isVisible = false
                pdfPages.isVisible = false
            }

            PdfLoadState.Loading -> {
                pdfLoading.isVisible = true
                pdfPages.isVisible = false
            }

            is PdfLoadState.Ready -> {
                pdfLoading.isVisible = false
                pdfPages.isVisible = true
            }

            PdfLoadState.Error -> {
                pdfLoading.isVisible = false
                pdfPages.isVisible = false
            }
        }
        listener?.onLoadStateChanged(state)
    }

    private fun notifyLoadError() {
        listener?.onLoadError()
    }

    companion object {
        private const val RECYCLER_VIEW_CACHE_SIZE = 8
        private const val RECYCLER_VIEW_PREFETCH_COUNT = 6
        private const val MAX_CONCURRENT_RENDERS = 1
        private const val PREFETCH_AHEAD = 2
        private const val ZOOM_SCALE_EPSILON = 0.001f
        private const val PERCENT_DIVISOR = 100f
        private const val VELOCITY_UNITS_MS = 1000
        private const val MIN_ZOOM_SCALE = 0.25f
        private const val MAX_ZOOM_SCALE = 4f
        private const val ZOOM_STEP = 25
        private const val LINK_HIGHLIGHT_ALPHA = 76
        private const val LINK_HIGHLIGHT_HOLD_MS = 250L
        private const val LINK_HIGHLIGHT_FADE_MS = 200L
    }
}

private class PdfLinkHighlightDrawable(
    private val normalizedBounds: List<RectF>,
    color: Int,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    private val baseAlpha = Color.alpha(color)
    private var drawableAlpha = 255

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        if (width <= 0f || height <= 0f) {
            return
        }
        paint.alpha = baseAlpha * drawableAlpha / 255
        for (rect in normalizedBounds) {
            canvas.drawRect(
                rect.left * width,
                rect.top * height,
                rect.right * width,
                rect.bottom * height,
                paint,
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        drawableAlpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = drawableAlpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
