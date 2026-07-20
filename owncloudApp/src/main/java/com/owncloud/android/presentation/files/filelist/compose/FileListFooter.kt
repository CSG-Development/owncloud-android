package com.owncloud.android.presentation.files.filelist.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

/**
 * File-list footer showing the aggregated file/folder count text.
 */
@Composable
fun FileListFooter(
    text: String,
    modifier: Modifier = Modifier,
) {
    val fontSize = dimensionResource(R.dimen.two_line_secondary_text_size).value.sp
    val textStyle = TextStyle(
        color = colorResource(R.color.homecloud_gray_label),
        fontSize = fontSize,
        lineHeight = fontSize,
        letterSpacing = 0.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

    Column(
        modifier = modifier.height(FOOTER_HEIGHT),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = textStyle,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(R.dimen.standard_padding)),
        )
        Spacer(modifier = Modifier.height(FOOTER_TEXT_BOTTOM_MARGIN))
    }
}

private val FOOTER_HEIGHT = 112.dp
private val FOOTER_TEXT_BOTTOM_MARGIN = 56.dp

private class FileListFooterPreviewParameterProvider : PreviewParameterProvider<String> {
    override val values: Sequence<String> = sequenceOf(
        "1 file",
        "1 folder",
        "1 file, 1 folder",
        "3 files",
        "2 folders",
        "5 files, 3 folders",
    )
}

@HomeCloudPreview
@Composable
private fun FileListFooterPreview(
    @PreviewParameter(FileListFooterPreviewParameterProvider::class)
    text: String,
) {
    HomeCloudTheme {
        Surface {
            FileListFooter(
                text = text,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
