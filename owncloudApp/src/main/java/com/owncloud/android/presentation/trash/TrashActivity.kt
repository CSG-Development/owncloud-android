package com.owncloud.android.presentation.trash

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.ui.activity.FileDisplayActivity

class TrashActivity : FileDisplayActivity(), TrashFragment.TrashToolbarListener {

    private var itemCount = 0
    private var selectedCount = 0
    private var currentViewType: ViewType = ViewType.VIEW_TYPE_LIST

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileListOption = FileListOption.TRASH
        setBottomBarVisibility(false)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_fragment_container, TrashFragment.newInstance(), TAG_TRASH_LIST)
                .commit()
        }

        updateToolbarTitle()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.trash_menu, menu)
        updateViewTypeMenuIcon(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                if (selectedCount > 0) {
                    getTrashFragment()?.clearSelection()
                } else {
                    onBackPressed()
                }
                true
            }
            R.id.action_toggle_view -> {
                getTrashFragment()?.toggleViewType()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed()
            return
        }

        val trashFragment = getTrashFragment()
        if (trashFragment?.hasSelection() == true) {
            trashFragment.clearSelection()
            return
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    override fun onSelectionChanged(itemCount: Int, selectedCount: Int) {
        this.itemCount = itemCount
        this.selectedCount = selectedCount
        updateToolbarTitle()
    }

    override fun onViewTypeChanged(viewType: ViewType) {
        currentViewType = viewType
        invalidateOptionsMenu()
    }

    private fun updateToolbarTitle() {
        if (selectedCount > 0) {
            updateStandardToolbar(
                title = resources.getQuantityString(R.plurals.items_selected_count, selectedCount, selectedCount),
                homeButtonDisplayed = true,
                showBackArrow = true,
                customIconDrawable = R.drawable.ic_close_accent,
            )
        } else {
            updateStandardToolbar(
                title = getString(R.string.homecloud_trash_title_with_count, itemCount),
                homeButtonDisplayed = true,
                showBackArrow = true,
            )
        }
    }

    private fun updateViewTypeMenuIcon(menu: Menu) {
        val toggleItem = menu.findItem(R.id.action_toggle_view) ?: return
        toggleItem.icon = AppCompatResources.getDrawable(
            this,
            currentViewType.getOppositeViewType().toDrawableRes(),
        )
    }

    private fun getTrashFragment(): TrashFragment? =
        supportFragmentManager.findFragmentByTag(TAG_TRASH_LIST) as? TrashFragment

    companion object {
        private const val TAG_TRASH_LIST = "TAG_TRASH_LIST"
    }
}
