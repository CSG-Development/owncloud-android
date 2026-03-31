package com.owncloud.android.presentation.tags

import android.os.Bundle
import android.view.Menu
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.ui.activity.FileDisplayActivity

class TagsActivity : FileDisplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileListOption = FileListOption.TAG_FILES
        setBottomBarVisibility(false)

        updateStandardToolbar(
            title = getString(R.string.drawer_menu_tags),
            homeButtonDisplayed = true,
            showBackArrow = true,
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.left_fragment_container, TagsFragment.newInstance(), TAG_TAGS_LIST)
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                updateStandardToolbar(
                    title = getString(R.string.drawer_menu_tags),
                    homeButtonDisplayed = true,
                    showBackArrow = true,
                )
            }
        }
    }

    fun showTagFiles(serverTagId: String, tagName: String) {
        updateStandardToolbar(
            title = tagName,
            homeButtonDisplayed = true,
            showBackArrow = true,
        )

        supportFragmentManager.beginTransaction()
            .replace(R.id.left_fragment_container, TagFilesFragment.newInstance(serverTagId, tagName), TAG_TAG_FILES)
            .addToBackStack(null)
            .commit()
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
            restoreToolbarForCurrentFragment()
            setBottomBarVisibility(false)
            return
        }

        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
    }

    private fun restoreToolbarForCurrentFragment() {
        val tagFilesFragment = supportFragmentManager.findFragmentByTag(TAG_TAG_FILES) as? TagFilesFragment
        if (tagFilesFragment != null) {
            updateStandardToolbar(title = tagFilesFragment.tagName, homeButtonDisplayed = true, showBackArrow = true)
        } else {
            updateStandardToolbar(title = getString(R.string.drawer_menu_tags), homeButtonDisplayed = true, showBackArrow = true)
        }
    }

    companion object {
        private const val TAG_TAGS_LIST = "TAG_TAGS_LIST"
        private const val TAG_TAG_FILES = "TAG_TAG_FILES"
        private const val SECOND_FRAGMENT_TAG = "SECOND_FRAGMENT"
    }
}
