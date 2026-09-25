package com.owncloud.android.presentation.imageedit

import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.remember
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

class ImageCropRotateActivity : AppCompatActivity() {

    private val viewModel: ImageCropRotateViewModel by viewModel {
        parametersOf(intent.getParcelableExtra(EXTRA_FILE) as OCFile?)
    }

    private val cropHandle = CropImageViewHandle()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val file = intent.getParcelableExtra(EXTRA_FILE) as OCFile?
        if (file == null) {
            Timber.w("Cannot edit image, missing file extra")
            finish()
            return
        }

        setContentView(R.layout.activity_image_crop_rotate)
        val toolbar = findViewById<Toolbar>(R.id.standard_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this) {
            onEditCancelled()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState ->
                    invalidateOptionsMenu()
                    val saved = uiState as? ImageCropRotateUiState.Saved ?: return@collect
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_OUTPUT_PATH, saved.outputFile.absolutePath),
                    )
                    finish()
                }
            }
        }

        findViewById<ComposeView>(R.id.image_crop_rotate_compose_view).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HomeCloudTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val imageUri = remember(uiState.localFilePath()) {
                        viewModel.getInputUri(this@ImageCropRotateActivity)
                    }

                    Surface(modifier = Modifier.fillMaxSize()) {
                        ImageCropRotateScreen(
                            imageUri = imageUri,
                            uiState = uiState,
                            errorMessage = uiState.errorMessageRes?.let { stringResource(it) },
                            cropHandle = cropHandle,
                            onErrorDismissed = viewModel::consumeError,
                            onImageLoaded = viewModel::onImageLoaded,
                            onCropComplete = viewModel::onSaveCompleted,
                            onOverwriteChosen = viewModel::onOverwriteChosen,
                            onSaveAsCopyChosen = viewModel::onSaveAsCopyChosen,
                            onConflictDismissed = viewModel::onConflictDismissed,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.image_crop_rotate_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_done)?.isEnabled =
            viewModel.uiState.value is ImageCropRotateUiState.Ready
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

    private fun onEditCancelled() {
        viewModel.cancelDownloadIfNeeded()
        finish()
    }

    private fun onSaveRequested() {
        if (cropHandle.view == null) return
        viewModel.onSaveStarted()
        val outputFile = viewModel.createOutputFile()
        val outputUri = viewModel.getOutputUri(this, outputFile)
        cropHandle.cropToOutput(viewModel.getOutputCompressFormat(), outputUri, outputFile)
    }

    companion object {
        const val EXTRA_FILE = "IMAGE_CROP_ROTATE_FILE"
        const val EXTRA_OUTPUT_PATH = "IMAGE_CROP_ROTATE_OUTPUT_PATH"

        fun createIntent(context: Context, file: OCFile): Intent =
            Intent(context, ImageCropRotateActivity::class.java)
                .putExtra(EXTRA_FILE, file)
    }
}
