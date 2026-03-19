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

class TagFilesViewModel(
    private val refreshFilesByTagUseCase: RefreshFilesByTagUseCase,
    private val sortFilesWithSyncInfoUseCase: SortFilesWithSyncInfoUseCase,
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val contextProvider: ContextProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TagFilesUiState>(TagFilesUiState.Loading)
    val uiState: StateFlow<TagFilesUiState> = _uiState

    private val _menuOptionsSingleFile = MutableSharedFlow<List<FileMenuOption>>()
    val menuOptionsSingleFile: SharedFlow<List<FileMenuOption>> = _menuOptionsSingleFile

    private val sortTypeAndOrder = MutableStateFlow(Pair(SortType.SORT_TYPE_BY_NAME, SortOrder.SORT_ORDER_ASCENDING))

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected = SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }
    }

    fun loadFiles(accountName: String, serverTagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _uiState.update { TagFilesUiState.Loading }

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
                    _uiState.update {
                        if (sorted.isEmpty()) TagFilesUiState.Empty else TagFilesUiState.Success(sorted)
                    }
                }

                is UseCaseResult.Error -> {
                    _uiState.update { TagFilesUiState.Error(result.throwable) }
                }
            }
        }
    }

    fun updateSortTypeAndOrder(sortType: SortType, sortOrder: SortOrder) {
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_TYPE, sortType.ordinal)
        sharedPreferencesProvider.putInt(PREF_FILE_LIST_SORT_ORDER, sortOrder.ordinal)
        sortTypeAndOrder.update { Pair(sortType, sortOrder) }

        val currentState = _uiState.value
        if (currentState is TagFilesUiState.Success && currentState.files.isNotEmpty()) {
            val sorted = sortList(currentState.files, sortTypeAndOrder.value)
            _uiState.update { TagFilesUiState.Success(sorted) }
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
        sharedPreferencesProvider.getBoolean(RECYCLER_VIEW_PREFERRED, false)

    fun setGridModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, true)
    }

    fun setListModeAsPreferred() {
        sharedPreferencesProvider.putBoolean(RECYCLER_VIEW_PREFERRED, false)
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

    sealed class TagFilesUiState {
        data object Loading : TagFilesUiState()
        data class Success(val files: List<OCFileWithSyncInfo>) : TagFilesUiState()
        data object Empty : TagFilesUiState()
        data class Error(val throwable: Throwable) : TagFilesUiState()
    }
}
