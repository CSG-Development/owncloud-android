package com.owncloud.android.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.data.providers.SharedPreferencesProvider
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.device.DeviceConnectionMonitor
import com.owncloud.android.domain.exceptions.NoConnectionWithServerException
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.exceptions.ServerConnectionTimeoutException
import com.owncloud.android.domain.exceptions.ServerNotReachableException
import com.owncloud.android.domain.exceptions.ServerResponseTimeoutException
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.domain.trash.usecases.DeleteTrashItemUseCase
import com.owncloud.android.domain.trash.usecases.IsTrashEnabledUseCase
import com.owncloud.android.domain.trash.usecases.ListTrashUseCase
import com.owncloud.android.domain.trash.usecases.RestoreTrashItemUseCase
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.common.UIResult
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.MainFileListViewModel.Companion.RECYCLER_VIEW_PREFERRED
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class TrashViewModel(
    private val listTrashUseCase: ListTrashUseCase,
    private val deleteTrashItemUseCase: DeleteTrashItemUseCase,
    private val restoreTrashItemUseCase: RestoreTrashItemUseCase,
    private val isTrashEnabledUseCase: IsTrashEnabledUseCase,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val appContextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    private val deviceConnectionMonitor: DeviceConnectionMonitor,
) : ViewModel() {

    private val _trashUiState = MutableStateFlow<TrashUiState>(TrashUiState.Loading)
    val trashUiState: StateFlow<TrashUiState> = _trashUiState

    private val _deleteOperation = MutableSharedFlow<UIResult<Int>>()
    val deleteOperation: SharedFlow<UIResult<Int>> = _deleteOperation.asSharedFlow()

    private val _restoreOperation = MutableSharedFlow<UIResult<Int>>()
    val restoreOperation: SharedFlow<UIResult<Int>> = _restoreOperation.asSharedFlow()

    private val networkErrorHandler: (Throwable) -> Unit = { throwable ->
        when (throwable) {
            is NoNetworkConnectionException -> deviceConnectionMonitor.reportNoNetwork()
            is ServerResponseTimeoutException,
            is ServerConnectionTimeoutException,
            is NoConnectionWithServerException,
            is ServerNotReachableException,
            -> deviceConnectionMonitor.reportUnreachable()
        }
    }

    val itemCount: Int
        get() = when (val state = _trashUiState.value) {
            is TrashUiState.Success -> state.items.size
            else -> 0
        }

    val selectedCount: Int
        get() = when (val state = _trashUiState.value) {
            is TrashUiState.Success -> state.items.count { it.isSelected }
            else -> 0
        }

    init {
        loadTrash()
    }

    fun loadTrash() {
        val currentAccount = AccountUtils.getCurrentOwnCloudAccount(appContextProvider.getContext())
        val accountName = currentAccount?.name ?: return
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val selectedFileIds = getSelectedFileIds()
            updateTrashUiState(TrashUiState.Loading)

            val isEnabled = isTrashEnabledUseCase(IsTrashEnabledUseCase.Params(accountName))
            if (isEnabled is UseCaseResult.Error || isEnabled.getDataOrNull() != true) {
                updateTrashUiState(TrashUiState.NotSupported)
                return@launch
            }

            when (val result = listTrashUseCase(ListTrashUseCase.Params(accountName))) {
                is UseCaseResult.Success -> {
                    val sortedItems = result.data
                        .sortedByDescending { it.deletedTimestamp ?: 0L }
                        .map { it.toTrashItemUi(isSelected = it.fileId in selectedFileIds) }
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
        updateSuccessItems { items ->
            items.mapIndexed { index, item ->
                if (index == position) item.copy(isSelected = !item.isSelected) else item
            }
        }
    }

    fun clearSelection() {
        updateSuccessItems { items ->
            items.map { it.copy(isSelected = false) }
        }
    }

    fun toggleSelectAll() {
        updateSuccessItems { items ->
            val selectAll = items.any { !it.isSelected }
            items.map { it.copy(isSelected = selectAll) }
        }
    }

    fun hasSelection(): Boolean = selectedCount > 0

    fun getSelectedItems(): List<HCTrashItem> =
        when (val state = _trashUiState.value) {
            is TrashUiState.Success -> state.items.filter { it.isSelected }.map { it.item }
            else -> emptyList()
        }

    fun deleteSelectedItems() {
        val itemsToDelete = getSelectedItems()
        if (itemsToDelete.isEmpty()) return

        val accountName = AccountUtils.getCurrentOwnCloudAccount(appContextProvider.getContext())?.name ?: return

        clearSelection()
        runTrashOperation(
            operationFlow = _deleteOperation,
            itemCount = itemsToDelete.size,
        ) {
            for (item in itemsToDelete) {
                when (val result = deleteTrashItemUseCase(DeleteTrashItemUseCase.Params(accountName, item.fileId))) {
                    is UseCaseResult.Success -> Unit
                    is UseCaseResult.Error -> return@runTrashOperation result.throwable
                }
            }
            null
        }
    }

    fun restoreSelectedItems() {
        val itemsToRestore = getSelectedItems()
        if (itemsToRestore.isEmpty()) return

        val accountName = AccountUtils.getCurrentOwnCloudAccount(appContextProvider.getContext())?.name ?: return

        clearSelection()
        runTrashOperation(
            operationFlow = _restoreOperation,
            itemCount = itemsToRestore.size,
        ) {
            for (item in itemsToRestore) {
                when (
                    val result = restoreTrashItemUseCase(
                        RestoreTrashItemUseCase.Params(
                            accountName = accountName,
                            fileId = item.fileId,
                            originalLocation = item.originalLocation,
                        ),
                    )
                ) {
                    is UseCaseResult.Success -> Unit
                    is UseCaseResult.Error -> return@runTrashOperation result.throwable
                }
            }
            null
        }
    }

    private fun runTrashOperation(
        operationFlow: MutableSharedFlow<UIResult<Int>>,
        itemCount: Int,
        block: suspend () -> Throwable?,
    ) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            operationFlow.emit(UIResult.Loading())

            if (!appContextProvider.isConnected()) {
                deviceConnectionMonitor.reportNoNetwork()
                operationFlow.emit(UIResult.Error(error = NoNetworkConnectionException()))
                Timber.w("Trash operation will not be executed due to lack of network connection")
                return@launch
            }

            val error = block()
            if (error != null) {
                networkErrorHandler(error)
                operationFlow.emit(UIResult.Error(error = error))
                return@launch
            }

            operationFlow.emit(UIResult.Success(itemCount))
            loadTrash()
        }
    }

    private fun getSelectedFileIds(): Set<String> =
        when (val state = _trashUiState.value) {
            is TrashUiState.Success -> state.items.filter { it.isSelected }.map { it.item.fileId }.toSet()
            else -> emptySet()
        }

    private fun updateTrashUiState(newState: TrashUiState) {
        _trashUiState.update { newState }
    }

    private inline fun updateSuccessItems(transform: (List<TrashItemUi>) -> List<TrashItemUi>) {
        _trashUiState.update { state ->
            when (state) {
                is TrashUiState.Success -> TrashUiState.Success(transform(state.items))
                else -> state
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
        data class Success(val items: List<TrashItemUi>) : TrashUiState()
        data object Empty : TrashUiState()
        data object NotSupported : TrashUiState()
        data class Error(val message: String) : TrashUiState()
    }
}
