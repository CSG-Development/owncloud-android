package com.owncloud.android.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.domain.tags.usecases.RefreshFilesByTagUseCase
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.ViewType.Companion.PREF_FILE_LIST_GRID
import com.owncloud.android.presentation.files.filelist.compose.FileListComposeUiState
import com.owncloud.android.presentation.files.filelist.compose.FileListEmptyUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.fileListEmptyUiState
import com.owncloud.android.presentation.files.filelist.compose.fileListItemsUiState
import com.owncloud.android.presentation.files.filelist.compose.fileListLoadingUiState
import com.owncloud.android.presentation.files.filelist.isOnlyListOrderChanged
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

class TagFilesViewModel(
    private val refreshFilesByTagUseCase: RefreshFilesByTagUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val contextProvider: ContextProvider,
) : ViewModel() {

    private val uiState = MutableStateFlow<TagFilesUiState>(TagFilesUiState.Loading)

    private val _menuOptionsSingleFile = MutableSharedFlow<List<FileMenuOption>>()
    val menuOptionsSingleFile: SharedFlow<List<FileMenuOption>> = _menuOptionsSingleFile

    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))

    private val layoutMode = MutableStateFlow(
        if (isGridModeSetAsPreferred()) FileListLayoutMode.Grid else FileListLayoutMode.List
    )
    private val gridColumns = MutableStateFlow(3)
    private val isRefreshing = MutableStateFlow(false)

    private val _scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvents: SharedFlow<Unit> = _scrollToTopEvents.asSharedFlow()

    val composeUiState: StateFlow<FileListComposeUiState> = combine(
        uiState,
        layoutMode,
        gridColumns,
        isRefreshing,
    ) { state, mode, columns, refreshing ->
        toComposeUiState(
            tagFilesUiState = state,
            layoutMode = mode,
            gridColumns = columns,
            isRefreshing = refreshing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FileListComposeUiState(layoutMode = layoutMode.value),
    )

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }

        viewModelScope.launch {
            var previousContent: List<OCFileWithSyncInfo> = emptyList()
            uiState.collect { state ->
                val newContent = when (state) {
                    is TagFilesUiState.Success -> state.files
                    else -> emptyList()
                }
                if (state is TagFilesUiState.Success && isOnlyListOrderChanged(previousContent, newContent)) {
                    _scrollToTopEvents.tryEmit(Unit)
                }
                previousContent = newContent
            }
        }
    }

    fun loadFiles(accountName: String, serverTagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val keepVisibleContent = when (uiState.value) {
                is TagFilesUiState.Success,
                TagFilesUiState.Empty,
                is TagFilesUiState.Error,
                -> true

                TagFilesUiState.Loading -> false
            }
            if (keepVisibleContent) {
                isRefreshing.value = true
            } else {
                uiState.update { TagFilesUiState.Loading }
            }

            val result = refreshFilesByTagUseCase(
                RefreshFilesByTagUseCase.Params(accountName = accountName, serverTagId = serverTagId)
            )

            when (result) {
                is UseCaseResult.Success -> {
                    val filesWithSyncInfo = result.data.map { file ->
                        OCFileWithSyncInfo(
                            file = file,
                            uploadWorkerUuid = null,
                            downloadWorkerUuid = null,
                            isSynchronizing = false,
                            space = null,
                        )
                    }
                    val sorted = sortList(filesWithSyncInfo, sortTypeAndOrder.value)
                    uiState.update {
                        if (sorted.isEmpty()) TagFilesUiState.Empty else TagFilesUiState.Success(sorted)
                    }
                }

                is UseCaseResult.Error -> {
                    uiState.update { TagFilesUiState.Error(result.throwable) }
                }
            }
            isRefreshing.value = false
        }
    }

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }

        val currentState = uiState.value
        if (currentState is TagFilesUiState.Success && currentState.files.isNotEmpty()) {
            val sorted = sortList(currentState.files, sortTypeAndOrder.value)
            uiState.update { TagFilesUiState.Success(sorted) }
        }
    }

    fun getSortType(): SortType = sortTypeAndOrder.value.first
    fun getSortOrder(): SortOrder = sortTypeAndOrder.value.second

    fun filterMenuOptionsForSingleFile(fileWithSyncInfo: OCFileWithSyncInfo) {
        val file = fileWithSyncInfo.file
        val fileSyncInfo = OCFileSyncInfo(
            fileId = file.id!!,
            uploadWorkerUuid = fileWithSyncInfo.uploadWorkerUuid,
            downloadWorkerUuid = fileWithSyncInfo.downloadWorkerUuid,
            isSynchronizing = fileWithSyncInfo.isSynchronizing,
        )
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = listOf(file),
                    filesSyncInfo = listOf(fileSyncInfo),
                    accountName = file.owner,
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
            _menuOptionsSingleFile.emit(result)
        }
    }

    fun isGridModeSetAsPreferred(): Boolean =
        sharedPreferencesProvider.getBoolean(PREF_FILE_LIST_GRID, false)

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

    private fun toComposeUiState(
        tagFilesUiState: TagFilesUiState,
        layoutMode: FileListLayoutMode,
        gridColumns: Int,
        isRefreshing: Boolean,
    ): FileListComposeUiState =
        when (tagFilesUiState) {
            TagFilesUiState.Loading -> fileListLoadingUiState(
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                isRefreshing = isRefreshing,
            )

            TagFilesUiState.Empty -> fileListEmptyUiState(
                emptyModel = TAG_FILES_EMPTY,
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                isRefreshing = isRefreshing,
            )

            is TagFilesUiState.Error -> fileListEmptyUiState(
                emptyModel = FileListEmptyUiModel(
                    iconRes = R.drawable.ic_tag_big,
                    titleText = tagFilesUiState.throwable.localizedMessage
                        ?: tagFilesUiState.throwable.message
                        ?: contextProvider.getContext().getString(R.string.common_error_unknown),
                ),
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                isRefreshing = isRefreshing,
            )

            is TagFilesUiState.Success -> fileListItemsUiState(
                folderContent = tagFilesUiState.files,
                emptyModel = TAG_FILES_EMPTY,
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                isRefreshing = isRefreshing,
                showThreeDotMenu = true,
                showSpacePath = false,
                isMultiPersonal = false,
                footerContext = contextProvider.getContext(),
            )
        }

    private fun sortList(
        filesWithSyncInfo: List<OCFileWithSyncInfo>,
        sortTypeAndOrder: Pair<SortType, SortOrder>,
    ): List<OCFileWithSyncInfo> =
        sortFilesWithSyncInfoUseCase(
            SortFilesWithSyncInfoUseCase.Params(
                listOfFiles = filesWithSyncInfo,
                sortType = SortTypeDomain.fromPreferences(sortTypeAndOrder.first.ordinal),
                ascending = sortTypeAndOrder.second == SortOrder.SORT_ORDER_ASCENDING,
            )
        )

    private sealed class TagFilesUiState {
        data object Loading : TagFilesUiState()
        data class Success(val files: List<OCFileWithSyncInfo>) : TagFilesUiState()
        data object Empty : TagFilesUiState()
        data class Error(val throwable: Throwable) : TagFilesUiState()
    }

    companion object {
        private val TAG_FILES_EMPTY = FileListEmptyUiModel(
            iconRes = R.drawable.ic_tag_big,
            titleRes = R.string.tag_files_empty_title,
        )
    }
}
