package com.owncloud.android.presentation.files.globalsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.usecases.SearchFilesUseCase
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.filelist.MainFileListViewModel.Companion.RECYCLER_VIEW_PREFERRED
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.owncloud.android.domain.files.usecases.SortType.Companion as SortTypeDomain

class GlobalSearchViewModel(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : ViewModel() {

    private val _searchUiState = MutableStateFlow<SearchUiState>(SearchUiState.Initial)
    val searchUiState: StateFlow<SearchUiState> = _searchUiState

    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))

    private val _menuOptions: MutableSharedFlow<List<FileMenuOption>> = MutableSharedFlow()
    val menuOptions: SharedFlow<List<FileMenuOption>> = _menuOptions

    private val _filtersState = MutableStateFlow(SearchFiltersState())
    val filtersState: StateFlow<SearchFiltersState> = _filtersState

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }
    }

    fun updateSearchQuery(query: String) {
        performSearch(query)
    }

    fun updateTypeFilters(selectedTypeIds: Set<TypeFilter>) {
        _filtersState.update { it.copy(selectedTypes = selectedTypeIds) }
        val currentSearchQuery = _searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    private fun updateDateFilter(dateFilter: DateFilter) {
        _filtersState.update { it.copy(dateFilter = dateFilter) }
        val currentSearchQuery = _searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    fun updateDateFilterById(filterId: String) {
        val dateFilter = DateFilter.fromId(filterId)
        updateDateFilter(dateFilter)
    }

    private fun updateSizeFilter(sizeFilter: SizeFilter) {
        _filtersState.update { it.copy(sizeFilter = sizeFilter) }
        val currentSearchQuery = _searchUiState.value.query
        performSearch(currentSearchQuery, true)
    }

    fun updateSizeFilterById(filterId: String) {
        val sizeFilter = SizeFilter.fromId(filterId)
        updateSizeFilter(sizeFilter)
    }

    fun getFiltersState(): SearchFiltersState = _filtersState.value

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }

        // Re-sort current results if we have any
        val currentState = _searchUiState.value
        if (currentState is SearchUiState.Success && currentState.results.isNotEmpty()) {
            val sortedResults = sortList(currentState.results, sortTypeAndOrder.value)
            _searchUiState.update { SearchUiState.Success(sortedResults, it.query) }
        }
    }

    fun getSortType(): SortType = sortTypeAndOrder.value.first

    fun getSortOrder(): SortOrder = sortTypeAndOrder.value.second

    private fun performSearch(query: String, allowEmptyQuery: Boolean = false) {
        if (query.isBlank() && !allowEmptyQuery) {
            _searchUiState.update { SearchUiState.Initial }
            return
        }

        _searchUiState.update { SearchUiState.Loading(it.query) }

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

                _searchUiState.update {
                    if (sortedResults.isEmpty()) {
                        SearchUiState.Empty(query)
                    } else {
                        SearchUiState.Success(sortedResults, query)
                    }
                }
            } catch (e: Exception) {
                _searchUiState.update { SearchUiState.Error(e.message ?: "Unknown error", it.query) }
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

    fun isGridModeSetAsPreferred(): Boolean =
        sharedPreferencesProvider.getBoolean(RECYCLER_VIEW_PREFERRED, false)

    fun setGridModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, true)
    }

    fun setListModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, false)
    }

    sealed class SearchUiState(open val query: String) {
        object Initial : SearchUiState("")
        data class Loading(override val query: String) : SearchUiState(query)
        data class Success(val results: List<OCFileWithSyncInfo>, override val query: String) : SearchUiState(query)
        data class Empty(override val query: String) : SearchUiState(query)
        data class Error(val message: String, override val query: String) : SearchUiState(query)
    }
}
