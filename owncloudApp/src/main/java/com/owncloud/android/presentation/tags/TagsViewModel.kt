package com.owncloud.android.presentation.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.UseCaseResult
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.domain.tags.usecases.CreateTagUseCase
import com.owncloud.android.domain.tags.usecases.DeleteTagUseCase
import com.owncloud.android.domain.tags.usecases.RefreshTagsForAccountUseCase
import com.owncloud.android.domain.tags.usecases.UpdateTagUseCase
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TagsViewModel(
    private val refreshTagsForAccountUseCase: RefreshTagsForAccountUseCase,
    private val createTagUseCase: CreateTagUseCase,
    private val updateTagUseCase: UpdateTagUseCase,
    private val deleteTagUseCase: DeleteTagUseCase,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
) : ViewModel() {

    private val _tagsUiState = MutableStateFlow<TagsUiState>(TagsUiState.Loading)
    val tagsUiState: StateFlow<TagsUiState> = _tagsUiState

    fun loadTags(accountName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            _tagsUiState.update { TagsUiState.Loading }
            val result = refreshTagsForAccountUseCase(
                RefreshTagsForAccountUseCase.Params(accountName = accountName)
            )
            when (result) {
                is UseCaseResult.Success -> {
                    val visibleTags = result.data.filter { it.userVisible }
                    _tagsUiState.update {
                        if (visibleTags.isEmpty()) {
                            TagsUiState.Empty
                        } else {
                            TagsUiState.Success(visibleTags)
                        }
                    }
                }
                is UseCaseResult.Error -> {
                    _tagsUiState.update { TagsUiState.Error(result.throwable) }
                }
            }
        }
    }

    fun createTag(accountName: String, name: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = createTagUseCase(CreateTagUseCase.Params(accountName = accountName, name = name))
            if (result is UseCaseResult.Success) {
                loadTags(accountName)
            }
        }
    }

    fun updateTag(accountName: String, tagId: String, displayName: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = updateTagUseCase(UpdateTagUseCase.Params(accountName = accountName, tagId = tagId, displayName = displayName))
            if (result is UseCaseResult.Success) {
                loadTags(accountName)
            }
        }
    }

    fun deleteTag(accountName: String, tagId: String) {
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = deleteTagUseCase(DeleteTagUseCase.Params(accountName = accountName, tagId = tagId))
            if (result is UseCaseResult.Success) {
                loadTags(accountName)
            }
        }
    }

    sealed class TagsUiState {
        data object Loading : TagsUiState()
        data class Success(val tags: List<OCTag>) : TagsUiState()
        data object Empty : TagsUiState()
        data class Error(val throwable: Throwable) : TagsUiState()
    }
}
