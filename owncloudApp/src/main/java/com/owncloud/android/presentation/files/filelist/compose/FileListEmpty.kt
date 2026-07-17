package com.owncloud.android.presentation.files.filelist.compose

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.extensions.toDrawableRes
import com.owncloud.android.extensions.toSubtitleStringRes
import com.owncloud.android.extensions.toTitleStringRes
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

data class FileListEmptyUiModel(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
)

fun FileListOption.toFileListEmptyUiModel(isSharesSpace: Boolean = false): FileListEmptyUiModel =
    if (isSharedByLink() && isSharesSpace) {
        FileListEmptyUiModel(
            iconRes = R.drawable.ic_ocis_shares,
            titleRes = R.string.shares_list_empty_title,
            subtitleRes = R.string.shares_list_empty_subtitle,
        )
    } else {
        FileListEmptyUiModel(
            iconRes = toDrawableRes(),
            titleRes = toTitleStringRes(),
            subtitleRes = toSubtitleStringRes(),
        )
    }

/**
 * Empty file-list placeholder. Visual parity with [R.layout.item_empty_dataset].
 */
@Composable
fun FileListEmpty(
    content: FileListEmptyUiModel,
    modifier: Modifier = Modifier,
) {
    val labelColor = colorResource(R.color.homecloud_gray_label)
    val horizontalMargin = dimensionResource(R.dimen.standard_margin)
    val halfPadding = dimensionResource(R.dimen.standard_half_padding)

    Column(
        modifier = modifier
            .padding(vertical = halfPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(content.iconRes),
            contentDescription = null,
            modifier = Modifier.size(EMPTY_ICON_SIZE),
            tint = labelColor,
        )
        Text(
            text = stringResource(content.titleRes),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = labelColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin, vertical = halfPadding),
        )
        Text(
            text = stringResource(content.subtitleRes),
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin),
        )
    }
}

private val EMPTY_ICON_SIZE = 72.dp

@HomeCloudPreview
@Composable
private fun FileListEmptyAllFilesPreview() {
    HomeCloudTheme {
        Surface {
            FileListEmpty(
                content = FileListOption.ALL_FILES.toFileListEmptyUiModel(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun FileListEmptySharesSpacePreview() {
    HomeCloudTheme {
        Surface {
            FileListEmpty(
                content = FileListOption.SHARED_BY_LINK.toFileListEmptyUiModel(isSharesSpace = true),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
