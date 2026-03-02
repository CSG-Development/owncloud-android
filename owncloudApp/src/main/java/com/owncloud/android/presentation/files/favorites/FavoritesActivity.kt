package com.owncloud.android.presentation.files.favorites

import android.os.Bundle
import com.owncloud.android.R
import com.owncloud.android.databinding.ActivityFavoritesBinding
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.presentation.files.details.FileDetailsFragment
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.fragment.FileFragment

class FavoritesActivity : FileActivity(), FileFragment.ContainerActivity {

    private lateinit var binding: ActivityFavoritesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStandardToolbar(
            title = getString(R.string.drawer_menu_favorites),
            homeButtonEnabled = true,
            displayShowTitleEnabled = true,
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_favorites, FavoritesFragment.newInstance(), TAG_FAVORITES_LIST)
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun showDetails(file: OCFile) {
        val detailsFragment = FileDetailsFragment.newInstance(
            fileToDetail = file,
            account = account,
            syncFileAtOpen = false,
            isMultiPersonal = false,
        )
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container_favorites, detailsFragment, TAG_FILE_DETAIL)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = file.fileName
    }

    fun showTextPreview(file: OCFile) {
        val previewFragment = com.owncloud.android.ui.preview.PreviewTextFragment.newInstance(
            file,
            account,
        )
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container_favorites, previewFragment, TAG_PREVIEW_TEXT)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = file.fileName
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
            supportActionBar?.title = getString(R.string.drawer_menu_favorites)
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG_FAVORITES_LIST = "TAG_FAVORITES_LIST"
        private const val TAG_FILE_DETAIL = "TAG_FILE_DETAIL"
        private const val TAG_PREVIEW_TEXT = "TAG_PREVIEW_TEXT"
    }
}
