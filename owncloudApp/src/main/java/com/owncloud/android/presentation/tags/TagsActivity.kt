package com.owncloud.android.presentation.tags

import android.os.Bundle
import android.view.Menu
import com.owncloud.android.R
import com.owncloud.android.databinding.ActivityTagsBinding
import com.owncloud.android.ui.activity.ToolbarActivity

class TagsActivity : ToolbarActivity() {

    private lateinit var binding: ActivityTagsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTagsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupStandardToolbar(
            title = getString(R.string.drawer_menu_tags),
            homeButtonEnabled = true,
            displayShowTitleEnabled = true,
        )

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container_tags, TagsFragment.newInstance(), TAG_TAGS_LIST)
                .commit()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val TAG_TAGS_LIST = "TAG_TAGS_LIST"
    }
}
