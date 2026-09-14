package com.owncloud.android.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView

/**
 * Phone-landscape chrome: overlay the toolbar and bottom nav, hide them when the user
 * scrolls down, and slide them back after a delayed reverse drag (not during a fling).
 */
interface LandscapeBarsScrollSink {
    fun onLandscapeContentScroll(dy: Int, isUserDragging: Boolean)
    fun onLandscapeContentFling()
    fun onLandscapeContentScrollIdle(isAtTop: Boolean)
}

fun LandscapeBarsScrollSink.skipWhen(blocked: () -> Boolean): LandscapeBarsScrollSink =
    object : LandscapeBarsScrollSink {
        override fun onLandscapeContentScroll(dy: Int, isUserDragging: Boolean) {
            if (blocked()) return
            this@skipWhen.onLandscapeContentScroll(dy, isUserDragging)
        }

        override fun onLandscapeContentFling() {
            if (blocked()) return
            this@skipWhen.onLandscapeContentFling()
        }

        override fun onLandscapeContentScrollIdle(isAtTop: Boolean) {
            if (blocked()) return
            this@skipWhen.onLandscapeContentScrollIdle(isAtTop)
        }
    }

// Not used for now. Can be used if the host list is a RecyclerView (like Spaces, Uploads)
fun RecyclerView.addLandscapeBarsScrollListener(sink: LandscapeBarsScrollSink) {
    var dragging = false
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy == 0) return
            sink.onLandscapeContentScroll(dy, dragging)
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_DRAGGING -> dragging = true
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    dragging = false
                    sink.onLandscapeContentFling()
                }
                RecyclerView.SCROLL_STATE_IDLE -> {
                    dragging = false
                    sink.onLandscapeContentScrollIdle(!recyclerView.canScrollVertically(-1))
                }
            }
        }
    })
}

