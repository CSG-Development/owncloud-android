package com.owncloud.android.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.domain.tags.usecases.AssignTagToFileUseCase
import com.owncloud.android.domain.tags.usecases.CreateTagUseCase
import com.owncloud.android.domain.tags.usecases.GetLocalTagsForFileUseCase
import com.owncloud.android.domain.tags.usecases.RefreshTagsForAccountUseCase
import com.owncloud.android.domain.tags.usecases.RefreshTagsForFileUseCase
import com.owncloud.android.domain.tags.usecases.RemoveTagFromFileUseCase
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManageTagsViewModel(
    private val removeTagFromFileUseCase: RemoveTagFromFileUseCase,
    private val refreshTagsForFileUseCase: RefreshTagsForFileUseCase,
    private val getLocalTagsForFileUseCase: GetLocalTagsForFileUseCase,
    private val refreshTagsForAccountUseCase: RefreshTagsForAccountUseCase,
    private val assignTagToFileUseCase: AssignTagToFileUseCase,
    private val createTagUseCase: CreateTagUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManageTagsUiState>(ManageTagsUiState.Loading)
    val uiState: StateFlow<ManageTagsUiState> = _uiState

    private val _allTagsUiState = MutableStateFlow<AllTagsUiState>(AllTagsUiState.Loading)
    val allTagsUiState: StateFlow<AllTagsUiState> = _allTagsUiState

    private val _errorEvent = MutableSharedFlow<Throwable>()
    val errorEvent: SharedFlow<Throwable> = _errorEvent

    fun loadTagsForFile(accountName: String, fileRemoteId: Long, fileLocalId: Long) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _uiState.update { ManageTagsUiState.Loading }
            val result = refreshTagsForFileUseCase(
                RefreshTagsForFileUseCase.Params(accountName, fileRemoteId, fileLocalId)
            )
            when (result) {
                is UseCaseResult.Success -> {
                    _uiState.update { ManageTagsUiState.Success(result.data) }
                }
                is UseCaseResult.Error -> {
                    _errorEvent.emit(result.throwable)
                    val localResult = getLocalTagsForFileUseCase(
                        GetLocalTagsForFileUseCase.Params(fileLocalId)
                    )
                    _uiState.update {
                        when (localResult) {
                            is UseCaseResult.Success -> ManageTagsUiState.Success(localResult.data)
                            is UseCaseResult.Error -> ManageTagsUiState.Error(result.throwable)
                        }
                    }
                }
            }
        }
    }

    fun removeTagFromFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _uiState.update { current ->
                if (current is ManageTagsUiState.Success)
                    current.copy(pendingTagIds = current.pendingTagIds + tagId)
                else current
            }
            val result = removeTagFromFileUseCase(
                RemoveTagFromFileUseCase.Params(
                    accountName = accountName,
                    fileLocalId = fileLocalId,
                    fileRemoteId = fileRemoteId,
                    tagId = tagId,
                )
            )
            when (result) {
                is UseCaseResult.Success -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success) {
                            current.copy(
                                tags = current.tags.filter { it.id != tagId },
                                pendingTagIds = current.pendingTagIds - tagId,
                            )
                        } else current
                    }
                }
                is UseCaseResult.Error -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success)
                            current.copy(pendingTagIds = current.pendingTagIds - tagId)
                        else current
                    }
                    _errorEvent.emit(result.throwable)
                }
            }
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
            val tagToAssign = (_allTagsUiState.value as? AllTagsUiState.Success)
                ?.tags?.firstOrNull { it.id == tagId }

            if (tagToAssign != null) {
                _uiState.update { current ->
                    val currentTags = (current as? ManageTagsUiState.Success)?.tags ?: emptyList()
                    val currentPending = (current as? ManageTagsUiState.Success)?.pendingTagIds ?: emptySet()
                    ManageTagsUiState.Success(
                        tags = currentTags + tagToAssign,
                        pendingTagIds = currentPending + tagId,
                    )
                }
            }

            val result = assignTagToFileUseCase(
                AssignTagToFileUseCase.Params(
                    accountName = accountName,
                    fileLocalId = fileLocalId,
                    fileRemoteId = fileRemoteId,
                    tagId = tagId
                )
            )
            when (result) {
                is UseCaseResult.Success -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success)
                            current.copy(pendingTagIds = current.pendingTagIds - tagId)
                        else current
                    }
                }
                is UseCaseResult.Error -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success)
                            current.copy(
                                tags = current.tags.filter { it.id != tagId },
                                pendingTagIds = current.pendingTagIds - tagId,
                            )
                        else current
                    }
                    _errorEvent.emit(result.throwable)
                }
            }
        }
    }

    fun createTagAndAssignToFile(accountName: String, fileLocalId: Long, fileRemoteId: Long, tagName: String) {
        val tempId = "pending_$tagName"
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val tempTag = OCTag(
                id = tempId,
                displayName = tagName,
                userVisible = true,
                userAssignable = true,
            )
            _uiState.update { current ->
                val currentTags = (current as? ManageTagsUiState.Success)?.tags ?: emptyList()
                val currentPending = (current as? ManageTagsUiState.Success)?.pendingTagIds ?: emptySet()
                ManageTagsUiState.Success(
                    tags = currentTags + tempTag,
                    pendingTagIds = currentPending + tempId,
                )
            }

            val createResult = createTagUseCase(CreateTagUseCase.Params(accountName = accountName, name = tagName))
            if (createResult is UseCaseResult.Error) {
                _uiState.update { current ->
                    if (current is ManageTagsUiState.Success)
                        current.copy(
                            tags = current.tags.filter { it.id != tempId },
                            pendingTagIds = current.pendingTagIds - tempId,
                        )
                    else current
                }
                _errorEvent.emit(createResult.throwable)
                return@launch
            }

            val refreshResult = refreshTagsForAccountUseCase(RefreshTagsForAccountUseCase.Params(accountName))
            if (refreshResult is UseCaseResult.Error) {
                _uiState.update { current ->
                    if (current is ManageTagsUiState.Success)
                        current.copy(
                            tags = current.tags.filter { it.id != tempId },
                            pendingTagIds = current.pendingTagIds - tempId,
                        )
                    else current
                }
                _errorEvent.emit(refreshResult.throwable)
                return@launch
            }

            val allTags = (refreshResult as UseCaseResult.Success).data
            _allTagsUiState.update { AllTagsUiState.Success(allTags.filter { it.userAssignable }) }

            val newTag = allTags.firstOrNull { it.displayName == tagName }
            val tagId = newTag?.id ?: run {
                _uiState.update { current ->
                    if (current is ManageTagsUiState.Success)
                        current.copy(
                            tags = current.tags.filter { it.id != tempId },
                            pendingTagIds = current.pendingTagIds - tempId,
                        )
                    else current
                }
                return@launch
            }

            val assignResult = assignTagToFileUseCase(
                AssignTagToFileUseCase.Params(
                    accountName = accountName,
                    fileLocalId = fileLocalId,
                    fileRemoteId = fileRemoteId,
                    tagId = tagId
                )
            )
            when (assignResult) {
                is UseCaseResult.Success -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success)
                            current.copy(
                                tags = current.tags.filter { it.id != tempId } + newTag,
                                pendingTagIds = current.pendingTagIds - tempId,
                            )
                        else current
                    }
                }
                is UseCaseResult.Error -> {
                    _uiState.update { current ->
                        if (current is ManageTagsUiState.Success)
                            current.copy(
                                tags = current.tags.filter { it.id != tempId },
                                pendingTagIds = current.pendingTagIds - tempId,
                            )
                        else current
                    }
                    _errorEvent.emit(assignResult.throwable)
                }
            }
        }
    }

    sealed class ManageTagsUiState {
        data object Loading : ManageTagsUiState()
        data class Success(
            val tags: List<OCTag>,
            val pendingTagIds: Set<String> = emptySet(),
        ) : ManageTagsUiState()
        data class Error(val throwable: Throwable) : ManageTagsUiState()
    }

    sealed class AllTagsUiState {
        data object Loading : AllTagsUiState()
        data class Success(val tags: List<OCTag>) : AllTagsUiState()
        data class Error(val throwable: Throwable) : AllTagsUiState()
    }
}
