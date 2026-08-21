package com.owncloud.android.presentation.files.filelist.compose

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.utils.DisplayUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    item: FileListItemUiModel,
    modifier: Modifier = Modifier,
    thumbnail: Bitmap? = null,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onThreeDotClick: () -> Unit = {},
    onVirtualOpenUploads: () -> Unit = {},
    onVirtualCancelUpload: () -> Unit = {},
) {
    val contentAlpha = if (item.isVirtual) 0.5f else 1f
    val isUploadVirtual = item.virtualKind == FileListVirtualKind.Upload
    var virtualMenuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.combinedClickable(
            onClick = {
                if (isUploadVirtual) {
                    virtualMenuExpanded = true
                } else {
                    onClick()
                }
            },
            onLongClick = onLongClick.takeUnless { item.isVirtual },
        ),
        contentAlignment = Alignment.CenterStart,
    ) {
        FileListRowContent(
            item = item,
            contentAlpha = contentAlpha,
            thumbnail = thumbnail,
            onThreeDotClick = onThreeDotClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimensionResource(R.dimen.standard_quarter_margin)),
        )
        if (item.isVirtual) {
            FileListUploadProgress(
                item = item,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
        if (isUploadVirtual) {
            FileListVirtualUploadMenu(
                expanded = virtualMenuExpanded,
                onDismissRequest = { virtualMenuExpanded = false },
                showCancelUpload = item.uploadProgress != 100,
                onOpenUploads = {
                    virtualMenuExpanded = false
                    onVirtualOpenUploads()
                },
                onCancelUpload = {
                    virtualMenuExpanded = false
                    onVirtualCancelUpload()
                },
            )
        }
    }
}

@Composable
private fun FileListRowContent(
    item: FileListItemUiModel,
    contentAlpha: Float,
    thumbnail: Bitmap?,
    onThreeDotClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileListRowThumbnail(
            item = item,
            contentAlpha = contentAlpha,
            thumbnail = thumbnail,
            modifier = Modifier.widthIn(min = dimensionResource(R.dimen.item_file_list_icon_min_width)),
        )
        FileListRowDetails(
            item = item,
            contentAlpha = contentAlpha,
            modifier = Modifier
                .weight(1f)
                .padding(end = dimensionResource(R.dimen.standard_padding)),
        )
        FileListRowTrailingAction(
            item = item,
            onThreeDotClick = onThreeDotClick,
        )
    }
}

@Composable
private fun FileListRowDetails(
    item: FileListItemUiModel,
    contentAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val secondaryTextColor = colorResource(R.color.list_item_lastmod_and_filesize_text)

    Column(modifier = modifier) {
        FileListRowFileName(
            name = item.name,
            modifier = Modifier.alpha(contentAlpha),
        )
        FileListRowSubtitle(
            item = item,
            secondaryTextColor = secondaryTextColor,
        )
        item.spacePath?.let { spacePath ->
            FileListSpacePathLine(
                spacePath = spacePath,
                secondaryTextColor = secondaryTextColor,
            )
        }
    }
}

