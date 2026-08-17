package com.owncloud.android.presentation.previews.compose

import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.previews.text.TextPreviewMarkwonFactory
import io.noties.markwon.Markwon
import io.noties.markwon.utils.NoCopySpannableFactory

/**
 * Remembers a [Markwon] built via [TextPreviewMarkwonFactory] (for Compose previews).
 * Runtime UI receives Markwon from [PreviewTextViewModel] instead.
 */
@Composable
fun rememberTextPreviewMarkwon(): Markwon {
    val context = LocalContext.current
    return remember {
        TextPreviewMarkwonFactory.create(context)
    }
}

/**
 * One markdown chunk row for LazyColumn, backed by a reusable [TextView].
 * Use the [AndroidView] overload with [onReset] so views can be recycled safely.
 */
@Composable
fun MarkdownBlockRow(
    spanned: Spanned,
    markwon: Markwon,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setSpannableFactory(NoCopySpannableFactory.getInstance())
                setTextIsSelectable(false)
            }
        },
        modifier = modifier,
        update = { textView ->
            textView.setTextColor(textColor)
            markwon.setParsedMarkdown(textView, spanned)
        },
        onReset = { textView ->
            textView.text = null
        },
    )
}

@HomeCloudPreview
@Composable
private fun MarkdownBlockRowPreview() {
    val markwon = rememberTextPreviewMarkwon()
    val spanned = remember(markwon) {
        markwon.toMarkdown("## Preview\n\nSample **markdown** chunk with a list:\n\n- one\n- two\n")
    }
    HomeCloudTheme {
        Surface {
            MarkdownBlockRow(
                spanned = spanned,
                markwon = markwon,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}
