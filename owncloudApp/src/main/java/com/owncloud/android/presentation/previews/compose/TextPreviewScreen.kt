package com.owncloud.android.presentation.previews.compose

import android.text.Spanned
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.previews.text.TextPreviewChunkRef
import com.owncloud.android.presentation.previews.text.TextPreviewChunkRefFixtures
import com.owncloud.android.presentation.previews.text.TextPreviewContentUiState
import com.owncloud.android.presentation.previews.text.TextPreviewMarkdownTab
import com.owncloud.android.presentation.previews.text.chunksOrEmpty
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun TextPreviewScreen(
    contentState: TextPreviewContentUiState,
    chunkTexts: Map<Int, String>,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    chunkSpanneds: Map<Int, Spanned> = emptyMap(),
    isMarkdown: Boolean = false,
    markdownTab: TextPreviewMarkdownTab = TextPreviewMarkdownTab.Rendered,
    onMarkdownTabSelected: (TextPreviewMarkdownTab) -> Unit = {},
    plainListState: LazyListState = rememberLazyListState(),
    markdownListState: LazyListState = rememberLazyListState(),
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit = { _, _ -> },
) {
    when (contentState) {
        TextPreviewContentUiState.Idle,
        TextPreviewContentUiState.Loading,
        -> {
            TextPreviewMessage(
                text = stringResource(R.string.homecloud_textpreview_loading),
                modifier = modifier,
                showProgress = true,
            )
        }

        is TextPreviewContentUiState.Error -> {
            TextPreviewMessage(
                text = stringResource(R.string.homecloud_textpreview_error),
                modifier = modifier,
                showProgress = false,
            )
        }

        is TextPreviewContentUiState.Indexing,
        is TextPreviewContentUiState.Ready,
        -> {
            val chunks = contentState.chunksOrEmpty()
            if (chunks.isEmpty() && contentState is TextPreviewContentUiState.Indexing) {
                TextPreviewMessage(
                    text = stringResource(R.string.homecloud_textpreview_loading),
                    modifier = modifier,
                    showProgress = true,
                )
            } else {
                TextPreviewContent(
                    chunks = chunks,
                    chunkTexts = chunkTexts,
                    chunkSpanneds = chunkSpanneds,
                    isMarkdown = isMarkdown,
                    markdownTab = markdownTab,
                    onMarkdownTabSelected = onMarkdownTabSelected,
                    plainListState = plainListState,
                    markdownListState = markdownListState,
                    markwon = markwon,
                    onVisibleRangeChanged = onVisibleRangeChanged,
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun TextPreviewContent(
    chunks: List<TextPreviewChunkRef>,
    chunkTexts: Map<Int, String>,
    chunkSpanneds: Map<Int, Spanned>,
    isMarkdown: Boolean,
    markdownTab: TextPreviewMarkdownTab,
    onMarkdownTabSelected: (TextPreviewMarkdownTab) -> Unit,
    plainListState: LazyListState,
    markdownListState: LazyListState,
    markwon: Markwon,
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (isMarkdown) {
            TextPreviewMarkdownTabs(
                selectedTab = markdownTab,
                onTabSelected = onMarkdownTabSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        val showRendered = isMarkdown && markdownTab == TextPreviewMarkdownTab.Rendered
        if (showRendered) {
            TextPreviewMarkdownList(
                chunks = chunks,
                chunkSpanneds = chunkSpanneds,
                markwon = markwon,
                listState = markdownListState,
                onVisibleRangeChanged = onVisibleRangeChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        } else {
            TextPreviewPlainList(
                chunks = chunks,
                chunkTexts = chunkTexts,
                listState = plainListState,
                onVisibleRangeChanged = onVisibleRangeChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextPreviewMarkdownTabs(
    selectedTab: TextPreviewMarkdownTab,
    onTabSelected: (TextPreviewMarkdownTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = when (selectedTab) {
        TextPreviewMarkdownTab.Rendered -> 0
        TextPreviewMarkdownTab.Plain -> 1
    }
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { onTabSelected(TextPreviewMarkdownTab.Rendered) },
            text = { Text(text = stringResource(R.string.tab_label_markdown)) },
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = { onTabSelected(TextPreviewMarkdownTab.Plain) },
            text = { Text(text = stringResource(R.string.tab_label_ascii)) },
        )
    }
}

@Composable
private fun TextPreviewMessage(
    text: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            if (showProgress) {
                CircularProgressIndicator()
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TextPreviewPlainList(
    chunks: List<TextPreviewChunkRef>,
    chunkTexts: Map<Int, String>,
    listState: LazyListState,
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextPreviewChunkLazyColumn(
        chunks = chunks,
        listState = listState,
        onVisibleRangeChanged = onVisibleRangeChanged,
        contentType = "plain_chunk",
        modifier = modifier,
    ) { chunk ->
        val text = chunkTexts[chunk.id]
        if (text != null) {
            TextPreviewPlainChunkRow(
                text = text,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TextPreviewChunkPlaceholder(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextPreviewMarkdownList(
    chunks: List<TextPreviewChunkRef>,
    chunkSpanneds: Map<Int, Spanned>,
    markwon: Markwon,
    listState: LazyListState,
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextPreviewChunkLazyColumn(
        chunks = chunks,
        listState = listState,
        onVisibleRangeChanged = onVisibleRangeChanged,
        contentType = "markdown_chunk",
        verticalSpacing = 12.dp,
        modifier = modifier,
    ) { chunk ->
        val spanned = chunkSpanneds[chunk.id]
        if (spanned != null) {
            MarkdownBlockRow(
                spanned = spanned,
                markwon = markwon,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        } else {
            TextPreviewChunkPlaceholder(
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TextPreviewChunkLazyColumn(
    chunks: List<TextPreviewChunkRef>,
    listState: LazyListState,
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
    contentType: String,
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 0.dp,
    itemContent: @Composable (TextPreviewChunkRef) -> Unit,
) {
    val onVisibleRangeChangedState = rememberUpdatedState(onVisibleRangeChanged)

    LaunchedEffect(listState, chunks.size) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) {
                0 to -1
            } else {
                visible.first().index to visible.last().index
            }
        }
            .distinctUntilChanged()
            .collect { (first, last) ->
                if (last >= first) {
                    onVisibleRangeChangedState.value(first, last)
                }
            }
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
    ) {
        items(
            items = chunks,
            key = { it.id },
            contentType = { contentType },
        ) { chunk ->
            itemContent(chunk)
        }
    }
}

@Composable
private fun TextPreviewPlainChunkRow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.withoutTrailingChunkBoundaryNewline(),
        modifier = modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun String.withoutTrailingChunkBoundaryNewline(): String = when {
    endsWith("\r\n") -> dropLast(2)
    endsWith("\n") -> dropLast(1)
    else -> this
}

@Composable
private fun TextPreviewChunkPlaceholder(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(24.dp),
            strokeWidth = 2.dp,
        )
    }
}

private val previewPlainChunkTexts: Map<Int, String> = mapOf(
    0 to "First plain chunk\nwith a few lines.\n",
    1 to "Second chunk of sample text.\n",
    2 to "Third chunk continues the file.\n",
    3 to "Last plain chunk.\n",
)

private val previewMarkdownSources: Map<Int, String> = mapOf(
    0 to "# Heading\n\nIntro paragraph.\n",
    1 to "- item one\n- item two\n\n**Bold** and *italic*.\n",
    2 to "Closing notes.\n",
)

@HomeCloudPreview
@Composable
private fun TextPreviewScreenLoadingPreview() {
    HomeCloudTheme {
        Surface {
            TextPreviewScreen(
                contentState = TextPreviewContentUiState.Loading,
                chunkTexts = emptyMap(),
                markwon = rememberTextPreviewMarkwon(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun TextPreviewScreenErrorPreview() {
    HomeCloudTheme {
        Surface {
            TextPreviewScreen(
                contentState = TextPreviewContentUiState.Error(IllegalStateException("preview")),
                chunkTexts = emptyMap(),
                markwon = rememberTextPreviewMarkwon(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun TextPreviewScreenPlainReadyPreview() {
    HomeCloudTheme {
        Surface {
            TextPreviewScreen(
                contentState = TextPreviewContentUiState.Ready(
                    chunks = TextPreviewChunkRefFixtures.plainFile,
                ),
                chunkTexts = previewPlainChunkTexts,
                markwon = rememberTextPreviewMarkwon(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun TextPreviewScreenMarkdownRenderedPreview() {
    val markwon = rememberTextPreviewMarkwon()
    val spanneds = remember(markwon) {
        previewMarkdownSources.mapValues { (_, markdown) -> markwon.toMarkdown(markdown) }
    }
    HomeCloudTheme {
        Surface {
            TextPreviewScreen(
                contentState = TextPreviewContentUiState.Ready(
                    chunks = TextPreviewChunkRefFixtures.markdownFile,
                ),
                chunkTexts = previewMarkdownSources,
                chunkSpanneds = spanneds,
                isMarkdown = true,
                markdownTab = TextPreviewMarkdownTab.Rendered,
                markwon = markwon,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun TextPreviewScreenMarkdownPlainTabPreview() {
    HomeCloudTheme {
        Surface {
            TextPreviewScreen(
                contentState = TextPreviewContentUiState.Ready(
                    chunks = TextPreviewChunkRefFixtures.markdownFile,
                ),
                chunkTexts = previewMarkdownSources,
                isMarkdown = true,
                markdownTab = TextPreviewMarkdownTab.Plain,
                markwon = rememberTextPreviewMarkwon(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
            )
        }
    }
}
