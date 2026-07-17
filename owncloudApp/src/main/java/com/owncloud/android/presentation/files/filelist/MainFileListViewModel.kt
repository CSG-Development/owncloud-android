/**
 * ownCloud Android client application
 *
 * @author Fernando Sanz Velasco
 * @author Jose Antonio Barros Ramos
 * @author Juan Carlos Garrote Gascón
 *
 * Copyright (C) 2023 ownCloud GmbH.
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

package com.owncloud.android.presentation.files.filelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.appregistry.model.AppRegistryMimeType
import com.owncloud.android.domain.appregistry.usecases.GetAppRegistryForMimeTypeAsStreamUseCase
import com.owncloud.android.domain.appregistry.usecases.GetAppRegistryWhichAllowCreationAsStreamUseCase
import com.owncloud.android.domain.appregistry.usecases.GetUrlToOpenInWebUseCase
import com.owncloud.android.domain.availableoffline.usecases.GetFilesAvailableOfflineFromAccountAsStreamUseCase
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFile.Companion.ROOT_PARENT_ID
import com.owncloud.android.domain.files.model.OCFile.Companion.ROOT_PATH
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.uploadTransferId
import com.owncloud.android.domain.files.usecases.GetFileByIdUseCase
import com.owncloud.android.domain.files.usecases.GetFileByRemotePathUseCase
import com.owncloud.android.domain.files.usecases.GetFileByRemotePathUseCase.Params
import com.owncloud.android.domain.files.usecases.GetFolderContentAsStreamUseCase
import com.owncloud.android.domain.files.usecases.GetSharedByLinkForAccountAsStreamUseCase
import com.owncloud.android.domain.files.usecases.SortFilesWithSyncInfoUseCase
import com.owncloud.android.domain.spaces.model.OCSpace
import com.owncloud.android.domain.spaces.usecases.GetSpaceWithSpecialsByIdForAccountUseCase
import com.owncloud.android.domain.utils.Event
import com.owncloud.android.extensions.ViewModelExt.runUseCaseWithResult
import com.owncloud.android.presentation.common.UIResult
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.filelist.compose.FileListContent
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.toFileListEmptyUiModel
import com.owncloud.android.presentation.files.filelist.compose.toFileListItemUiModel
import com.owncloud.android.presentation.files.operations.ArchiveWorkEnqueued
import com.owncloud.android.presentation.settings.advanced.SettingsAdvancedFragment.Companion.PREF_SHOW_HIDDEN_FILES
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.providers.WorkManagerProvider
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase.SyncFolderMode.SYNC_CONTENTS
import com.owncloud.android.usecases.synchronization.UpdateFoldersRecursivelyUseCase
import com.owncloud.android.workers.DownloadFileWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import com.owncloud.android.domain.files.usecases.SortType.Companion as SortTypeDomain

class MainFileListViewModel(
    private val getFolderContentAsStreamUseCase: GetFolderContentAsStreamUseCase,
    private val getSharedByLinkForAccountAsStreamUseCase: GetSharedByLinkForAccountAsStreamUseCase,
    private val getFilesAvailableOfflineFromAccountAsStreamUseCase: GetFilesAvailableOfflineFromAccountAsStreamUseCase,
    private val getFileByIdUseCase: GetFileByIdUseCase,
    private val getFileByRemotePathUseCase: GetFileByRemotePathUseCase,
    private val getSpaceWithSpecialsByIdForAccountUseCase: GetSpaceWithSpecialsByIdForAccountUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val synchronizeFolderUseCase: SynchronizeFolderUseCase,
    private val updateFoldersRecursivelyUseCase: UpdateFoldersRecursivelyUseCase,
    getAppRegistryWhichAllowCreationAsStreamUseCase: GetAppRegistryWhichAllowCreationAsStreamUseCase,
    private val getAppRegistryForMimeTypeAsStreamUseCase: GetAppRegistryForMimeTypeAsStreamUseCase,
    private val getUrlToOpenInWebUseCase: GetUrlToOpenInWebUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val workManagerProvider: WorkManagerProvider,
    initialFolderToDisplay: OCFile,
    fileListOptionParam: FileListOption,
    private val isPickerMode: Boolean = false,
) : ViewModel() {

    private val showHiddenFiles: Boolean = sharedPreferencesProvider.getBoolean(PREF_SHOW_HIDDEN_FILES, false)

    val currentFolderDisplayed: MutableStateFlow<OCFile> = MutableStateFlow(initialFolderToDisplay)
    val fileListOption: MutableStateFlow<FileListOption> = MutableStateFlow(fileListOptionParam)
    private val searchFilter: MutableStateFlow<String> = MutableStateFlow("")
    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))
    val space: MutableStateFlow<OCSpace?> = MutableStateFlow(null)

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    private val layoutMode = MutableStateFlow(
        if (isGridModeSetAsPreferred()) FileListLayoutMode.Grid else FileListLayoutMode.List
    )
    private val gridColumns = MutableStateFlow(3)
    private val isRefreshing = MutableStateFlow(false)
    private val isMultiPersonal = MutableStateFlow(false)

    private val _scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvents: SharedFlow<Unit> = _scrollToTopEvents.asSharedFlow()

    val appRegistryToCreateFiles: StateFlow<List<AppRegistryMimeType>> =
        getAppRegistryWhichAllowCreationAsStreamUseCase(
            GetAppRegistryWhichAllowCreationAsStreamUseCase.Params(
                accountName = initialFolderToDisplay.owner
            )
        ).stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val _appRegistryMimeType: MutableSharedFlow<AppRegistryMimeType?> = MutableSharedFlow()
    val appRegistryMimeType: SharedFlow<AppRegistryMimeType?> = _appRegistryMimeType

    private val _appRegistryMimeTypeSingleFile: MutableSharedFlow<AppRegistryMimeType?> = MutableSharedFlow()
    val appRegistryMimeTypeSingleFile: SharedFlow<AppRegistryMimeType?> = _appRegistryMimeTypeSingleFile

    private val _openInWebFlow = MutableStateFlow<Event<UIResult<String>>?>(null)
    val openInWebFlow: StateFlow<Event<UIResult<String>>?> = _openInWebFlow

    private val _menuOptions: MutableSharedFlow<List<FileMenuOption>> = MutableSharedFlow()
    val menuOptions: SharedFlow<List<FileMenuOption>> = _menuOptions

    private val _menuOptionsSingleFile: MutableSharedFlow<List<FileMenuOption>> = MutableSharedFlow()
    val menuOptionsSingleFile: SharedFlow<List<FileMenuOption>> = _menuOptionsSingleFile

    // Must be initialized before fileListUiState: toFileListUiState() combines these flows.
    private val uploadProgressByTransferId: StateFlow<Map<Long, Int>> =
        workManagerProvider.getRunningUploadsWorkInfosLiveData()
            .asFlow()
            .map { runningWorkInfos ->
                runningWorkInfos.mapNotNull { workInfo ->
                    val transferId = workInfo.extractTransferIdFromTags() ?: return@mapNotNull null
                    val progress = workInfo.progress.getInt(DownloadFileWorker.WORKER_KEY_PROGRESS, 0).coerceIn(0, 100)
                    transferId to progress
                }.toMap()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    private val _archiveWorkMetadata = MutableStateFlow<Map<UUID, ArchiveWorkEnqueued>>(emptyMap())

    private val pendingArchiveWorkInfos: StateFlow<List<WorkInfo>> =
        workManagerProvider.getPendingArchiveWorkInfosLiveData()
            .asFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    /** File list ui state combines the other fields and generate a new state whenever any of them changes */
    val fileListUiState: StateFlow<FileListUiState> =
        combine(
            currentFolderDisplayed,
            fileListOption,
            searchFilter,
            sortTypeAndOrder,
            space,
        ) { currentFolderDisplayed, fileListOption, searchFilter, sortTypeAndOrder, space ->
            composeFileListUiStateForThisParams(
                currentFolderDisplayed = currentFolderDisplayed,
                fileListOption = fileListOption,
                searchFilter = searchFilter,
                sortTypeAndOrder = sortTypeAndOrder,
                space = space,
            )
        }
            .flatMapLatest { it }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = FileListUiState.Loading
            )

    val composeUiState: StateFlow<MainFileListComposeUiState> = combine(
        fileListUiState,
        selectedIds,
        layoutMode,
        gridColumns,
        isRefreshing,
    ) { uiState, selected, mode, columns, refreshing ->
        ComposeUiInputs(uiState, selected, mode, columns, refreshing)
    }.combine(isMultiPersonal) { inputs, multiPersonal ->
        toComposeUiState(
            fileListUiState = inputs.uiState,
            selectedIds = inputs.selectedIds,
            layoutMode = inputs.layoutMode,
            gridColumns = inputs.gridColumns,
            isRefreshing = inputs.isRefreshing,
            isMultiPersonal = multiPersonal,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainFileListComposeUiState(
            layoutMode = layoutMode.value,
            pullToRefreshEnabled = fileListOptionParam != FileListOption.AV_OFFLINE,
        ),
    )

    fun onArchiveWorkEnqueued(enqueued: ArchiveWorkEnqueued) {
        _archiveWorkMetadata.update { it + (enqueued.workId to enqueued) }
    }

    init {
        val sortTypeSelected = SortType.values()[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.values()[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }
        updateSpace()
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            synchronizeFolderUseCase(
                SynchronizeFolderUseCase.Params(
                    remotePath = initialFolderToDisplay.remotePath,
                    accountName = initialFolderToDisplay.owner,
                    spaceId = initialFolderToDisplay.spaceId,
                    syncMode = SYNC_CONTENTS,
                )
            )
        }
        startPeriodicalFoldersUpdate(accountName = initialFolderToDisplay.owner)

        viewModelScope.launch {
            var previousContent: List<OCFileWithSyncInfo> = emptyList()
            fileListUiState.collect { state ->
                if (state !is FileListUiState.Success) return@collect
                val newContent = state.folderContent
                if (isOnlySortOrderChanged(previousContent, newContent)) {
                    _scrollToTopEvents.tryEmit(Unit)
                }
                previousContent = newContent
                val currentIds = newContent.mapNotNull { it.file.id }.toSet()
                selectedIds.update { it.intersect(currentIds) }
            }
        }
    }

    fun navigateToFolderId(folderId: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = getFileByIdUseCase(GetFileByIdUseCase.Params(fileId = folderId))
            result.getDataOrNull()?.let {
                updateFolderToDisplay(it)
            }
        }
    }

    fun getFile(): OCFile =
        currentFolderDisplayed.value

    fun getSpace(): OCSpace? =
        space.value

    fun setGridModeAsPreferred() {
        savePreferredLayoutManager(true)
        layoutMode.value = FileListLayoutMode.Grid
    }

    fun setListModeAsPreferred() {
        savePreferredLayoutManager(false)
        layoutMode.value = FileListLayoutMode.List
    }

    fun updateGridColumns(columns: Int) {
        gridColumns.value = columns.coerceAtLeast(1)
    }

    fun setMultiPersonal(value: Boolean) {
        isMultiPersonal.value = value
    }

    fun setRefreshing(refreshing: Boolean) {
        isRefreshing.value = refreshing
    }

    fun toggleSelection(fileId: Long) {
        selectedIds.update { current ->
            if (fileId in current) current - fileId else current + fileId
        }
    }

    fun select(fileId: Long) {
        selectedIds.update { it + fileId }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun selectAll() {
        val ids = (fileListUiState.value as? FileListUiState.Success)
            ?.folderContent
            ?.mapNotNull { it.file.id }
            .orEmpty()
        selectedIds.value = ids.toSet()
    }

    fun selectInverse() {
        val ids = (fileListUiState.value as? FileListUiState.Success)
            ?.folderContent
            ?.mapNotNull { it.file.id }
            .orEmpty()
        val current = selectedIds.value
        selectedIds.value = ids.filterTo(mutableSetOf()) { it !in current }
    }

    private fun savePreferredLayoutManager(isGridModeSelected: Boolean) {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, isGridModeSelected)
    }

    fun isGridModeSetAsPreferred() = sharedPreferencesProvider.getBoolean(RECYCLER_VIEW_PREFERRED, false)

    private fun toComposeUiState(
        fileListUiState: FileListUiState,
        selectedIds: Set<Long>,
        layoutMode: FileListLayoutMode,
        gridColumns: Int,
        isRefreshing: Boolean,
        isMultiPersonal: Boolean,
    ): MainFileListComposeUiState {
        val option = when (fileListUiState) {
            is FileListUiState.Success -> fileListUiState.fileListOption
            else -> fileListOption.value
        }
        val pullEnabled = option != FileListOption.AV_OFFLINE
        if (fileListUiState !is FileListUiState.Success) {
            return MainFileListComposeUiState(
                content = FileListContent.Loading,
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
                isRefreshing = isRefreshing,
                pullToRefreshEnabled = pullEnabled,
            )
        }

        val folderContent = fileListUiState.folderContent
        val items = folderContent.map { info ->
            val showSpacePath = option.isAvailableOffline() ||
                option.isFavorites() ||
                (option.isSharedByLink() && info.space == null)
            info.toFileListItemUiModel(
                showThreeDotMenu = !option.isFavorites(),
                showSpacePath = showSpacePath,
                isMultiPersonal = isMultiPersonal,
            )
        }
        val content = if (folderContent.isEmpty()) {
            FileListContent.Empty(
                model = option.toFileListEmptyUiModel(
                    isSharesSpace = option.isSharedByLink() && fileListUiState.space != null,
                ),
            )
        } else {
            FileListContent.Items(
                items = items,
                footerText = if (isPickerMode) null else FileListFooterText.fromFiles(
                    contextProvider.getContext(),
                    folderContent,
                ),
            )
        }

        return MainFileListComposeUiState(
            folderContent = folderContent,
            content = content,
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            isRefreshing = isRefreshing,
            pullToRefreshEnabled = pullEnabled,
        )
    }

    private fun isOnlySortOrderChanged(
        oldList: List<OCFileWithSyncInfo>,
        newList: List<OCFileWithSyncInfo>,
    ): Boolean {
        if (oldList.size != newList.size) return false
        if (oldList === newList || oldList == newList) return false
        // Full-item equality (same as FileListDiffCallback): progress-only updates must not scroll.
        val oldFreq = oldList.groupingBy { it }.eachCount()
        val newFreq = newList.groupingBy { it }.eachCount()
        return oldFreq == newFreq
    }

    private fun sortList(filesWithSyncInfo: List<OCFileWithSyncInfo>, sortTypeAndOrder: Pair<SortType, SortOrder>): List<OCFileWithSyncInfo> =
        sortFilesWithSyncInfoUseCase(
            SortFilesWithSyncInfoUseCase.Params(
                listOfFiles = filesWithSyncInfo,
                sortType = SortTypeDomain.fromPreferences(sortTypeAndOrder.first.ordinal),
                ascending = sortTypeAndOrder.second == SortOrder.SORT_ORDER_ASCENDING
            )
        )

    fun manageBrowseUp() {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val currentFolder = currentFolderDisplayed.value
            val parentId = currentFolder.parentId
            val parentDir: OCFile?

            // browsing back to not shared by link or av offline should update to root
            if (parentId != null && parentId != ROOT_PARENT_ID) {
                // Browsing to parent folder. Not root
                val fileByIdResult = getFileByIdUseCase(GetFileByIdUseCase.Params(parentId))
                when (fileListOption.value) {
                    FileListOption.ALL_FILES -> {
                        parentDir = fileByIdResult.getDataOrNull()
                    }

                    FileListOption.SHARED_BY_LINK -> {
                        val fileById = fileByIdResult.getDataOrNull()
                        parentDir =
                            if (fileById != null && (!fileById.sharedByLink || fileById.sharedWithSharee != true) && fileById.spaceId == null) {
                                getFileByRemotePathUseCase(Params(fileById.owner, ROOT_PATH)).getDataOrNull()
                            } else {
                                fileById
                            }
                    }

                    FileListOption.AV_OFFLINE -> {
                        val fileById = fileByIdResult.getDataOrNull()
                        parentDir = if (fileById != null && (!fileById.isAvailableOffline)) {
                            getFileByRemotePathUseCase(Params(fileById.owner, ROOT_PATH)).getDataOrNull()
                        } else {
                            fileById
                        }
                    }

                    FileListOption.SPACES_LIST -> {
                        parentDir = TODO("Move it to usecase if possible")
                    }

                    FileListOption.UPLOADS_LIST -> {
                        parentDir = null
                        // do nothing
                    }

                    FileListOption.GLOBAL_SEARCH -> {
                        parentDir = null
                    }

                    FileListOption.FAVORITES -> {
                        parentDir = null
                    }

                    FileListOption.TAG_FILES -> {
                        parentDir = null
                    }

                    FileListOption.TRASH -> {
                        parentDir = null
                    }
                }
            } else if (parentId == ROOT_PARENT_ID) {
                // Browsing to parent folder. Root
                val rootFolderForAccountResult = getFileByRemotePathUseCase(
                    GetFileByRemotePathUseCase.Params(
                        remotePath = ROOT_PATH,
                        owner = currentFolder.owner,
                    )
                )
                parentDir = rootFolderForAccountResult.getDataOrNull()
            } else {
                // Browsing to non existing parent folder.
                TODO()
            }

            parentDir?.let { updateFolderToDisplay(it) }
        }
    }

    fun updateFolderToDisplay(newFolderToDisplay: OCFile) {
        currentFolderDisplayed.update { newFolderToDisplay }
        searchFilter.update { "" }
        updateSpace()
    }

    fun updateSearchFilter(newSearchFilter: String) {
        searchFilter.update { newSearchFilter }
    }

    fun updateFileListOption(newFileListOption: FileListOption) {
        fileListOption.update { newFileListOption }
    }

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }
    }

    fun openInWeb(fileId: String, appName: String) {
        runUseCaseWithResult(
            coroutineDispatcher = coroutinesDispatcherProvider.io,
            flow = _openInWebFlow,
            useCase = getUrlToOpenInWebUseCase,
            useCaseParams = GetUrlToOpenInWebUseCase.Params(
                fileId = fileId,
                accountName = getFile().owner,
                appName = appName,
            ),
            showLoading = false,
            requiresConnection = true,
        )
    }

    fun resetOpenInWebFlow() {
        _openInWebFlow.value = null
    }

    fun filterMenuOptions(
        files: List<OCFile>, filesSyncInfo: List<OCFileSyncInfo>,
        displaySelectAll: Boolean, isMultiselection: Boolean
    ) {
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = files,
                    filesSyncInfo = filesSyncInfo,
                    accountName = currentFolderDisplayed.value.owner,
                    currentFolder = currentFolderDisplayed.value,
                    isAnyFileVideoPreviewing = false,
                    displaySelectAll = displaySelectAll,
                    displaySelectInverse = isMultiselection,
                    onlyAvailableOfflineFiles = fileListOption.value.isAvailableOffline(),
                    onlySharedByLinkFiles = fileListOption.value.isSharedByLink(),
                    shareViaLinkAllowed = shareViaLinkAllowed,
                    shareWithUsersAllowed = shareWithUsersAllowed,
                    sendAllowed = sendAllowed,
                )
            )
            if (isMultiselection) {
                _menuOptions.emit(result)
            } else {
                _menuOptionsSingleFile.emit(result)
            }
        }
    }

    fun getAppRegistryForMimeType(mimeType: String, isMultiselection: Boolean) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = getAppRegistryForMimeTypeAsStreamUseCase(
                GetAppRegistryForMimeTypeAsStreamUseCase.Params(accountName = getFile().owner, mimeType)
            )
            if (isMultiselection) {
                _appRegistryMimeType.emit(result.firstOrNull())
            } else {
                _appRegistryMimeTypeSingleFile.emit(result.firstOrNull())
            }
        }
    }

    private fun updateSpace() {
        val folderToDisplay = currentFolderDisplayed.value
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            if (folderToDisplay.remotePath == ROOT_PATH) {
                val currentSpace = getSpaceWithSpecialsByIdForAccountUseCase(
                    GetSpaceWithSpecialsByIdForAccountUseCase.Params(
                        spaceId = folderToDisplay.spaceId,
                        accountName = folderToDisplay.owner,
                    )
                )
                space.update { currentSpace }
            }
        }

    }

    private fun startPeriodicalFoldersUpdate(accountName: String) {
        // TODO: move to background job worker
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            delay(10.seconds) // initial delay to not interfere with the current folder refresh
            while (isActive) {
                updateFoldersRecursivelyUseCase(params = UpdateFoldersRecursivelyUseCase.Params(accountName = accountName))
                delay(5.minutes) // delay between updates
            }
        }
    }

    private fun composeFileListUiStateForThisParams(
        currentFolderDisplayed: OCFile,
        fileListOption: FileListOption,
        searchFilter: String?,
        sortTypeAndOrder: Pair<SortType, SortOrder>,
        space: OCSpace?,
    ): Flow<FileListUiState> =
        when (fileListOption) {
            FileListOption.ALL_FILES -> retrieveFlowForAllFiles(currentFolderDisplayed, currentFolderDisplayed.owner)
            FileListOption.SHARED_BY_LINK -> retrieveFlowForShareByLink(currentFolderDisplayed, currentFolderDisplayed.owner)
            FileListOption.AV_OFFLINE -> retrieveFlowForAvailableOffline(currentFolderDisplayed, currentFolderDisplayed.owner)
            FileListOption.SPACES_LIST -> flowOf()
            FileListOption.UPLOADS_LIST -> flowOf()
            FileListOption.GLOBAL_SEARCH -> flowOf()
            FileListOption.FAVORITES -> flowOf()
            FileListOption.TAG_FILES -> flowOf()
            FileListOption.TRASH -> flowOf()
        }.toFileListUiState(
            currentFolderDisplayed,
            fileListOption,
            searchFilter,
            sortTypeAndOrder,
            space,
        )

    private fun retrieveFlowForAllFiles(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        getFolderContentAsStreamUseCase(
            GetFolderContentAsStreamUseCase.Params(
                folderId = currentFolderDisplayed.id
                    ?: getFileByRemotePathUseCase(GetFileByRemotePathUseCase.Params(accountName, ROOT_PATH)).getDataOrNull()!!.id!!
            )
        )

    /**
     * In root folder, all the shared by link files should be shown. Otherwise, the folder content should be shown.
     * Logic to handle the browse back in [manageBrowseUp]
     */
    private fun retrieveFlowForShareByLink(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        if (currentFolderDisplayed.remotePath == ROOT_PATH && currentFolderDisplayed.spaceId == null) {
            getSharedByLinkForAccountAsStreamUseCase(GetSharedByLinkForAccountAsStreamUseCase.Params(accountName))
        } else {
            retrieveFlowForAllFiles(currentFolderDisplayed, accountName)
        }

    /**
     * In root folder, all the available offline files should be shown. Otherwise, the folder content should be shown.
     * Logic to handle the browse back in [manageBrowseUp]
     */
    private fun retrieveFlowForAvailableOffline(
        currentFolderDisplayed: OCFile,
        accountName: String,
    ): Flow<List<OCFileWithSyncInfo>> =
        if (currentFolderDisplayed.remotePath == ROOT_PATH) {
            getFilesAvailableOfflineFromAccountAsStreamUseCase(GetFilesAvailableOfflineFromAccountAsStreamUseCase.Params(accountName))
        } else {
            retrieveFlowForAllFiles(currentFolderDisplayed, accountName)
        }

    private fun Flow<List<OCFileWithSyncInfo>>.toFileListUiState(
        currentFolderDisplayed: OCFile,
        fileListOption: FileListOption,
        searchFilter: String?,
        sortTypeAndOrder: Pair<SortType, SortOrder>,
        space: OCSpace?,
    ) = combine(
        this,
        uploadProgressByTransferId,
        pendingArchiveWorkInfos,
        _archiveWorkMetadata,
    ) { folderContent, progressByTransferId, pendingWorks, workMetadata ->
        val activeMetadata = workMetadata.filterKeys { workId ->
            pendingWorks.any { it.id == workId }
        }
        folderContent
            .map { fileWithSyncInfo ->
                fileWithSyncInfo.withUploadProgress(progressByTransferId)
            }
            .withArchiveVirtualFiles(
                currentFolder = currentFolderDisplayed,
                pendingWorks = pendingWorks,
                workMetadata = activeMetadata,
            )
    }.map { folderContentWithProgress ->
        FileListUiState.Success(
            folderToDisplay = currentFolderDisplayed,
            folderContent = folderContentWithProgress.filter { fileWithSyncInfo ->
                fileWithSyncInfo.file.fileName.contains(
                    searchFilter ?: "",
                    ignoreCase = true
                ) && (showHiddenFiles || !fileWithSyncInfo.file.fileName.startsWith("."))
            }.let { sortList(it, sortTypeAndOrder) },
            fileListOption = fileListOption,
            searchFilter = searchFilter,
            space = space,
        )
    }

    private fun OCFileWithSyncInfo.withUploadProgress(progressByTransferId: Map<Long, Int>): OCFileWithSyncInfo {
        val transferId = file.uploadTransferId() ?: return this
        return copy(uploadProgress = progressByTransferId[transferId] ?: uploadProgress)
    }

    private fun WorkInfo.extractTransferIdFromTags(): Long? =
        tags.firstNotNullOfOrNull { tag -> tag.toLongOrNull() }

    sealed interface FileListUiState {
        object Loading : FileListUiState
        data class Success(
            val folderToDisplay: OCFile?,
            val folderContent: List<OCFileWithSyncInfo>,
            val fileListOption: FileListOption,
            val searchFilter: String?,
            val space: OCSpace?,
        ) : FileListUiState
    }

    private data class ComposeUiInputs(
        val uiState: FileListUiState,
        val selectedIds: Set<Long>,
        val layoutMode: FileListLayoutMode,
        val gridColumns: Int,
        val isRefreshing: Boolean,
    )

    companion object {
        internal const val RECYCLER_VIEW_PREFERRED = "RECYCLER_VIEW_PREFERRED"
    }
}

