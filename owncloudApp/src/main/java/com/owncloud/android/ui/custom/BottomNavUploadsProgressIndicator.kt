package com.owncloud.android.ui.custom

import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.owncloud.android.R
import com.owncloud.android.presentation.transfers.PendingUploadsIndicatorState

class BottomNavUploadsProgressIndicator(
    private val bottomNavigationView: BottomNavigationView,
) {
    private val progressIndicator: CircularProgressIndicator
    private val uploadsIndicatorSize: Int
    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        reposition()
    }
    val offset = bottomNavigationView.resources.getDimensionPixelSize(R.dimen.bottom_nav_uploads_indicator_offset)

    init {
        val context = bottomNavigationView.context
        val parent = bottomNavigationView.parent as ViewGroup
        uploadsIndicatorSize = context.resources.getDimensionPixelSize(R.dimen.bottom_nav_uploads_indicator_size)
        val trackThickness = context.resources.getDimensionPixelSize(R.dimen.bottom_nav_uploads_indicator_track_thickness)
        progressIndicator = CircularProgressIndicator(context, null, R.attr.circularProgressIndicatorStyle).apply {
            this.indicatorSize = uploadsIndicatorSize
            this.trackThickness = trackThickness
            isIndeterminate = true
            isVisible = false
            isClickable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        parent.addView(progressIndicator)
        bottomNavigationView.addOnLayoutChangeListener(layoutChangeListener)
    }

    fun update(state: PendingUploadsIndicatorState, bottomNavVisible: Boolean) {
        val visible = state.isVisible && bottomNavVisible
        progressIndicator.isVisible = visible
        if (!visible) return
        reposition()
    }

    fun reposition() {
        if (!progressIndicator.isVisible) return

        val uploadsItem = bottomNavigationView.findViewById<View>(R.id.nav_uploads) ?: return
        val iconView = uploadsItem.findViewById<View>(com.google.android.material.R.id.navigation_bar_item_icon_view)
            ?: uploadsItem

        val parent = progressIndicator.parent as? View ?: return
        val parentLocation = IntArray(2)
        val iconLocation = IntArray(2)
        parent.getLocationInWindow(parentLocation)
        iconView.getLocationInWindow(iconLocation)

        progressIndicator.x = (iconLocation[0] - parentLocation[0] + iconView.width - uploadsIndicatorSize + offset).toFloat()
        progressIndicator.y = (iconLocation[1] - parentLocation[1]).toFloat()
        progressIndicator.z = 10f
    }

    fun detach() {
        bottomNavigationView.removeOnLayoutChangeListener(layoutChangeListener)
        (progressIndicator.parent as? ViewGroup)?.removeView(progressIndicator)
    }
}
