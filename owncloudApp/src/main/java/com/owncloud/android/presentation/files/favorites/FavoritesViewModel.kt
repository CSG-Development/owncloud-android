package com.owncloud.android.presentation.files.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.domain.files.usecases.GetFavoriteFilesForAccountAsStreamUseCase
import com.owncloud.android.domain.files.usecases.SetFileFavoriteStatusUseCase
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.ViewType.Companion.PREF_FILE_LIST_GRID
import com.owncloud.android.presentation.files.filelist.clearFileSelection
import com.owncloud.android.presentation.files.filelist.compose.FileListComposeUiState
import com.owncloud.android.presentation.files.filelist.compose.FileListEmptyUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.fileListEmptyUiState
import com.owncloud.android.presentation.files.filelist.compose.fileListItemsUiState
import com.owncloud.android.presentation.files.filelist.compose.fileListLoadingUiState
import com.owncloud.android.presentation.files.filelist.inverseFileSelection
import com.owncloud.android.presentation.files.filelist.isOnlyListOrderChanged
import com.owncloud.android.presentation.files.filelist.retainFileSelection
import com.owncloud.android.presentation.files.filelist.selectAllFiles
import com.owncloud.android.presentation.files.filelist.toggleFileSelection
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
import com.owncloud.android.domain.files.usecases.SortType.Companion as SortTypeDomain

class FavoritesViewModel(
    private val getFavoriteFilesForAccountAsStreamUseCase: GetFavoriteFilesForAccountAsStreamUseCase,
    private val setFileFavoriteStatusUseCase: SetFileFavoriteStatusUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
) : ViewModel() {

    private val _favoritesUiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val favoritesUiState: StateFlow<FavoritesUiState> = _favoritesUiState

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

    val composeUiState: StateFlow<FileListComposeUiState> = combine(
        favoritesUiState,
        selectedIds,
        layoutMode,
        gridColumns,
        isMultiPersonal,
    ) { uiState, selected, mode, columns, multiPersonal ->
        toComposeUiState(
            favoritesUiState = uiState,
            selectedIds = selected,
            layoutMode = mode,
            gridColumns = columns,
            isMultiPersonal = multiPersonal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FileListComposeUiState(
            layoutMode = layoutMode.value,
            pullToRefreshEnabled = false,
        ),
    )

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }

        viewModelScope.launch {
            var previousContent: List<OCFileWithSyncInfo> = emptyList()
            favoritesUiState.collect { state ->
                val newContent = when (state) {
                    is FavoritesUiState.Success -> state.results
                    else -> emptyList()
                }
                if (state is FavoritesUiState.Success && isOnlyListOrderChanged(previousContent, newContent)) {
                    _scrollToTopEvents.tryEmit(Unit)
                }
                previousContent = newContent
                val selectableIds = newContent.mapNotNull { info ->
                    info.file.id?.takeUnless { info.file.isVirtualFile() }
                }.toSet()
                selectedIds.retainFileSelection(selectableIds)
            }
        }
    }

    fun loadFavorites(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            getFavoriteFilesForAccountAsStreamUseCase(
                GetFavoriteFilesForAccountAsStreamUseCase.Params(owner = accountName)
            ).collect { favorites ->
                val sorted = sortList(favorites, sortTypeAndOrder.value)
                _favoritesUiState.update {
                    if (sorted.isEmpty()) {
                        FavoritesUiState.Empty
                    } else {
                        FavoritesUiState.Success(sorted)
                    }
                }
            }
        }
    }

    fun toggleFavorite(fileId: Long, currentIsFavorite: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            setFileFavoriteStatusUseCase(
                SetFileFavoriteStatusUseCase.Params(
                    fileId = fileId,
                    isFavorite = !currentIsFavorite,
                )
            )
        }
    }

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }

        val currentState = _favoritesUiState.value
        if (currentState is FavoritesUiState.Success && currentState.results.isNotEmpty()) {
            val sortedResults = sortList(currentState.results, sortTypeAndOrder.value)
            _favoritesUiState.update { FavoritesUiState.Success(sortedResults) }
        }
    }

    fun getSortType(): SortType = sortTypeAndOrder.value.first
    fun getSortOrder(): SortOrder = sortTypeAndOrder.value.second

    fun setMultiPersonal(value: Boolean) {
        isMultiPersonal.value = value
    }

    fun setGridModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(PREF_FILE_LIST_GRID, true)
        layoutMode.value = FileListLayoutMode.Grid
    }

    fun setListModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(PREF_FILE_LIST_GRID, false)
        layoutMode.value = FileListLayoutMode.List
    }

    fun updateGridColumns(columns: Int) {
        gridColumns.value = columns.coerceAtLeast(1)
    }

    fun toggleSelection(fileId: Long) {
        selectedIds.toggleFileSelection(fileId)
    }

    fun clearSelection() {
        selectedIds.clearFileSelection()
    }

    fun selectAll() {
        selectedIds.selectAllFiles(composeUiState.value.selectableFileIds())
    }

    fun selectInverse() {
        selectedIds.inverseFileSelection(composeUiState.value.selectableFileIds())
    }

    fun isGridModeSetAsPreferred(): Boolean =
        sharedPreferencesProvider.getBoolean(PREF_FILE_LIST_GRID, false)

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
        favoritesUiState: FavoritesUiState,
        selectedIds: Set<Long>,
        layoutMode: FileListLayoutMode,
        gridColumns: Int,
        isMultiPersonal: Boolean,
    ): FileListComposeUiState = when (favoritesUiState) {
        FavoritesUiState.Loading -> fileListLoadingUiState(
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            pullToRefreshEnabled = false,
        )

        FavoritesUiState.Empty -> fileListEmptyUiState(
            emptyModel = FAVORITES_EMPTY,
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            pullToRefreshEnabled = false,
        )

        is FavoritesUiState.Success -> fileListItemsUiState(
            folderContent = favoritesUiState.results,
            emptyModel = FAVORITES_EMPTY,
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            pullToRefreshEnabled = false,
            showThreeDotMenu = false,
            showSpacePath = true,
            isMultiPersonal = isMultiPersonal,
            footerContext = contextProvider.getContext(),
        )
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

    sealed class FavoritesUiState {
        data object Loading : FavoritesUiState()
        data class Success(val results: List<OCFileWithSyncInfo>) : FavoritesUiState()
        data object Empty : FavoritesUiState()
    }

    companion object {
        private val FAVORITES_EMPTY = FileListEmptyUiModel(
            iconRes = R.drawable.ic_star_big_gray,
            titleRes = R.string.favorites_empty_title,
            subtitleRes = R.string.favorites_empty_subtitle,
        )
    }
}
