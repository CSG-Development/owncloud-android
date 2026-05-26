package com.owncloud.android.ui.preview

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ProgressBar
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
    private var currentLoadGeneration = 0
    private var targetWidthPx = 0
    private var pageCount = 0

    private val pages = mutableListOf<PdfPageUiModel>()
    private var baseHeightsPx = IntArray(0)
    private val renderJobs = mutableMapOf<Int, Job>()
    private val renderSemaphore = Semaphore(MAX_CONCURRENT_RENDERS)

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
    private var pinchTargetScale = 1f
    private var velocityTracker: VelocityTracker? = null

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
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isDragging && isHorizontalPanAvailable()) {
                        val movedX = abs(event.x - downX)
                        val movedY = abs(event.y - downY)
                        if (movedX > movedY) {
                            isDragging = true
                            lastX = event.x
                            lastY = event.y
                            rv.parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    }
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
        val nextPercent = ZOOM_PRESETS.firstOrNull { it > zoomState.displayPercent } ?: ZOOM_PRESETS.last()
        applyZoom(nextPercent / PERCENT_DIVISOR, PdfZoomMode.Custom)
    }

    fun zoomOut() {
        val previousPercent = ZOOM_PRESETS.lastOrNull { it < zoomState.displayPercent } ?: ZOOM_PRESETS.first()
        applyZoom(previousPercent / PERCENT_DIVISOR, PdfZoomMode.Custom)
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
        cancelAllRenderJobs()
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

                currentLoadGeneration++
                pageCount = openedManager.pageCount
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
                setLoadState(PdfLoadState.Error)
                resetPageNavigation()
                notifyLoadError()
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

        private val ZOOM_PRESETS = listOf(25, 50, 75, 100, 150, 200, 400)
    }
}
