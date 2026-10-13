package com.owncloud.android.presentation.editphoto

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.addCallback
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class EditPhotoActivity : AppCompatActivity() {

    private val viewModel: EditPhotoViewModel by viewModel {
        parametersOf(intent.getParcelableExtra(EXTRA_FILE) as OCFile?)
    }

    private val editorHandle = PhotoEditorHandle()
    private var saveInFlight = false

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

        onBackPressedDispatcher.addCallback(this) {
            onEditCancelled()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    invalidateOptionsMenu()
                    if (uiState is EditPhotoUiState.Saved) {
                        finish()
                    }
                }
            }
        }

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
                            onOverwriteChosen = ::onOverwriteChosen,
                            onSaveAsCopyChosen = ::onSaveAsCopyChosen,
                            onConflictDismissed = viewModel::onConflictDismissed,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        window.decorView.requestApplyInsets()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_photo_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_done)?.isEnabled = isDoneEnabled()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onEditCancelled()
                true
            }
            R.id.action_done -> {
                onSaveRequested()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun isDoneEnabled(): Boolean {
        val ready = viewModel.uiState.value as? EditPhotoUiState.Ready ?: return false
        return ready.isImageLoaded
    }

    private fun onEditCancelled() {
        viewModel.cancelDownloadIfNeeded()
        finish()
    }

    private fun onSaveRequested() {
        if (saveInFlight || !isDoneEnabled() || editorHandle.photoEditor == null) return
        saveInFlight = true
        lifecycleScope.launch {
            try {
                if (viewModel.targetNameExists()) {
                    viewModel.showNameConflict()
                } else {
                    flattenAndEnqueue(overwrite = false)
                }
            } finally {
                saveInFlight = false
            }
        }
    }

    private fun onOverwriteChosen() {
        flattenAfterConflict(overwrite = true)
    }

    private fun onSaveAsCopyChosen() {
        flattenAfterConflict(overwrite = false)
    }

    private fun flattenAfterConflict(overwrite: Boolean) {
        if (saveInFlight || viewModel.uiState.value !is EditPhotoUiState.NameConflict) return
        if (editorHandle.photoEditor == null) return
        saveInFlight = true
        lifecycleScope.launch {
            try {
                flattenAndEnqueue(overwrite)
            } finally {
                saveInFlight = false
            }
        }
    }

    private suspend fun flattenAndEnqueue(overwrite: Boolean) {
        viewModel.onSaveStarted()
        val outputFile = viewModel.createOutputFile()
        val error = editorHandle.saveAsFile(
            file = outputFile,
            compressFormat = viewModel.getOutputCompressFormat(),
        )
        viewModel.onFlattenCompleted(outputFile = outputFile, error = error, overwrite = overwrite)
    }

    companion object {
        const val EXTRA_FILE = "EDIT_PHOTO_FILE"

        fun createIntent(context: Context, file: OCFile): Intent =
            Intent(context, EditPhotoActivity::class.java)
                .putExtra(EXTRA_FILE, file)
    }
}
