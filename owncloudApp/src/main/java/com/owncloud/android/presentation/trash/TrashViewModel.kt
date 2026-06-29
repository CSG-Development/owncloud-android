package com.owncloud.android.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.domain.trash.usecases.IsTrashEnabledUseCase
import com.owncloud.android.domain.trash.usecases.ListTrashUseCase
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.MainFileListViewModel.Companion.RECYCLER_VIEW_PREFERRED
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TrashViewModel(
    private val listTrashUseCase: ListTrashUseCase,
    private val isTrashEnabledUseCase: IsTrashEnabledUseCase,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val appContextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _trashUiState = MutableStateFlow<TrashUiState>(TrashUiState.Loading)
    val trashUiState: StateFlow<TrashUiState> = _trashUiState

    private val _selectedPositions = MutableStateFlow<Set<Int>>(emptySet())
    val selectedPositions: StateFlow<Set<Int>> = _selectedPositions

    val itemCount: Int
        get() = when (val state = _trashUiState.value) {
            is TrashUiState.Success -> state.items.size
            else -> 0
        }

    val isAllSelected: Boolean
        get() = itemCount > 0 && _selectedPositions.value.size == itemCount

    init {
        loadTrash()
    }

    fun loadTrash() {
        val currentAccount = AccountUtils.getCurrentOwnCloudAccount(appContextProvider.getContext())
        val accountName = currentAccount?.name ?: return
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            updateTrashUiState(TrashUiState.Loading)

            val isEnabled = isTrashEnabledUseCase(IsTrashEnabledUseCase.Params(accountName))
            if (isEnabled is UseCaseResult.Error || isEnabled.getDataOrNull() != true) {
                updateTrashUiState(TrashUiState.NotSupported)
                return@launch
            }

            when (val result = listTrashUseCase(ListTrashUseCase.Params(accountName))) {
                is UseCaseResult.Success -> {
                    val sortedItems = result.data.sortedByDescending { it.deletedTimestamp ?: 0L }
                    updateTrashUiState(
                        if (sortedItems.isEmpty()) {
                            TrashUiState.Empty
                        } else {
                            TrashUiState.Success(sortedItems)
                        }
                    )
                }
                is UseCaseResult.Error -> {
                    updateTrashUiState(TrashUiState.Error(result.throwable.message ?: ""))
                }
            }
        }
    }

    fun toggleSelection(position: Int) {
        _selectedPositions.update { selected ->
            if (position in selected) {
                selected - position
            } else {
                selected + position
            }
        }
    }

    fun clearSelection() {
        _selectedPositions.update { emptySet() }
    }

    fun toggleSelectAll() {
        val count = itemCount
        if (count == 0) return

        _selectedPositions.update { selected ->
            if (selected.size == count) {
                emptySet()
            } else {
                (0 until count).toSet()
            }
        }
    }

    fun hasSelection(): Boolean = _selectedPositions.value.isNotEmpty()

    fun isSelected(position: Int): Boolean = position in _selectedPositions.value

    private fun updateTrashUiState(newState: TrashUiState) {
        _trashUiState.update { newState }
        _selectedPositions.update { selected ->
            when (newState) {
                is TrashUiState.Success -> selected.filter { it < newState.items.size }.toSet()
                is TrashUiState.Loading -> selected
                else -> emptySet()
            }
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

    fun getCurrentViewType(): ViewType =
        if (isGridModeSetAsPreferred()) ViewType.VIEW_TYPE_GRID else ViewType.VIEW_TYPE_LIST

    sealed class TrashUiState {
        data object Loading : TrashUiState()
        data class Success(val items: List<HCTrashItem>) : TrashUiState()
        data object Empty : TrashUiState()
        data object NotSupported : TrashUiState()
        data class Error(val message: String) : TrashUiState()
    }
}
