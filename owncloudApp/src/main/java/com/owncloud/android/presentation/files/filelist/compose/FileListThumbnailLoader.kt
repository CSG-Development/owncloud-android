package com.owncloud.android.presentation.files.filelist.compose

import android.accounts.Account
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.files.model.OCFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads a file thumbnail via [ThumbnailsCacheManager]:
 * 1. Disk-cache hit (IO thread)
 * 2. Optional [ThumbnailsCacheManager.ThumbnailGenerationTask] when [OCFile.needsToUpdateThumbnail]
 * Cancels in-flight generation on dispose / key change.
 */
@Composable
fun rememberFileListThumbnail(
    file: OCFile?,
    account: Account?,
): Bitmap? {
    val context = LocalContext.current
    val fileId = file?.id
    val remoteId = file?.remoteId
    val needsThumbnail = file?.needsToUpdateThumbnail == true
    var bitmap by remember(fileId, remoteId) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(fileId, remoteId, needsThumbnail, account?.name) {
        if (file == null || remoteId.isNullOrEmpty()) {
            bitmap = null
            return@DisposableEffect onDispose { }
        }

        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val taskRef = AtomicReference<ThumbnailsCacheManager.ThumbnailGenerationTask?>(null)

        scope.launch {
            val cached = withContext(Dispatchers.IO) {
                ThumbnailsCacheManager.getBitmapFromDiskCache(remoteId)
            }
            bitmap = cached

            if (!needsThumbnail || account == null) {
                return@launch
            }

            val imageView = object : ImageView(context) {
                override fun setImageBitmap(bm: Bitmap?) {
                    super.setImageBitmap(bm)
                    if (bm != null) {
                        bitmap = bm
                    }
                }
            }.apply {
                tag = fileId
            }

            if (!ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, imageView)) {
                return@launch
            }

            val task = ThumbnailsCacheManager.ThumbnailGenerationTask(imageView, account)
            taskRef.set(task)
            val asyncDrawable = ThumbnailsCacheManager.AsyncThumbnailDrawable(
                context.resources,
                cached,
                task,
            )
            if (asyncDrawable.minimumHeight > 0 && asyncDrawable.minimumWidth > 0) {
                imageView.setImageDrawable(asyncDrawable)
            }
            @Suppress("DEPRECATION")
            task.execute(file)
        }

        onDispose {
            taskRef.get()?.cancel(true)
            scope.cancel()
        }
    }

    return bitmap
}
