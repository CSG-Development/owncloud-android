/**
 * ownCloud Android client application
 *
 * @author Juan Carlos Garrote Gascón
 * @author Parneet Singh
 * @author Jorge Aguado Recio
 *
 * Copyright (C) 2024 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.owncloud.android.presentation.previews

import android.text.Spanned
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdAsStreamUseCase
import com.owncloud.android.presentation.previews.text.TextPreviewChunkKind
import com.owncloud.android.presentation.previews.text.TextPreviewChunkRef
import com.owncloud.android.presentation.previews.text.TextPreviewContentUiState
import com.owncloud.android.presentation.previews.text.TextPreviewDiskChunkLoader
import com.owncloud.android.presentation.previews.text.TextPreviewIndexSnapshot
import com.owncloud.android.presentation.previews.text.TextPreviewMarkdownChunkIndexer
import com.owncloud.android.presentation.previews.text.TextPreviewMarkdownChunkRenderer
import com.owncloud.android.presentation.previews.text.TextPreviewMarkdownTab
import com.owncloud.android.presentation.previews.text.TextPreviewPlainChunkIndexer
import com.owncloud.android.presentation.previews.text.chunksOrEmpty
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import io.noties.markwon.Markwon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

class PreviewTextViewModel(
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    getFileByIdAsStreamUseCase: GetFileByIdAsStreamUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val plainChunkIndexer: TextPreviewPlainChunkIndexer,
    private val markdownChunkIndexer: TextPreviewMarkdownChunkIndexer,
    private val diskChunkLoader: TextPreviewDiskChunkLoader,
    private val markdownChunkRenderer: TextPreviewMarkdownChunkRenderer,
    ocFile: OCFile,
) : ViewModel() {

    /** Same Markwon used for parse in [markdownChunkRenderer]; pass to Compose for display. */
    val markwon: Markwon get() = markdownChunkRenderer.markwon()

    private val _menuOptions: MutableStateFlow<List<FileMenuOption>> = MutableStateFlow(emptyList())
    val menuOptions: StateFlow<List<FileMenuOption>> = _menuOptions

    private val _contentUiState: MutableStateFlow<TextPreviewContentUiState> =
        MutableStateFlow(TextPreviewContentUiState.Idle)
    val contentUiState: StateFlow<TextPreviewContentUiState> = _contentUiState.asStateFlow()

    private val _chunkTexts: MutableStateFlow<Map<Int, String>> = MutableStateFlow(emptyMap())
    val chunkTexts: StateFlow<Map<Int, String>> = _chunkTexts.asStateFlow()

    private val _chunkSpanneds: MutableStateFlow<Map<Int, Spanned>> = MutableStateFlow(emptyMap())
    val chunkSpanneds: StateFlow<Map<Int, Spanned>> = _chunkSpanneds.asStateFlow()

    private val _isMarkdownFile: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isMarkdownFile: StateFlow<Boolean> = _isMarkdownFile.asStateFlow()

    private val _markdownTab: MutableStateFlow<TextPreviewMarkdownTab> =
        MutableStateFlow(TextPreviewMarkdownTab.Rendered)
    val markdownTab: StateFlow<TextPreviewMarkdownTab> = _markdownTab.asStateFlow()

    private val currentFile: Flow<OCFile?> = getFileByIdAsStreamUseCase(GetFileByIdAsStreamUseCase.Params(ocFile.id!!))

    private var storagePath: String? = null
    private var previewKind: TextPreviewChunkKind = TextPreviewChunkKind.Plain
    private var indexJob: Job? = null
    private var visibleLoadJob: Job? = null
    private var firstVisibleIndex: Int = 0
    private var lastVisibleIndex: Int = 0

    fun getCurrentFile(): Flow<OCFile?> = currentFile

    fun loadPreview(file: OCFile) {
        val path = file.storagePath
        if (path.isNullOrEmpty()) {
            Timber.e("Cannot preview text: storagePath is null or empty")
            _contentUiState.value = TextPreviewContentUiState.Error(
                IllegalStateException("storagePath is null or empty"),
            )
            return
        }
        if (isMarkdownFile(file)) {
            loadMarkdownPreview(path)
        } else {
            loadPlainPreview(path)
        }
    }

    fun reloadPreview() {
        val path = storagePath ?: return
        when (previewKind) {
            TextPreviewChunkKind.Plain -> loadPlainPreview(path)
            TextPreviewChunkKind.Markdown -> loadMarkdownPreview(path)
        }
    }

    fun selectMarkdownTab(tab: TextPreviewMarkdownTab) {
        if (_markdownTab.value == tab) return
        _markdownTab.value = tab
        scheduleVisibleChunkLoads()
    }

    fun onVisibleRangeChanged(firstVisible: Int, lastVisible: Int) {
        firstVisibleIndex = firstVisible.coerceAtLeast(0)
        lastVisibleIndex = lastVisible.coerceAtLeast(firstVisibleIndex)
        scheduleVisibleChunkLoads()
    }

    fun filterMenuOptions(file: OCFile, accountName: String) {
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = listOf(file),
                    accountName = accountName,
                    isAnyFileVideoPreviewing = false,
                    displaySelectAll = false,
                    displaySelectInverse = false,
                    onlyAvailableOfflineFiles = false,
                    onlySharedByLinkFiles = false,
                    shareViaLinkAllowed = shareViaLinkAllowed,
                    shareWithUsersAllowed = shareWithUsersAllowed,
                    sendAllowed = sendAllowed,
                )
            )
            result.apply {
                remove(FileMenuOption.RENAME)
                remove(FileMenuOption.MOVE)
                remove(FileMenuOption.COPY)
                remove(FileMenuOption.SYNC)
            }
            _menuOptions.update { result }
        }
    }

    private fun loadPlainPreview(path: String) {
        startIndexing(
            path = path,
            kind = TextPreviewChunkKind.Plain,
            indexer = { plainChunkIndexer.index(it) },
        )
    }

    private fun loadMarkdownPreview(path: String) {
        _markdownTab.value = TextPreviewMarkdownTab.Rendered
        startIndexing(
            path = path,
            kind = TextPreviewChunkKind.Markdown,
            indexer = { markdownChunkIndexer.index(it) },
        )
    }

    private fun startIndexing(
        path: String,
        kind: TextPreviewChunkKind,
        indexer: (String) -> Flow<TextPreviewIndexSnapshot>,
    ) {
        indexJob?.cancel()
        visibleLoadJob?.cancel()
        diskChunkLoader.clearCache()
        if (kind == TextPreviewChunkKind.Markdown) {
            markdownChunkRenderer.clearCache()
        }
        storagePath = path
        previewKind = kind
        _isMarkdownFile.value = kind == TextPreviewChunkKind.Markdown
        _chunkTexts.value = emptyMap()
        _chunkSpanneds.value = emptyMap()
        firstVisibleIndex = 0
        lastVisibleIndex = 0
        _contentUiState.value = TextPreviewContentUiState.Loading

        indexJob = viewModelScope.launch(coroutinesDispatcherProvider.io) {
            try {
                indexer(path).collect { snapshot ->
                    _contentUiState.value = if (snapshot.isComplete) {
                        TextPreviewContentUiState.Ready(chunks = snapshot.chunks)
                    } else {
                        TextPreviewContentUiState.Indexing(
                            chunks = snapshot.chunks,
                            bytesScanned = snapshot.bytesScanned,
                        )
                    }
                    scheduleVisibleChunkLoads()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.e(error, "Failed to index text preview ($kind): $path")
                _contentUiState.value = TextPreviewContentUiState.Error(error)
            }
        }
    }

    private fun scheduleVisibleChunkLoads() {
        val path = storagePath ?: return
        val chunks = _contentUiState.value.chunksOrEmpty()
        if (chunks.isEmpty()) return

        val needRenderedMarkdown =
            previewKind == TextPreviewChunkKind.Markdown &&
                _markdownTab.value == TextPreviewMarkdownTab.Rendered
        val prefetch = if (needRenderedMarkdown) PREFETCH_MARKDOWN_CHUNKS else PREFETCH_PLAIN_CHUNKS
        val keepExtra = if (needRenderedMarkdown) KEEP_EXTRA_MARKDOWN_CHUNKS else KEEP_EXTRA_PLAIN_CHUNKS

        visibleLoadJob?.cancel()
        visibleLoadJob = viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val lastIndex = chunks.lastIndex
            val visibleFirst = firstVisibleIndex.coerceIn(0, lastIndex)
            val visibleLast = lastVisibleIndex.coerceIn(visibleFirst, lastIndex)
            val loadFirst = (visibleFirst - prefetch).coerceAtLeast(0)
            val loadLast = (visibleLast + prefetch).coerceAtMost(lastIndex)
            val keepFirst = (visibleFirst - keepExtra).coerceAtLeast(0)
            val keepLast = (visibleLast + keepExtra).coerceAtMost(lastIndex)

            pruneChunkMapsOutside(chunks, keepFirst, keepLast)

            val indices = prioritizedIndices(
                visibleFirst = visibleFirst,
                visibleLast = visibleLast,
                loadFirst = loadFirst,
                loadLast = loadLast,
            )

            coroutineScope {
                val semaphore = Semaphore(MAX_CONCURRENT_CHUNK_LOADS)
                indices.map { index ->
                    async {
                        semaphore.withPermit {
                            ensureActive()
                            if (!isIndexWithin(index, keepFirst, keepLast)) return@withPermit

                            val ref = chunks[index]
                            try {
                                val text = diskChunkLoader.getCached(ref.id)
                                    ?: diskChunkLoader.load(path, ref)
                                if (!isIndexWithin(index, firstVisibleIndex - keepExtra, lastVisibleIndex + keepExtra)) {
                                    return@withPermit
                                }
                                publishChunkText(ref.id, text)

                                if (needRenderedMarkdown) {
                                    ensureActive()
                                    if (!isIndexWithin(index, firstVisibleIndex - keepExtra, lastVisibleIndex + keepExtra)) {
                                        return@withPermit
                                    }
                                    val spanned = markdownChunkRenderer.getCached(ref.id)
                                        ?: markdownChunkRenderer.render(ref.id, text)
                                    if (!isIndexWithin(index, firstVisibleIndex - keepExtra, lastVisibleIndex + keepExtra)) {
                                        return@withPermit
                                    }
                                    publishChunkSpanned(ref.id, spanned)
                                }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Exception) {
                                Timber.w(error, "Failed to load text chunk id=${ref.id}")
                            }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    private fun pruneChunkMapsOutside(
        chunks: List<TextPreviewChunkRef>,
        keepFirst: Int,
        keepLast: Int,
    ) {
        if (chunks.isEmpty() || keepFirst > keepLast) return
        val keepIds = chunks.subList(keepFirst, keepLast + 1).mapTo(HashSet()) { it.id }
        _chunkTexts.update { current ->
            if (current.keys.all { it in keepIds }) current else current.filterKeys { it in keepIds }
        }
        _chunkSpanneds.update { current ->
            if (current.keys.all { it in keepIds }) current else current.filterKeys { it in keepIds }
        }
    }

    private fun isIndexWithin(index: Int, first: Int, last: Int): Boolean {
        val safeFirst = first.coerceAtLeast(0)
        val safeLast = last.coerceAtLeast(safeFirst)
        return index in safeFirst..safeLast
    }

    private fun prioritizedIndices(
        visibleFirst: Int,
        visibleLast: Int,
        loadFirst: Int,
        loadLast: Int,
    ): List<Int> {
        val ordered = LinkedHashSet<Int>()
        for (index in visibleFirst..visibleLast) {
            ordered.add(index)
        }
        var before = visibleFirst - 1
        var after = visibleLast + 1
        while (before >= loadFirst || after <= loadLast) {
            if (after <= loadLast) {
                ordered.add(after)
                after++
            }
            if (before >= loadFirst) {
                ordered.add(before)
                before--
            }
        }
        return ordered.toList()
    }

    private fun publishChunkText(chunkId: Int, text: String) {
        _chunkTexts.update { current ->
            if (current[chunkId] == text) current else current + (chunkId to text)
        }
    }

    private fun publishChunkSpanned(chunkId: Int, spanned: Spanned) {
        _chunkSpanneds.update { current ->
            if (current[chunkId] === spanned) current else current + (chunkId to spanned)
        }
    }

    companion object {
        private const val MIME_TYPE_MARKDOWN = "text/markdown"
        private const val PREFETCH_PLAIN_CHUNKS = 4
        private const val PREFETCH_MARKDOWN_CHUNKS = 8
        private const val KEEP_EXTRA_PLAIN_CHUNKS = 8
        private const val KEEP_EXTRA_MARKDOWN_CHUNKS = 16
        private const val MAX_CONCURRENT_CHUNK_LOADS = 2

        fun isMarkdownFile(file: OCFile): Boolean =
            file.mimeType == MIME_TYPE_MARKDOWN || file.getMimeTypeFromName() == MIME_TYPE_MARKDOWN
    }
}