@Composable
private fun FileListRowFileName(
    name: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        style = fileListTextStyle(
            fontSize = dimensionResource(R.dimen.two_line_primary_text_size).value.sp,
            color = MaterialTheme.colorScheme.primary,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun FileListRowSubtitle(
    item: FileListItemUiModel,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sizeText = remember(item.length, item.hideSizeAndSeparator, context) {
        if (item.hideSizeAndSeparator) {
            ""
        } else {
            DisplayUtils.bytesToHumanReadable(item.length, context, true)
        }
    }
    val lastModifiedText = remember(item.modificationTimestamp, context) {
        DisplayUtils
            .getRelativeTimestamp(context, item.modificationTimestamp)
            .toString()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.showShareIcons) {
            FileListRowShareIcons(
                sharedWithUsers = item.sharedWithUsers,
                sharedByLink = item.sharedByLink,
                tint = secondaryTextColor,
                modifier = Modifier.padding(end = dimensionResource(R.dimen.standard_half_margin)),
            )
        }
        FileListRowSizeAndDate(
            sizeText = sizeText,
            lastModifiedText = lastModifiedText,
            hideSizeAndSeparator = item.hideSizeAndSeparator,
            secondaryTextColor = secondaryTextColor,
        )
    }
}

@Composable
private fun FileListRowShareIcons(
    sharedWithUsers: Boolean,
    sharedByLink: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sharedWithUsers) {
            Icon(
                painter = painterResource(R.drawable.ic_share_generic),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
        if (sharedByLink) {
            Icon(
                painter = painterResource(R.drawable.ic_shared_by_link),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun FileListRowSizeAndDate(
    sizeText: String,
    lastModifiedText: String,
    hideSizeAndSeparator: Boolean,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val secondaryStyle = fileListTextStyle(
        fontSize = dimensionResource(R.dimen.two_line_secondary_text_size).value.sp,
        color = secondaryTextColor,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hideSizeAndSeparator) {
            Text(
                text = lastModifiedText,
                style = secondaryStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = sizeText,
                style = secondaryStyle,
                maxLines = 1,
            )
            Text(
                text = ",",
                style = secondaryStyle,
            )
            Text(
                text = lastModifiedText,
                style = secondaryStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = dimensionResource(R.dimen.standard_quarter_margin)),
            )
        }
    }
}

@Composable
private fun FileListRowTrailingAction(
    item: FileListItemUiModel,
    onThreeDotClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        item.showCheckbox -> FileListRowSelectionCheckbox(
            isSelected = item.isSelected,
            modifier = modifier.padding(end = dimensionResource(R.dimen.standard_margin)),
        )
        item.showThreeDotMenu -> FileListRowThreeDotMenu(
            fileName = item.name,
            onClick = onThreeDotClick,
            modifier = modifier.size(dimensionResource(R.dimen.icon_button_size)),
        )
        else -> Spacer(
            modifier = modifier.width(dimensionResource(R.dimen.standard_quarter_margin)),
        )
    }
}

@Composable
private fun FileListRowSelectionCheckbox(
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

@Composable
private fun FileListRowThreeDotMenu(
    fileName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_three_dot_menu),
            contentDescription = stringResource(
                R.string.content_description_file_operations,
                fileName,
            ),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Matches XML TextView sizing (no M3 letter-spacing / inflated line height). */
private fun fileListTextStyle(fontSize: TextUnit, color: Color): TextStyle =
    TextStyle(
        color = color,
        fontSize = fontSize,
        lineHeight = fontSize,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

@Composable
private fun FileListRowThumbnail(
    item: FileListItemUiModel,
    contentAlpha: Float,
    thumbnail: Bitmap?,
    modifier: Modifier = Modifier,
) {
    val iconSize = dimensionResource(R.dimen.file_icon_size)
    val pngBackground = colorResource(R.color.background_color)
    val imageModifier = Modifier
        .size(iconSize)
        .alpha(contentAlpha)
        .then(
            if (item.mimeType == "image/png") {
                Modifier.background(pngBackground)
            } else {
                Modifier
            }
        )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box {
            if (thumbnail != null) {
                val imageBitmap = remember(thumbnail) { thumbnail.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            } else {
                Image(
                    painter = painterResource(item.mimeIconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = imageModifier,
                )
            }
            FileListRowLocalPin(
                localPin = item.localPin,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dimensionResource(R.dimen.file_indicator_pin_size))
                    .offset(x = 8.dp, y = 10.dp),
            )
        }
    }
}

@Composable
private fun FileListRowLocalPin(
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
private fun FileListSpacePathLine(
    spacePath: FileListSpacePathUiModel,
    secondaryTextColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            spacePath.showPersonalLabel -> FileListSpaceLabel(
                iconRes = R.drawable.ic_folder,
                label = stringResource(R.string.bottom_nav_personal),
                tint = secondaryTextColor,
                modifier = Modifier.padding(end = dimensionResource(R.dimen.standard_half_margin)),
            )
            spacePath.spaceName != null -> FileListSpaceLabel(
                iconRes = R.drawable.ic_spaces,
                label = spacePath.spaceName,
                tint = secondaryTextColor,
                modifier = Modifier.padding(end = dimensionResource(R.dimen.standard_half_margin)),
            )
        }
        Text(
            text = spacePath.parentPath,
            color = secondaryTextColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileListSpaceLabel(
    iconRes: Int,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(tint),
            modifier = Modifier
                .size(15.dp)
                .padding(end = 2.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 100.dp),
        )
    }
}

@HomeCloudPreview
@Composable
private fun FileListRowPreview(
    @PreviewParameter(FileListItemUiModelPreviewParameterProvider::class)
    item: FileListItemUiModel,
) {
    HomeCloudTheme {
        Surface {
            FileListRow(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListRowThumbnailMissingPreview() {
    HomeCloudTheme {
        Surface {
            FileListRow(
                item = FileListItemUiModelFixtures.file,
                thumbnail = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListRowThumbnailLoadedPreview() {
    val thumbnail = remember {
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(android.graphics.Color.parseColor("#1976D2"))
        }
    }
    HomeCloudTheme {
        Surface {
            FileListRow(
                item = FileListItemUiModelFixtures.fileWithThumbnail,
                thumbnail = thumbnail,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.item_file_list_min_height)),
            )
        }
    }
}
