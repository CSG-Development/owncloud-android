package com.owncloud.android.presentation.files.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.usecases.SearchFilesUseCase
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.domain.tags.usecases.GetTagsByLocalIdsUseCase
import com.owncloud.android.domain.tags.usecases.GetTagsForAccountUseCase
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.filelist.FileListFooterText
import com.owncloud.android.presentation.files.filelist.MainFileListViewModel.Companion.RECYCLER_VIEW_PREFERRED
import com.owncloud.android.presentation.files.filelist.compose.FileListContent
import com.owncloud.android.presentation.files.filelist.compose.FileListEmptyUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.toFileListItemUiModel
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.owncloud.android.domain.files.usecases.SortType.Companion as SortTypeDomain

class GlobalSearchViewModel(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val getTagsForAccountUseCase: GetTagsForAccountUseCase,
    private val getTagsByLocalIdsUseCase: GetTagsByLocalIdsUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : ViewModel() {

    private val searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)

    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val layoutMode = MutableStateFlow(
        if (isGridModeSetAsPreferred()) FileListLayoutMode.Grid else FileListLayoutMode.List
    )
    private val gridColumns = MutableStateFlow(3)
    private val isMultiPersonal = MutableStateFlow(false)

    private val _scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvents: SharedFlow<Unit> = _scrollToTopEvents.asSharedFlow()

    private val _menuOptions: MutableSharedFlow<List<FileMenuOption>> = MutableSharedFlow()
    val menuOptions: SharedFlow<List<FileMenuOption>> = _menuOptions

    private val _filtersState = MutableStateFlow(SearchFiltersState())
    val filtersState: StateFlow<SearchFiltersState> = _filtersState

    private val _tagsLoading = MutableStateFlow(false)
    val tagsLoading: StateFlow<Boolean> = _tagsLoading

    private val _openTagsBottomSheetEvent = MutableSharedFlow<List<OCTag>>()
    val openTagsBottomSheetEvent: SharedFlow<List<OCTag>> = _openTagsBottomSheetEvent

    val composeUiState: StateFlow<GlobalSearchComposeUiState> = combine(
        searchUiState,
        selectedIds,
        layoutMode,
        gridColumns,
        isMultiPersonal,
    ) { uiState, selected, mode, columns, multiPersonal ->
        toComposeUiState(
            searchUiState = uiState,
            selectedIds = selected,
            layoutMode = mode,
            gridColumns = columns,
            isMultiPersonal = multiPersonal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GlobalSearchComposeUiState(
            content = FileListContent.Empty(INITIAL_EMPTY),
            layoutMode = layoutMode.value,
        ),
    )

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }

        viewModelScope.launch {
            searchUiState.collect { state ->
                val newContent = when (state) {
                    is SearchUiState.Success -> state.results
                    else -> emptyList()
                }
                if (state is SearchUiState.Success) {
                    _scrollToTopEvents.tryEmit(Unit)
                }
                val currentIds = newContent.mapNotNull { it.file.id }.toSet()
                selectedIds.update { it.intersect(currentIds) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        performSearch(query, hasFiltersSelected())
    }

    fun updateTypeFilters(selectedTypeIds: Set<TypeFilter>) {
        _filtersState.update { it.copy(selectedTypes = selectedTypeIds) }
        val currentSearchQuery = searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    private fun updateDateFilter(dateFilter: DateFilter) {
        _filtersState.update { it.copy(dateFilter = dateFilter) }
        val currentSearchQuery = searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    fun updateDateFilterById(filterId: String) {
        val dateFilter = DateFilter.fromId(filterId)
        updateDateFilter(dateFilter)
    }

    private fun updateSizeFilter(sizeFilter: SizeFilter) {
        _filtersState.update { it.copy(sizeFilter = sizeFilter) }
        val currentSearchQuery = searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    fun updateSizeFilterById(filterId: String) {
        val sizeFilter = SizeFilter.fromId(filterId)
        updateSizeFilter(sizeFilter)
    }

    fun loadTagsForAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _tagsLoading.update { true }
            val result = getTagsForAccountUseCase(GetTagsForAccountUseCase.Params(accountName = accountName))
            if (result is UseCaseResult.Success) {
                val tags = result.data.filter { it.userVisible }
                _openTagsBottomSheetEvent.emit(tags)
            }
            _tagsLoading.update { false }
        }
    }

    fun updateTagFilters(selectedTagLocalIds: Set<Long>) {
        viewModelScope.launch {
            val tags = withContext(coroutinesDispatcherProvider.io) {
                getTagsByLocalIdsUseCase(
                    GetTagsByLocalIdsUseCase.Params(
                        selectedTagLocalIds
                            .toList()
                    )
                )
            }
            _filtersState.update { it.copy(selectedTags = tags.getDataOrNull() ?: emptyList()) }
            val currentSearchQuery = searchUiState.value.query
            performSearch(currentSearchQuery, true)
        }
    }

    private fun hasFiltersSelected(): Boolean =
        _filtersState.value.selectedTypes.isNotEmpty() ||
                _filtersState.value.dateFilter != DateFilter.ANY ||
                _filtersState.value.sizeFilter != SizeFilter.ANY ||
                _filtersState.value.selectedTags.isNotEmpty()

    fun getFiltersState(): SearchFiltersState = _filtersState.value

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }

        val currentState = searchUiState.value
        if (currentState is SearchUiState.Success && currentState.results.isNotEmpty()) {
            val sortedResults = sortList(currentState.results, sortTypeAndOrder.value)
            searchUiState.update { SearchUiState.Success(sortedResults, it.query) }
        }
    }

    fun getSortType(): SortType = sortTypeAndOrder.value.first

    fun getSortOrder(): SortOrder = sortTypeAndOrder.value.second

    fun setMultiPersonal(value: Boolean) {
        isMultiPersonal.value = value
    }

    fun setGridModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, true)
        layoutMode.value = FileListLayoutMode.Grid
    }

    fun setListModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, false)
        layoutMode.value = FileListLayoutMode.List
    }

    fun updateGridColumns(columns: Int) {
        gridColumns.value = columns.coerceAtLeast(1)
    }

    fun toggleSelection(fileId: Long) {
        selectedIds.update { current ->
            if (fileId in current) current - fileId else current + fileId
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        selectedIds.value = composeUiState.value.selectableFileIds().toSet()
    }

    fun selectInverse() {
        val ids = composeUiState.value.selectableFileIds()
        val current = selectedIds.value
        selectedIds.value = ids.filterTo(mutableSetOf()) { it !in current }
    }

    fun isGridModeSetAsPreferred(): Boolean =
        sharedPreferencesProvider.getBoolean(RECYCLER_VIEW_PREFERRED, false)

    private fun performSearch(query: String, allowEmptyQuery: Boolean = false) {
        if (query.isBlank() && !allowEmptyQuery) {
            searchUiState.update { SearchUiState.Initial }
            return
        }

        searchUiState.update { SearchUiState.Loading(it.query) }

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            try {
                val filters = _filtersState.value
                val result = searchFilesUseCase(
                    SearchFilesUseCase.Params(
                        searchPattern = query,
                        ignoreCase = true,
                        minDate = filters.dateFilter.getMinDate(),
                        maxDate = Long.MAX_VALUE,
                        minSize = filters.sizeFilter.getMinSize(),
                        maxSize = filters.sizeFilter.getMaxSize(),
                        tagLocalIds = filters.selectedTags.map { it.localId },
                    )
                )

                val filteredResult = if (filters.selectedTypes.isNotEmpty()) {
                    val mimePatterns = filters.getMimePatterns()
                    result.filter { file ->
                        mimePatterns.isEmpty() || mimePatterns.any { pattern ->
                            (pattern == TYPE_FILE && !file.isFolder) ||
                                    file.mimeType.startsWith(pattern) || file.mimeType == pattern
                        }
                    }
                } else {
                    result
                }

                val filesWithSyncInfo = filteredResult.map { file ->
                    OCFileWithSyncInfo(
                        file = file,
                        uploadWorkerUuid = null,
                        downloadWorkerUuid = null,
                        isSynchronizing = false,
                        space = null,
                    )
                }

                val sortedResults = sortList(filesWithSyncInfo, sortTypeAndOrder.value)

                searchUiState.update {
                    if (sortedResults.isEmpty()) {
                        SearchUiState.Empty(query)
                    } else {
                        SearchUiState.Success(sortedResults, query)
                    }
                }
            } catch (e: Exception) {
                searchUiState.update { SearchUiState.Error(e.message ?: "Unknown error", it.query) }
            }
        }
    }

    private fun sortList(
        filesWithSyncInfo: List<OCFileWithSyncInfo>,
        sortTypeAndOrder: Pair<SortType, SortOrder>
    ): List<OCFileWithSyncInfo> =
        sortFilesWithSyncInfoUseCase(
            SortFilesWithSyncInfoUseCase.Params(
                listOfFiles = filesWithSyncInfo,
                sortType = SortTypeDomain.fromPreferences(sortTypeAndOrder.first.ordinal),
                ascending = sortTypeAndOrder.second == SortOrder.SORT_ORDER_ASCENDING
            )
        )

    fun filterMenuOptions(
        files: List<OCFile>,
        filesSyncInfo: List<OCFileSyncInfo>,
        displaySelectAll: Boolean,
        isMultiselection: Boolean
    ) {
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        val accountName = files.firstOrNull()?.owner ?: return

        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = files,
                    filesSyncInfo = filesSyncInfo,
                    accountName = accountName,
                    isAnyFileVideoPreviewing = false,
                    displaySelectAll = displaySelectAll,
                    displaySelectInverse = isMultiselection,
                    onlyAvailableOfflineFiles = false,
                    onlySharedByLinkFiles = false,
                    shareViaLinkAllowed = shareViaLinkAllowed,
                    shareWithUsersAllowed = shareWithUsersAllowed,
                    sendAllowed = sendAllowed,
                )
            )
            _menuOptions.emit(result)
        }
    }

    private fun toComposeUiState(
        searchUiState: SearchUiState,
        selectedIds: Set<Long>,
        layoutMode: FileListLayoutMode,
        gridColumns: Int,
        isMultiPersonal: Boolean,
    ): GlobalSearchComposeUiState {
        return when (searchUiState) {
            SearchUiState.Initial -> GlobalSearchComposeUiState(
                content = FileListContent.Empty(INITIAL_EMPTY),
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
            )

            is SearchUiState.Loading -> GlobalSearchComposeUiState(
                content = FileListContent.Loading,
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
            )

            is SearchUiState.Empty -> GlobalSearchComposeUiState(
                content = FileListContent.Empty(RESULTS_EMPTY),
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
            )

            is SearchUiState.Error -> GlobalSearchComposeUiState(
                content = FileListContent.Empty(
                    FileListEmptyUiModel(
                        iconRes = R.drawable.ic_search,
                        titleText = searchUiState.message,
                    ),
                ),
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
            )

            is SearchUiState.Success -> {
                val folderContent = searchUiState.results
                val items = folderContent.map { info ->
                    info.toFileListItemUiModel(
                        showThreeDotMenu = true,
                        showSpacePath = false,
                        isMultiPersonal = isMultiPersonal,
                    )
                }
                GlobalSearchComposeUiState(
                    folderContent = folderContent,
                    content = FileListContent.Items(
                        items = items,
                        footerText = FileListFooterText.fromFiles(contextProvider.getContext(), folderContent),
                    ),
                    layoutMode = layoutMode,
                    gridColumns = gridColumns,
                    selectedIds = selectedIds,
                )
            }
        }
    }

    private sealed class SearchUiState(open val query: String) {
        data object Initial : SearchUiState("")
        data class Loading(override val query: String) : SearchUiState(query)
        data class Success(val results: List<OCFileWithSyncInfo>, override val query: String) : SearchUiState(query)
        data class Empty(override val query: String) : SearchUiState(query)
        data class Error(val message: String, override val query: String) : SearchUiState(query)
    }

    companion object {
        private val INITIAL_EMPTY = FileListEmptyUiModel(
            iconRes = R.drawable.ic_search_2,
            titleRes = R.string.homecloud_global_search_initial_title,
        )
        private val RESULTS_EMPTY = FileListEmptyUiModel(
            iconRes = R.drawable.ic_search_2,
            titleRes = R.string.homecloud_global_search_empty_title,
            subtitleRes = R.string.homecloud_global_search_empty_subtitle,
        )
    }
}
