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
import com.owncloud.android.domain.files.model.isVirtualFile
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
import com.owncloud.android.presentation.common.compose.HomeCloudBannerStyle
import com.owncloud.android.presentation.common.compose.HomeCloudBannerUiModel
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortOrder.Companion.PREF_FILE_LIST_SORT_ORDER
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.SortType.Companion.PREF_FILE_LIST_SORT_TYPE
import com.owncloud.android.presentation.files.ViewType.Companion.PREF_FILE_LIST_GRID
import com.owncloud.android.presentation.files.filelist.compose.ArchiveActivityUiModel
import com.owncloud.android.presentation.files.filelist.compose.ArchiveActivityUiModelMapper
import com.owncloud.android.presentation.files.filelist.compose.FileListComposeUiState
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.fileListItemsUiState
import com.owncloud.android.presentation.files.filelist.compose.fileListLoadingUiState
import com.owncloud.android.presentation.files.filelist.compose.toFileListEmptyUiModel
import com.owncloud.android.presentation.files.operations.ArchiveErrorUiModel
import com.owncloud.android.presentation.files.operations.ArchiveFailureType
import com.owncloud.android.presentation.files.operations.ArchiveWorkCompleted
import com.owncloud.android.presentation.files.operations.ArchiveWorkFailed
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.messageRes
import com.owncloud.android.presentation.files.operations.showRetry
import com.owncloud.android.presentation.settings.advanced.SettingsAdvancedFragment.Companion.PREF_SHOW_HIDDEN_FILES
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.providers.WorkManagerProvider
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_UNZIP
import com.owncloud.android.usecases.archive.ARCHIVE_TAG_ZIP
import com.owncloud.android.usecases.archive.ArchiveWorkTags
import com.owncloud.android.usecases.archive.KEY_ARCHIVE_FAILURE_TYPE
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase
import com.owncloud.android.usecases.synchronization.SynchronizeFolderUseCase.SyncFolderMode.SYNC_CONTENTS
import com.owncloud.android.usecases.synchronization.UpdateFoldersRecursivelyUseCase
import com.owncloud.android.workers.DownloadFileWorker
import com.owncloud.android.workers.UnzipFileWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val observedArchiveWorkIds = mutableSetOf<UUID>()

    private val pendingArchiveWorkInfos: StateFlow<List<WorkInfo>> =
        workManagerProvider.getPendingArchiveWorkInfosLiveData()
            .asFlow()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _archiveActivity = MutableStateFlow<ArchiveActivityUiModel?>(null)
    val archiveActivity: StateFlow<ArchiveActivityUiModel?> = _archiveActivity.asStateFlow()

    private val _archiveWorkCompleted = MutableSharedFlow<ArchiveWorkCompleted>(extraBufferCapacity = 1)
    val archiveWorkCompleted: SharedFlow<ArchiveWorkCompleted> = _archiveWorkCompleted.asSharedFlow()

    private val _archiveUnsupportedDialog = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val archiveUnsupportedDialog: SharedFlow<Unit> = _archiveUnsupportedDialog.asSharedFlow()

    private val _archiveErrors = MutableStateFlow<List<ArchiveErrorUiModel>>(emptyList())

    val archiveErrorBanner: StateFlow<HomeCloudBannerUiModel?> = _archiveErrors
        .map { errors -> errors.firstOrNull()?.toBannerUiModel() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val _archiveRetryOperations = MutableSharedFlow<FileOperation>(extraBufferCapacity = 1)
    val archiveRetryOperations: SharedFlow<FileOperation> = _archiveRetryOperations.asSharedFlow()

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

    val composeUiState: StateFlow<FileListComposeUiState> = combine(
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
        initialValue = FileListComposeUiState(
            layoutMode = layoutMode.value,
            pullToRefreshEnabled = fileListOptionParam != FileListOption.AV_OFFLINE,
        ),
    )

    fun onArchiveWorkEnqueued(workId: UUID) {
        val accountName = currentFolderDisplayed.value.owner
        if (observedArchiveWorkIds.add(workId)) {
            observeArchiveWorkUntilFinished(workId, accountName)
        }
        refreshArchiveActivity()
    }

    fun cancelArchiveWork(workId: UUID) {
        workManagerProvider.cancelWorkById(workId)
        observedArchiveWorkIds.remove(workId)
        refreshArchiveActivity()
    }

    fun dismissArchiveErrorBanner() {
        _archiveErrors.update { errors -> errors.drop(1) }
    }

    fun retryArchiveError() {
        viewModelScope.launch {
            var head: ArchiveErrorUiModel? = null
            _archiveErrors.update { errors ->
                val current = errors.firstOrNull()
                if (current == null || !current.failure.failureType.showRetry) {
                    errors
                } else {
                    head = current
                    errors.drop(1)
                }
            }
            val failure = head?.failure ?: return@launch
            val operation = resolveRetryOperation(failure) ?: return@launch
            _archiveRetryOperations.emit(operation)
        }
    }

    private suspend fun resolveRetryOperation(failure: ArchiveWorkFailed): FileOperation? =
        withContext(coroutinesDispatcherProvider.io) {
            if (failure.isCompress) {
                val parentFolder = getFileByIdUseCase(GetFileByIdUseCase.Params(failure.parentFolderId))
                    .getDataOrNull()
                    ?.takeIf { it.isFolder }
                    ?: return@withContext null
                val files = failure.sourceFileIds.mapNotNull { fileId ->
                    getFileByIdUseCase(GetFileByIdUseCase.Params(fileId)).getDataOrNull()
                }
                if (files.isEmpty()) return@withContext null
                FileOperation.CompressOperation(
                    accountName = failure.accountName,
                    parentFolder = parentFolder,
                    files = files,
                )
            } else {
                val zipFileId = failure.zipFileId ?: return@withContext null
                val zipFile = getFileByIdUseCase(GetFileByIdUseCase.Params(zipFileId))
                    .getDataOrNull()
                    ?: return@withContext null
                FileOperation.ExtractOperation(
                    accountName = failure.accountName,
                    zipFile = zipFile,
                )
            }
        }

    private fun ArchiveErrorUiModel.toBannerUiModel(): HomeCloudBannerUiModel =
        HomeCloudBannerUiModel(
            messageRes = messageRes,
            style = HomeCloudBannerStyle.ERROR,
            actionLabelRes = R.string.homecloud_retry.takeIf { showRetry },
            contentKey = id,
        )

    private fun trackPendingArchiveWorks(pendingWorks: List<WorkInfo>) {
        val accountName = currentFolderDisplayed.value.owner
        pendingWorks.forEach { workInfo ->
            if (!workInfo.tags.contains(accountName)) return@forEach
            if (!workInfo.tags.contains(ARCHIVE_TAG_ZIP) && !workInfo.tags.contains(ARCHIVE_TAG_UNZIP)) {
                return@forEach
            }
            if (observedArchiveWorkIds.add(workInfo.id)) {
                observeArchiveWorkUntilFinished(workInfo.id, accountName)
            }
        }
    }

    private fun refreshArchiveActivity() {
        _archiveActivity.value = ArchiveActivityUiModelMapper.fromPendingWorks(
            accountName = currentFolderDisplayed.value.owner,
            pendingWorks = pendingArchiveWorkInfos.value,
        )
    }

    private fun observeArchiveWorkUntilFinished(workId: UUID, accountName: String) {
        viewModelScope.launch {
            val workInfo = workManagerProvider.getWorkInfoByIdFlow(workId)
                .filterNotNull()
                .first { it.state.isFinished }

            val stillObserved = observedArchiveWorkIds.contains(workId)
            val isCompress = workInfo.tags.contains(ARCHIVE_TAG_ZIP)
            val isArchiveWork = isCompress || workInfo.tags.contains(ARCHIVE_TAG_UNZIP)
            if (!stillObserved || !isArchiveWork || !workInfo.tags.contains(accountName)) {
                observedArchiveWorkIds.remove(workId)
                refreshArchiveActivity()
                return@launch
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val viewFolderId = resolveArchiveViewFolderId(workInfo, accountName)
                    val itemCount = ArchiveWorkTags.parseItemCount(workInfo.tags)
                        ?: if (isCompress) {
                            ArchiveWorkTags.parseParentFolderId(workInfo.tags)?.let { parentId ->
                                ArchiveWorkTags.parseSourceFileIds(workInfo.tags, parentId).size
                            } ?: 1
                        } else {
                            1
                        }
                    _archiveWorkCompleted.emit(
                        ArchiveWorkCompleted(
                            isCompress = isCompress,
                            itemCount = itemCount,
                            viewFolderId = viewFolderId,
                        ),
                    )
                }
                WorkInfo.State.FAILED -> {
                    val failureType = resolveArchiveFailureType(workInfo)
                    if (failureType == ArchiveFailureType.PASSWORD_PROTECTED) {
                        _archiveUnsupportedDialog.emit(Unit)
                    } else {
                        val failed = resolveArchiveWorkFailed(workInfo, accountName, isCompress, failureType)
                        if (failed != null) {
                            _archiveErrors.update { errors ->
                                errors + ArchiveErrorUiModel(
                                    failure = failed,
                                    messageRes = failed.failureType.messageRes(failed.isCompress),
                                    showRetry = failed.failureType.showRetry,
                                )
                            }
                        }
                    }
                }
                else -> Unit
            }
            observedArchiveWorkIds.remove(workId)
            refreshArchiveActivity()
        }
    }

    private fun resolveArchiveFailureType(workInfo: WorkInfo): ArchiveFailureType {
        val typeName = workInfo.outputData.getString(KEY_ARCHIVE_FAILURE_TYPE) ?: return ArchiveFailureType.UNEXPECTED
        return runCatching { ArchiveFailureType.valueOf(typeName) }.getOrDefault(ArchiveFailureType.UNEXPECTED)
    }

    private suspend fun resolveArchiveWorkFailed(
        workInfo: WorkInfo,
        accountName: String,
        isCompress: Boolean,
        failureType: ArchiveFailureType,
    ): ArchiveWorkFailed? {
        val parentFolderId = ArchiveWorkTags.parseParentFolderId(workInfo.tags) ?: return null
        val displayName = ArchiveWorkTags.parseDisplayName(workInfo.tags).orEmpty()
        val spaceId = withContext(coroutinesDispatcherProvider.io) {
            getFileByIdUseCase(GetFileByIdUseCase.Params(parentFolderId)).getDataOrNull()?.spaceId
        }
        return if (isCompress) {
            ArchiveWorkFailed(
                failureType = failureType,
                isCompress = true,
                displayName = displayName,
                sourceFileIds = ArchiveWorkTags.parseSourceFileIds(workInfo.tags, parentFolderId),
                zipFileId = null,
                parentFolderId = parentFolderId,
                spaceId = spaceId,
                accountName = accountName,
            )
        } else {
            ArchiveWorkFailed(
                failureType = failureType,
                isCompress = false,
                displayName = displayName,
                sourceFileIds = emptyList(),
                zipFileId = ArchiveWorkTags.parseZipFileId(workInfo.tags, parentFolderId),
                parentFolderId = parentFolderId,
                spaceId = spaceId,
                accountName = accountName,
            )
        }
    }

    private suspend fun resolveArchiveViewFolderId(
        workInfo: WorkInfo,
        accountName: String,
    ): Long {
        val parentFolderId = ArchiveWorkTags.parseParentFolderId(workInfo.tags) ?: return currentFolderDisplayed.value.id!!
        if (workInfo.tags.contains(ARCHIVE_TAG_ZIP)) {
            return parentFolderId
        }

        val targetRemotePath = workInfo.outputData.getString(UnzipFileWorker.KEY_TARGET_REMOTE_PATH)
            ?: workInfo.progress.getString(UnzipFileWorker.KEY_TARGET_REMOTE_PATH)
            ?: ArchiveWorkTags.parseRemotePath(workInfo.tags)
            ?: return parentFolderId

        val spaceId = withContext(coroutinesDispatcherProvider.io) {
            getFileByIdUseCase(GetFileByIdUseCase.Params(parentFolderId)).getDataOrNull()?.spaceId
        }

        val targetFile = withContext(coroutinesDispatcherProvider.io) {
            getFileByRemotePathUseCase(
                GetFileByRemotePathUseCase.Params(
                    owner = accountName,
                    remotePath = targetRemotePath,
                    spaceId = spaceId,
                ),
            ).getDataOrNull()
        } ?: return parentFolderId

        return when {
            targetFile.isFolder -> targetFile.id ?: parentFolderId
            else -> targetFile.parentId ?: parentFolderId
        }
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
            pendingArchiveWorkInfos.collect { pendingWorks ->
                trackPendingArchiveWorks(pendingWorks)
                refreshArchiveActivity()
            }
        }
        viewModelScope.launch {
            currentFolderDisplayed
                .map { it.owner }
                .distinctUntilChanged()
                .collect {
                    trackPendingArchiveWorks(pendingArchiveWorkInfos.value)
                    refreshArchiveActivity()
                }
        }

        viewModelScope.launch {
            var previousContent: List<OCFileWithSyncInfo> = emptyList()
            fileListUiState.collect { state ->
                if (state !is FileListUiState.Success) return@collect
                val newContent = state.folderContent
                if (isOnlyListOrderChanged(previousContent, newContent)) {
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

    fun setMultiPersonal(value: Boolean) {
        isMultiPersonal.value = value
    }

    fun setRefreshing(refreshing: Boolean) {
        isRefreshing.value = refreshing
    }

    fun toggleSelection(fileId: Long) {
        selectedIds.toggleFileSelection(fileId)
    }

    fun select(fileId: Long) {
        selectedIds.selectFile(fileId)
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

    fun isGridModeSetAsPreferred() =
        sharedPreferencesProvider.getBoolean(PREF_FILE_LIST_GRID, false)

    private fun toComposeUiState(
        fileListUiState: FileListUiState,
        selectedIds: Set<Long>,
        layoutMode: FileListLayoutMode,
        gridColumns: Int,
        isRefreshing: Boolean,
        isMultiPersonal: Boolean,
    ): FileListComposeUiState {
        val option = when (fileListUiState) {
            is FileListUiState.Success -> fileListUiState.fileListOption
            else -> fileListOption.value
        }
        val pullEnabled = option != FileListOption.AV_OFFLINE
        if (fileListUiState !is FileListUiState.Success) {
            return fileListLoadingUiState(
                layoutMode = layoutMode,
                gridColumns = gridColumns,
                selectedIds = selectedIds,
                isRefreshing = isRefreshing,
                pullToRefreshEnabled = pullEnabled,
            )
        }

        val folderContent = fileListUiState.folderContent
        return fileListItemsUiState(
            folderContent = folderContent,
            emptyModel = option.toFileListEmptyUiModel(
                isSharesSpace = option.isSharedByLink() && fileListUiState.space != null,
            ),
            layoutMode = layoutMode,
            gridColumns = gridColumns,
            selectedIds = selectedIds,
            isRefreshing = isRefreshing,
            pullToRefreshEnabled = pullEnabled,
            showThreeDotMenu = !option.isFavorites(),
            isMultiPersonal = isMultiPersonal,
            showSpacePathForItem = { info ->
                option.isAvailableOffline() ||
                    option.isFavorites() ||
                    (option.isSharedByLink() && info.space == null)
            },
            footerContext = contextProvider.getContext(),
            includeFooter = !isPickerMode,
        )
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
    ) { folderContent, progressByTransferId ->
        folderContent.map { fileWithSyncInfo ->
            fileWithSyncInfo.withUploadProgress(progressByTransferId)
        }
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
}

