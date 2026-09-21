package com.owncloud.android.presentation.editphoto

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class EditPhotoActivity : AppCompatActivity() {

    private val viewModel: EditPhotoViewModel by viewModel {
        parametersOf(intent.getParcelableExtra(EXTRA_FILE) as OCFile?)
    }

    private val editorHandle = PhotoEditorHandle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getParcelableExtra(EXTRA_FILE) as OCFile?
        if (file == null) {
            Timber.w("Cannot open Edit Photo, missing file extra")
            finish()
            return
        }

        setContentView(R.layout.activity_edit_photo)
        val toolbar = findViewById<Toolbar>(R.id.standard_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.homecloud_filelist_edit_photo)

        findViewById<ComposeView>(R.id.edit_photo_compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HomeCloudTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    Surface(modifier = Modifier.fillMaxSize()) {
                        EditPhotoScreen(
                            uiState = uiState,
                            errorMessage = uiState.errorMessageRes()?.let { stringResource(it) },
                            editorHandle = editorHandle,
                            onErrorDismissed = viewModel::consumeError,
                            onImageLoaded = viewModel::onImageLoaded,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_FILE = "EDIT_PHOTO_FILE"

        fun createIntent(context: Context, file: OCFile): Intent =
            Intent(context, EditPhotoActivity::class.java)
                .putExtra(EXTRA_FILE, file)
    }
}
