package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import java.util.UUID

@Composable
fun ArchiveActivityCard(
    activity: ArchiveActivityUiModel,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    onCancel: (UUID) -> Unit = {},
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        color = colorResource(R.color.homecloud_dialog_background),
        shadowElevation = CARD_ELEVATION,
    ) {
        if (activity.operationCount >= 2) {
            ExpandableArchiveActivityContent(
                activity = activity,
                initiallyExpanded = initiallyExpanded,
                onCancel = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            val operation = activity.operations.first()
            ArchiveActivityOperationRow(
                operation = operation,
                onCancel = { onCancel(operation.workId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = dimensionResource(R.dimen.standard_padding),
                        vertical = dimensionResource(R.dimen.standard_half_margin),
                    ),
            )
        }
    }
}

@Composable
private fun ExpandableArchiveActivityContent(
    activity: ArchiveActivityUiModel,
    initiallyExpanded: Boolean,
    onCancel: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(modifier = modifier) {
        ArchiveActivityHeader(
            operationCount = activity.operationCount,
            expanded = expanded,
            onToggle = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(R.dimen.standard_padding),
                    end = dimensionResource(R.dimen.standard_half_margin),
                    top = dimensionResource(R.dimen.standard_half_margin),
                    bottom = dimensionResource(R.dimen.standard_quarter_margin),
                ),
        )
        if (!expanded) {
            ArchiveActivityProgressBar(
                progress = activity.overallProgress,
                isIndeterminate = activity.isOverallProgressIndeterminate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.standard_padding)),
            )
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.standard_half_margin)))
        AnimatedVisibility(visible = expanded) {
            ArchiveActivityOperations(
                operations = activity.operations,
                onCancel = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ArchiveActivityHeader(
    operationCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = colorResource(R.color.homecloud_color_accent)
    Row(
        modifier = modifier.clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Info,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(HEADER_ICON_SIZE),
        )
        Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_half_margin)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.homecloud_filelist_activity_in_progress_title),
                color = colorResource(R.color.homecloud_primary),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.homecloud_filelist_activity_in_progress_subtitle,
                    operationCount,
                    operationCount,
                ),
                color = colorResource(R.color.homecloud_gray_label),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(CHEVRON_SIZE),
        )
    }
}

@Composable
private fun ArchiveActivityOperations(
    operations: List<ArchiveActivityOperationUiModel>,
    onCancel: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            horizontal = dimensionResource(R.dimen.standard_padding)
        )
    ) {
        operations.forEachIndexed { index, operation ->
            if (index > 0) {
                HorizontalDivider(color = colorResource(R.color.homecloud_color_outline))
            }
            ArchiveActivityOperationRow(
                operation = operation,
                onCancel = { onCancel(operation.workId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = dimensionResource(R.dimen.standard_half_margin),
                    ),
            )
        }
    }
}

@Composable
private fun ArchiveActivityOperationRow(
    operation: ArchiveActivityOperationUiModel,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.file_zip),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(OPERATION_ICON_SIZE),
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_half_margin)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.displayName,
                    color = colorResource(R.color.homecloud_primary),
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (operation.isCompress) {
                            R.string.homecloud_filelist_compressing_status
                        } else {
                            R.string.homecloud_filelist_extracting_status
                        },
                    ),
                    color = colorResource(R.color.homecloud_gray_label),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.standard_quarter_margin)))
        ArchiveActivityProgressBar(
            progress = operation.progress,
            isIndeterminate = operation.isProgressIndeterminate,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colorResource(R.color.homecloud_button_borderless_text),
            ),
        ) {
            Text(text = stringResource(R.string.homecloud_filelist_archive_cancel))
        }
    }
}

@Composable
private fun ArchiveActivityProgressBar(
    progress: Int?,
    isIndeterminate: Boolean,
    modifier: Modifier = Modifier,
) {
    val indicatorColor = colorResource(R.color.homecloud_progressbar_indicator)
    val trackColor = colorResource(R.color.homecloud_progressbar_track)
    val progressModifier = modifier.height(PROGRESS_TRACK_THICKNESS)
    if (isIndeterminate || progress == null) {
        LinearProgressIndicator(
            modifier = progressModifier,
            color = indicatorColor,
            trackColor = trackColor,
            strokeCap = StrokeCap.Square,
            gapSize = 0.dp,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = progressModifier,
            color = indicatorColor,
            trackColor = trackColor,
            gapSize = 0.dp,
            strokeCap = StrokeCap.Square,
            drawStopIndicator = {},
        )
    }
}

private val CARD_CORNER_RADIUS = 8.dp
private val CARD_ELEVATION = 1.dp
private val HEADER_ICON_SIZE = 24.dp
private val CHEVRON_SIZE = 32.dp
private val OPERATION_ICON_SIZE = 40.dp
private val PROGRESS_TRACK_THICKNESS = 4.dp

@HomeCloudPreview
@Composable
private fun ArchiveActivityCardSinglePreview() {
    HomeCloudTheme {
        Surface(color = colorResource(R.color.homecloud_surface)) {
            ArchiveActivityCard(
                activity = ArchiveActivityUiModelFixtures.singleCompressIndeterminate,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun ArchiveActivityCardMultiCollapsedPreview() {
    HomeCloudTheme {
        Surface(color = colorResource(R.color.homecloud_surface)) {
            ArchiveActivityCard(
                activity = ArchiveActivityUiModelFixtures.threeOperationsMixed,
                initiallyExpanded = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun ArchiveActivityCardMultiExpandedPreview() {
    HomeCloudTheme {
        Surface(color = colorResource(R.color.homecloud_surface)) {
            ArchiveActivityCard(
                activity = ArchiveActivityUiModelFixtures.threeOperationsMixed,
                initiallyExpanded = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
    }
}
