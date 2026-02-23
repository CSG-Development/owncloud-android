package com.owncloud.android.presentation.files.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.usecases.GetFavoriteFilesForAccountAsStreamUseCase
import com.owncloud.android.domain.files.usecases.SetFileFavoriteStatusUseCase
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

    private val _menuOptions: MutableSharedFlow<List<FileMenuOption>> = MutableSharedFlow()
    val menuOptions: SharedFlow<List<FileMenuOption>> = _menuOptions

    init {
        val sortTypeSelected = SortType.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_TYPE, SortType.SORT_TYPE_BY_NAME.ordinal)]
        val sortOrderSelected =
            SortOrder.entries[sharedPreferencesProvider.getInt(PREF_FILE_LIST_SORT_ORDER, SortOrder.SORT_ORDER_ASCENDING.ordinal)]
        sortTypeAndOrder.update { Pair(sortTypeSelected, sortOrderSelected) }
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

    sealed class FavoritesUiState {
        data object Loading : FavoritesUiState()
        data class Success(val results: List<OCFileWithSyncInfo>) : FavoritesUiState()
        data object Empty : FavoritesUiState()
    }
}
