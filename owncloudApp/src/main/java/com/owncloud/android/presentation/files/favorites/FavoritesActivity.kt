package com.owncloud.android.presentation.files.favorites

import android.os.Bundle
import android.view.Menu
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.ui.activity.FileDisplayActivity

class FavoritesActivity : FileDisplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileListOption = FileListOption.FAVORITES
        setBottomBarVisibility(false)

        if (savedInstanceState == null) {
            updateStandardToolbar(
                title = getString(R.string.drawer_menu_favorites),
                homeButtonDisplayed = true,
                showBackArrow = true,
            )
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_fragment_container, FavoritesFragment.newInstance(), TAG_FAVORITES_LIST)
                .commit()
        } else {
            updateStandardToolbar(
                title = getString(R.string.drawer_menu_favorites),
                homeButtonDisplayed = true,
                showBackArrow = true,
            )
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                updateStandardToolbar(
                    title = getString(R.string.drawer_menu_favorites),
                    homeButtonDisplayed = true,
                    showBackArrow = true,
                )
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onBackPressed() {
        if (isDrawerOpen()) {
            super.onBackPressed()
            return
        }

        val hasSecondFragment = supportFragmentManager.findFragmentByTag(SECOND_FRAGMENT_TAG) != null
        if (hasSecondFragment) {
            super.onBackPressed()
            setBottomBarVisibility(false)
            return
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    companion object {
        private const val TAG_FAVORITES_LIST = "TAG_FAVORITES_LIST"
        private const val SECOND_FRAGMENT_TAG = "SECOND_FRAGMENT"
    }
}
