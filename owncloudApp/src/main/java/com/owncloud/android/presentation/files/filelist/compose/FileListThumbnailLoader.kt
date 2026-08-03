package com.owncloud.android.presentation.files.filelist.compose

import android.accounts.Account
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.files.model.OCFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Loads a file thumbnail via [ThumbnailsCacheManager]:
 * 1. Disk-cache hit (IO thread)
 * 2. Optional generation when [OCFile.needsToUpdateThumbnail] (no ImageView)
 * Cancels in-flight generation on dispose / key change; ignores stale results.
 */
@Composable
fun rememberFileListThumbnail(
    file: OCFile?,
    account: Account?,
): Bitmap? {
    val fileId = file?.id
    val remoteId = file?.remoteId
    val needsThumbnail = file?.needsToUpdateThumbnail == true
    var bitmap by remember(fileId, remoteId) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(fileId, remoteId, needsThumbnail, account?.name) {
        if (file == null || remoteId.isNullOrEmpty()) {
            bitmap = null
            return@DisposableEffect onDispose { }
        }

        val expectedFileId = fileId
        val expectedRemoteId = remoteId
        val active = AtomicBoolean(true)
        val scope = CoroutineScope(Dispatchers.Main.immediate)
        val taskRef = AtomicReference<ThumbnailsCacheManager.ThumbnailGenerationTask?>(null)

        fun applyIfCurrent(result: Bitmap?, fromFile: OCFile? = null) {
            if (!active.get()) return
            if (fromFile != null) {
                if (fromFile.id != expectedFileId || fromFile.remoteId != expectedRemoteId) return
            }
            bitmap = result
        }

        scope.launch {
            val cached = withContext(Dispatchers.IO) {
                ThumbnailsCacheManager.getBitmapFromDiskCache(expectedRemoteId)
            }
            applyIfCurrent(cached)

            if (!needsThumbnail || account == null || !active.get()) {
                return@launch
            }

            val task = ThumbnailsCacheManager.startThumbnailGeneration(
                file,
                account,
            ) { generatedFile, generatedBitmap ->
                val ocFile = generatedFile as? OCFile
                applyIfCurrent(generatedBitmap, ocFile)
            }
            taskRef.set(task)
        }

        onDispose {
            active.set(false)
            taskRef.getAndSet(null)?.cancel(true)
            scope.cancel()
        }
    }

    return bitmap
}