class LandscapeBarsController(
    private val parent: ConstraintLayout,
    private val topBar: View,
    private val bottomBar: View?,
    private val content: View,
    private val isLandscapePhone: Boolean,
) {
    private var overlayApplied = false
    private var autoHideEnabled = false
    private var barsVisible = true
    private var shownFraction = SHOWN
    private var targetFraction = SHOWN
    private var topBarHeight = 0
    private var bottomBarHeight = 0
    private var accumulatedDown = 0
    private var accumulatedUp = 0
    private var showPosted = false
    private var animator: ValueAnimator? = null

    private val scrollDownThresholdPx: Int =
        (SCROLL_DOWN_THRESHOLD_DP * content.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private val scrollUpThresholdPx: Int =
        (SCROLL_UP_THRESHOLD_DP * content.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private val showRunnable = Runnable {
        showPosted = false
        setBarsVisible(visible = true, animate = true)
    }

    init {
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit

            override fun onViewDetachedFromWindow(v: View) {
                cancelPendingShow()
                animator?.cancel()
            }
        })
    }

    fun enableAndShow() {
        if (!isLandscapePhone || bottomBar == null) return
        autoHideEnabled = true
        accumulatedDown = 0
        accumulatedUp = 0
        cancelPendingShow()
        runWhenBarsLaidOut {
            applyOverlayIfNeeded()
            setBarsVisible(visible = true, animate = overlayApplied && shownFraction < SHOWN)
        }
    }

    fun disableAndReset() {
        cancelPendingShow()
        autoHideEnabled = false
        accumulatedDown = 0
        accumulatedUp = 0
        if (!isLandscapePhone || bottomBar == null) return
        animator?.cancel()
        topBar.animate().cancel()
        bottomBar.animate().cancel()
        topBar.translationY = 0f
        bottomBar.translationY = 0f
        content.updatePadding(top = 0, bottom = 0)
        restoreConstraintsIfNeeded()
        barsVisible = true
        shownFraction = SHOWN
    }

    fun onScroll(dy: Int, isUserDragging: Boolean) {
        if (!autoHideEnabled) return
        when {
            dy > 0 -> {
                accumulatedUp = 0
                accumulatedDown += dy
                cancelPendingShow()
                if (accumulatedDown >= scrollDownThresholdPx) {
                    setBarsVisible(visible = false, animate = true)
                }
            }
            dy < 0 -> {
                accumulatedDown = 0
                accumulatedUp += -dy
                if (isUserDragging && accumulatedUp >= scrollUpThresholdPx && !barsVisible) {
                    scheduleShow()
                }
            }
        }
    }

    fun onFling() {
        if (!autoHideEnabled) return
        cancelPendingShow()
    }

    fun onScrollIdle(isAtTop: Boolean) {
        if (!autoHideEnabled) return
        accumulatedDown = 0
        accumulatedUp = 0
        if (isAtTop) {
            cancelPendingShow()
            setBarsVisible(visible = true, animate = true)
        }
    }

    private fun runWhenBarsLaidOut(action: () -> Unit) {
        val bar = bottomBar ?: return
        val run = {
            if (autoHideEnabled && topBar.height > 0 && bar.height > 0) {
                action()
            }
        }
        if (topBar.height > 0 && bar.height > 0) {
            run()
        } else {
            parent.doOnLayout { run() }
            bar.doOnLayout { run() }
        }
    }

    private fun applyOverlayIfNeeded() {
        val bar = bottomBar ?: return
        if (overlayApplied) return
        topBarHeight = topBar.height
        bottomBarHeight = bar.height
        if (topBarHeight == 0 || bottomBarHeight == 0) return

        val constraints = ConstraintSet()
        constraints.clone(parent)
        constraints.connect(content.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        constraints.connect(content.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        constraints.applyTo(parent)

        content.updatePadding(top = topBarHeight, bottom = bottomBarHeight)
        topBar.bringToFront()
        bar.bringToFront()
        overlayApplied = true
        shownFraction = SHOWN
        barsVisible = true
    }

    private fun restoreConstraintsIfNeeded() {
        val bar = bottomBar ?: return
        if (!overlayApplied) return
        val constraints = ConstraintSet()
        constraints.clone(parent)
        constraints.connect(content.id, ConstraintSet.TOP, topBar.id, ConstraintSet.BOTTOM)
        constraints.connect(content.id, ConstraintSet.BOTTOM, bar.id, ConstraintSet.TOP)
        constraints.applyTo(parent)
        overlayApplied = false
    }

    private fun setBarsVisible(visible: Boolean, animate: Boolean) {
        if (!overlayApplied) {
            applyOverlayIfNeeded()
            if (!overlayApplied) {
                return
            }
        }
        if (visible == barsVisible && (animator == null || animator?.isRunning != true)) {
            return
        }
        barsVisible = visible
        val target = if (visible) SHOWN else HIDDEN
        if (!animate || shownFraction == target) {
            applyFraction(target, target)
            return
        }
        if (animator?.isStarted == true && targetFraction == target) {
            return
        }
        animator?.cancel()
        val start = shownFraction
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                applyFraction(animation.animatedValue as Float, target)
            }
            targetFraction = target
        }
        animator?.start()
    }

    private fun applyFraction(fraction: Float, targetFraction: Float) {
        shownFraction = fraction
        this.targetFraction = targetFraction
        val hidden = 1f - fraction
        topBar.translationY = -topBarHeight * hidden
        bottomBar?.translationY = bottomBarHeight * hidden
        content.updatePadding(
            top = (topBarHeight * fraction).toInt(),
            bottom = (bottomBarHeight * fraction).toInt(),
        )
    }

    private fun scheduleShow() {
        if (showPosted || barsVisible) return
        showPosted = true
        content.postDelayed(showRunnable, SHOW_DELAY_MS)
    }

    private fun cancelPendingShow() {
        content.removeCallbacks(showRunnable)
        showPosted = false
    }

    companion object {
        private const val ANIMATION_DURATION_MS = 200L
        private const val SHOW_DELAY_MS = 150L
        private const val SCROLL_UP_THRESHOLD_DP = 32f
        private const val SCROLL_DOWN_THRESHOLD_DP = 8f
        private const val SHOWN = 1f
        private const val HIDDEN = 0f
    }
}
