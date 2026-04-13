package com.owncloud.android.presentation.tags

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.ui.activity.FileDisplayActivity

class TagsActivity : FileDisplayActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fileListOption = FileListOption.TAG_FILES
        setBottomBarVisibility(false)

        val manageTagsFile: OCFile? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_MANAGE_TAGS_FILE, OCFile::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_MANAGE_TAGS_FILE)
        }

        if (savedInstanceState == null) {
            if (manageTagsFile != null) {
                updateStandardToolbar(
                    title = getString(R.string.manage_tags_option),
                    homeButtonDisplayed = true,
                    showBackArrow = true,
                )
                supportFragmentManager.beginTransaction()
                    .replace(R.id.left_fragment_container, ManageTagsFragment.newInstance(manageTagsFile), TAG_MANAGE_TAGS)
                    .commit()
            } else {
                updateStandardToolbar(
                    title = getString(R.string.drawer_menu_tags),
                    homeButtonDisplayed = true,
                    showBackArrow = true,
                )
                supportFragmentManager.beginTransaction()
                    .replace(R.id.left_fragment_container, TagsFragment.newInstance(), TAG_TAGS_LIST)
                    .commit()
            }
        } else if (manageTagsFile == null) {
            updateStandardToolbar(
                title = getString(R.string.drawer_menu_tags),
                homeButtonDisplayed = true,
                showBackArrow = true,
            )
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                val isManageTagsMode = supportFragmentManager.findFragmentByTag(TAG_MANAGE_TAGS) != null
                updateStandardToolbar(
                    title = getString(if (isManageTagsMode) R.string.manage_tags_option else R.string.drawer_menu_tags),
                    homeButtonDisplayed = true,
                    showBackArrow = true,
                )
            }
        }
    }

    fun showTagFiles(serverTagId: String, tagName: String) {
        updateStandardToolbar(
            title = "“${tagName}”",
            homeButtonDisplayed = true,
            showBackArrow = true,
            customIconDrawable = R.drawable.ic_close_accent
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
            updateStandardToolbar(title = tagFilesFragment.tagName, homeButtonDisplayed = true, showBackArrow = true, customIconDrawable = R.drawable.ic_close_accent)
        } else {
            updateStandardToolbar(title = getString(R.string.drawer_menu_tags), homeButtonDisplayed = true, showBackArrow = true)
        }
    }

    companion object {
        private const val TAG_TAGS_LIST = "TAG_TAGS_LIST"
        private const val TAG_TAG_FILES = "TAG_TAG_FILES"
        private const val TAG_MANAGE_TAGS = "TAG_MANAGE_TAGS"
        private const val SECOND_FRAGMENT_TAG = "SECOND_FRAGMENT"
        private const val EXTRA_MANAGE_TAGS_FILE = "EXTRA_MANAGE_TAGS_FILE"

        fun startForManageTags(context: Context, file: OCFile): Intent =
            Intent(context, TagsActivity::class.java).apply {
                putExtra(EXTRA_MANAGE_TAGS_FILE, file)
            }
    }
}
