package com.owncloud.android.presentation.previews.compose

import android.text.Spanned
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.previews.text.TextPreviewContentUiState
import com.owncloud.android.presentation.previews.text.TextPreviewMarkdownTab
import com.owncloud.android.utils.PreferenceUtils
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val EmptySpannedMapFlow: StateFlow<Map<Int, Spanned>> = MutableStateFlow(emptyMap())
private val FalseFlow: StateFlow<Boolean> = MutableStateFlow(false)
private val DefaultMarkdownTabFlow: StateFlow<TextPreviewMarkdownTab> =
    MutableStateFlow(TextPreviewMarkdownTab.Rendered)

/**
 * Collects text-preview state and renders [TextPreviewScreen] inside [HomeCloudTheme].
 * Uses the same [Markwon] instance as the ViewModel renderer for display.
 */
@Composable
fun TextPreviewHost(
    contentUiStateFlow: StateFlow<TextPreviewContentUiState>,
    chunkTextsFlow: StateFlow<Map<Int, String>>,
    markwon: Markwon,
    modifier: Modifier = Modifier,
    chunkSpannedsFlow: StateFlow<Map<Int, Spanned>> = EmptySpannedMapFlow,
    isMarkdownFlow: StateFlow<Boolean> = FalseFlow,
    markdownTabFlow: StateFlow<TextPreviewMarkdownTab> = DefaultMarkdownTabFlow,
    onMarkdownTabSelected: (TextPreviewMarkdownTab) -> Unit = {},
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
) {
    val contentState by contentUiStateFlow.collectAsState()
    val chunkTexts by chunkTextsFlow.collectAsState()
    val chunkSpanneds by chunkSpannedsFlow.collectAsState()
    val isMarkdown by isMarkdownFlow.collectAsState()
    val markdownTab by markdownTabFlow.collectAsState()
    val plainListState = rememberLazyListState()
    val markdownListState = rememberLazyListState()

    HomeCloudTheme {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            TextPreviewScreen(
                contentState = contentState,
                chunkTexts = chunkTexts,
                chunkSpanneds = chunkSpanneds,
                isMarkdown = isMarkdown,
                markdownTab = markdownTab,
                onMarkdownTabSelected = onMarkdownTabSelected,
                plainListState = plainListState,
                markdownListState = markdownListState,
                markwon = markwon,
                onVisibleRangeChanged = onVisibleRangeChanged,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun ComposeView.setTextPreviewContent(
    contentUiStateFlow: StateFlow<TextPreviewContentUiState>,
    chunkTextsFlow: StateFlow<Map<Int, String>>,
    markwon: Markwon,
    onVisibleRangeChanged: (firstVisible: Int, lastVisible: Int) -> Unit,
    chunkSpannedsFlow: StateFlow<Map<Int, Spanned>> = EmptySpannedMapFlow,
    isMarkdownFlow: StateFlow<Boolean> = FalseFlow,
    markdownTabFlow: StateFlow<TextPreviewMarkdownTab> = DefaultMarkdownTabFlow,
    onMarkdownTabSelected: (TextPreviewMarkdownTab) -> Unit = {},
) {
    filterTouchesWhenObscured =
        PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setContent {
        TextPreviewHost(
            contentUiStateFlow = contentUiStateFlow,
            chunkTextsFlow = chunkTextsFlow,
            markwon = markwon,
            chunkSpannedsFlow = chunkSpannedsFlow,
            isMarkdownFlow = isMarkdownFlow,
            markdownTabFlow = markdownTabFlow,
            onMarkdownTabSelected = onMarkdownTabSelected,
            onVisibleRangeChanged = onVisibleRangeChanged,
        )
    }
}
