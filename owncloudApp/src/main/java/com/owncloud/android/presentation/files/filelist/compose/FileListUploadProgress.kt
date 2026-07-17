package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.owncloud.android.R

/**
 * Upload/archive progress bar shared by list and grid items.
 * Colors and thickness match [R.style.Widget_homeCloud_HorizontalProgressBar].
 */
@Composable
fun FileListUploadProgress(
    item: FileListItemUiModel,
    modifier: Modifier = Modifier,
) {
    val indicatorColor = colorResource(R.color.homecloud_progressbar_indicator)
    val trackColor = colorResource(R.color.homecloud_progressbar_track)
    val progressModifier = modifier.height(UPLOAD_PROGRESS_TRACK_THICKNESS)
    if (item.isProgressIndeterminate) {
        LinearProgressIndicator(
            modifier = progressModifier,
            color = indicatorColor,
            trackColor = trackColor,
        )
    } else {
        val progress = ((item.uploadProgress ?: 0).coerceIn(0, 100)) / 100f
        LinearProgressIndicator(
            progress = progress,
            modifier = progressModifier,
            color = indicatorColor,
            trackColor = trackColor,
        )
    }
}

private val UPLOAD_PROGRESS_TRACK_THICKNESS = 8.dp
