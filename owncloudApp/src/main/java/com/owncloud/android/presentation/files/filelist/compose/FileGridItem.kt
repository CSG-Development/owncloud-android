package com.owncloud.android.presentation.files.filelist.compose

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

/**
 * Grid cell for file list. Visual parity with [R.layout.grid_item].
 *
 * @param expandedThumbnail When true (image grid cell with a loaded thumb), media fills the cell
 *   like XML MATCH_PARENT thumbnail sizing; otherwise uses the fixed 72dp icon size.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridItem(
    item: FileListItemUiModel,
    modifier: Modifier = Modifier,
    thumbnail: Bitmap? = null,
    expandedThumbnail: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
) {
    val contentAlpha = if (item.isVirtual) 0.5f else 1f

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick.takeUnless { item.isVirtual },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        FileGridItemMedia(
            item = item,
            contentAlpha = contentAlpha,
            thumbnail = thumbnail,
            expandedThumbnail = expandedThumbnail,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        if (!expandedThumbnail) {
            FileGridItemFileName(
                name = item.name,
                contentAlpha = contentAlpha,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun FileGridItemMedia(
    item: FileListItemUiModel,
    contentAlpha: Float,
    thumbnail: Bitmap?,
    expandedThumbnail: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        FileGridItemThumbnail(
            item = item,
            contentAlpha = contentAlpha,
            thumbnail = thumbnail,
            expandedThumbnail = expandedThumbnail,
            modifier = Modifier.align(Alignment.Center),
        )
        if (item.showShareIcons) {
            FileGridItemShareIcons(
                sharedWithUsers = item.sharedWithUsers,
                sharedByLink = item.sharedByLink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimensionResource(R.dimen.standard_quarter_margin))
                    .padding(top = dimensionResource(R.dimen.standard_quarter_margin)),
            )
        }
        FileGridItemLocalPin(
            localPin = item.localPin,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 2.dp)
                .size(dimensionResource(R.dimen.file_indicator_pin_size_grid)),
        )
        if (item.showCheckbox) {
            FileGridItemCheckbox(
                isSelected = item.isSelected,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 4.dp),
            )
        }
        if (item.isVirtual) {
            FileListUploadProgress(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = dimensionResource(R.dimen.item_file_grid_margin)),
            )
        }
    }
}

@Composable
private fun FileGridItemThumbnail(
    item: FileListItemUiModel,
    contentAlpha: Float,
    thumbnail: Bitmap?,
    expandedThumbnail: Boolean,
    modifier: Modifier = Modifier,
) {
    val pngBackground = colorResource(R.color.background_color)
    val pngModifier = if (item.mimeType == "image/png") {
        Modifier.background(pngBackground)
    } else {
        Modifier
    }
    val contentScale = if (expandedThumbnail) ContentScale.Crop else ContentScale.Fit
    val imageModifier = if (expandedThumbnail) {
        Modifier
            .fillMaxSize()
            .padding(dimensionResource(R.dimen.item_file_image_grid_margin))
            .alpha(contentAlpha)
            .then(pngModifier)
    } else {
        Modifier
            .padding(horizontal = dimensionResource(R.dimen.item_file_grid_margin))
            .size(
                width = dimensionResource(R.dimen.item_file_grid_width),
                height = dimensionResource(R.dimen.item_file_grid_height),
            )
            .alpha(contentAlpha)
            .then(pngModifier)
    }

    Box(
        modifier = if (expandedThumbnail) {
            modifier.fillMaxSize()
        } else {
            modifier
        },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = null,
                contentScale = contentScale,
                modifier = imageModifier,
            )
        } else {
            Image(
                painter = painterResource(item.mimeIconRes),
                contentDescription = null,
                contentScale = contentScale,
                modifier = imageModifier,
            )
        }
    }
}

@Composable
private fun FileGridItemShareIcons(
    sharedWithUsers: Boolean,
    sharedByLink: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sharedWithUsers) {
            Image(
                painter = painterResource(R.drawable.ic_share_generic_black),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
        }
        if (sharedByLink) {
            Image(
                painter = painterResource(R.drawable.ic_shared_by_link),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun FileGridItemLocalPin(
    localPin: FileListLocalPin,
    modifier: Modifier = Modifier,
) {
    val pinRes = localPin.toDrawableRes() ?: return
    Image(
        painter = painterResource(pinRes),
        contentDescription = null,
        modifier = modifier,
    )
}

@Composable
private fun FileGridItemCheckbox(
    isSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(
            if (isSelected) R.drawable.ic_checkbox_marked else R.drawable.ic_checkbox_blank_outline
        ),
        contentDescription = null,
        modifier = modifier,
    )
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
private fun FileGridItemFileName(
    name: String,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val fontSize = 16.sp
    val textStyle = TextStyle(
        color = MaterialTheme.colorScheme.primary,
        fontSize = fontSize,
        lineHeight = fontSize,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val displayText = rememberMiddleEllipsizedText(
            text = name,
            style = textStyle,
            maxWidthPx = maxWidthPx,
        )
        Text(
            text = displayText,
            style = textStyle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(contentAlpha),
        )
    }
}

/** Approximates Android TextView ellipsize="middle" for a single line. */
@Composable
private fun rememberMiddleEllipsizedText(
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
): String {
    val textMeasurer = rememberTextMeasurer()
    return remember(text, style, maxWidthPx) {
        if (maxWidthPx <= 0) return@remember text
        val unconstrained = Constraints()
        val fullWidth = textMeasurer.measure(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            constraints = unconstrained,
        ).size.width
        if (fullWidth <= maxWidthPx) return@remember text

        val ellipsis = "\u2026"
        var low = 0
        var high = text.length
        var best = ellipsis
        while (low <= high) {
            val mid = (low + high) / 2
            val head = mid / 2
            val tail = mid - head
            val candidate = buildString(head + 1 + tail) {
                append(text, 0, head)
                append(ellipsis)
                if (tail > 0) append(text, text.length - tail, text.length)
            }
            val width = textMeasurer.measure(
                text = candidate,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                constraints = unconstrained,
            ).size.width
            if (width <= maxWidthPx) {
                best = candidate
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        best
    }
}

@HomeCloudPreview
@Composable
private fun FileGridItemPreview(
    @PreviewParameter(FileListItemUiModelPreviewParameterProvider::class)
    item: FileListItemUiModel,
) {
    HomeCloudTheme {
        Surface {
            FileGridItem(
                item = item,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileGridItemExpandedThumbnailPreview() {
    val thumbnail = remember {
        createBitmap(128, 128).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.rgb(80, 140, 200))
        }
    }
    HomeCloudTheme {
        Surface {
            FileGridItem(
                item = FileListItemUiModelFixtures.fileWithThumbnail,
                thumbnail = thumbnail,
                expandedThumbnail = true,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileGridItemSelectedPreview() {
    HomeCloudTheme {
        Surface {
            FileGridItem(
                item = FileListItemUiModelFixtures.selected,
                modifier = Modifier.width(160.dp),
            )
        }
    }
}
