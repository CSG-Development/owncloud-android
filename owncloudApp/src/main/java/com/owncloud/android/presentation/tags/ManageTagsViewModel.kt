package com.owncloud.android.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.domain.tags.usecases.AssignTagToFileUseCase
import com.owncloud.android.domain.tags.usecases.CreateTagUseCase
import com.owncloud.android.domain.tags.usecases.RefreshTagsForAccountUseCase
import com.owncloud.android.domain.tags.usecases.RefreshTagsForFileUseCase
import com.owncloud.android.domain.tags.usecases.RemoveTagFromFileUseCase
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageTagsViewModel(
    private val removeTagFromFileUseCase: RemoveTagFromFileUseCase,
    private val refreshTagsForFileUseCase: RefreshTagsForFileUseCase,
    private val refreshTagsForAccountUseCase: RefreshTagsForAccountUseCase,
    private val assignTagToFileUseCase: AssignTagToFileUseCase,
    private val createTagUseCase: CreateTagUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManageTagsUiState>(ManageTagsUiState.Loading)
    val uiState: StateFlow<ManageTagsUiState> = _uiState

    private val _allTagsUiState = MutableStateFlow<AllTagsUiState>(AllTagsUiState.Loading)
    val allTagsUiState: StateFlow<AllTagsUiState> = _allTagsUiState

    fun loadTagsForFile(accountName: String, fileRemoteId: Long, fileLocalId: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _uiState.update { ManageTagsUiState.Loading }
            val result = refreshTagsForFileUseCase(RefreshTagsForFileUseCase.Params(accountName, fileRemoteId, fileLocalId))
            _uiState.update {
                when (result) {
                    is UseCaseResult.Success -> ManageTagsUiState.Success(result.data)
                    is UseCaseResult.Error -> ManageTagsUiState.Error(result.throwable)
                }
            }
        }
    }

    fun removeTagFromFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            removeTagFromFileUseCase(
                RemoveTagFromFileUseCase.Params(
                    accountName = accountName,
                    fileLocalId = fileLocalId,
                    fileRemoteId = fileRemoteId,
                    tagId = tagId,
                )
            )
            loadTagsForFile(accountName, fileRemoteId, fileLocalId)
        }
    }

    fun loadAllTagsForAccount(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = refreshTagsForAccountUseCase(RefreshTagsForAccountUseCase.Params(accountName))
            _allTagsUiState.update {
                when (result) {
                    is UseCaseResult.Success -> AllTagsUiState.Success(result.data.filter { it.userAssignable })
                    is UseCaseResult.Error -> AllTagsUiState.Error(result.throwable)
                }
            }
        }
    }

    fun assignTagToFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            assignTagToFileUseCase(AssignTagToFileUseCase.Params(accountName = accountName, fileLocalId = fileLocalId, fileRemoteId = fileRemoteId, tagId = tagId))
            loadTagsForFile(accountName, fileRemoteId, fileLocalId)
            loadAllTagsForAccount(accountName)
        }
    }

    fun createTagAndAssignToFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val createResult = createTagUseCase(CreateTagUseCase.Params(accountName = accountName, name = tagName))
            if (createResult is UseCaseResult.Error) return@launch
            val refreshResult = refreshTagsForAccountUseCase(RefreshTagsForAccountUseCase.Params(accountName))
            if (refreshResult is UseCaseResult.Success) {
                val newTag = refreshResult.data.firstOrNull { it.displayName == tagName }
                val tagId = newTag?.id
                if (tagId != null) {
                    assignTagToFileUseCase(AssignTagToFileUseCase.Params(accountName = accountName, fileLocalId = fileLocalId, fileRemoteId = fileRemoteId, tagId = tagId))
                }
                _allTagsUiState.update { AllTagsUiState.Success(refreshResult.data.filter { it.userAssignable }) }
            }
            loadTagsForFile(accountName, fileRemoteId, fileLocalId)
        }
    }

    sealed class ManageTagsUiState {
        data object Loading : ManageTagsUiState()
        data class Success(val tags: List<OCTag>) : ManageTagsUiState()
        data class Error(val throwable: Throwable) : ManageTagsUiState()
    }

    sealed class AllTagsUiState {
        data object Loading : AllTagsUiState()
        data class Success(val tags: List<OCTag>) : AllTagsUiState()
        data class Error(val throwable: Throwable) : AllTagsUiState()
    }
}
