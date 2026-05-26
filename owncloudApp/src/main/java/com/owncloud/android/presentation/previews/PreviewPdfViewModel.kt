package com.owncloud.android.presentation.previews

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.usecases.GetFileByIdAsStreamUseCase
import com.owncloud.android.providers.ContextProvider
import com.owncloud.android.providers.CoroutinesDispatcherProvider
import com.owncloud.android.usecases.files.FilterFileMenuOptionsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PreviewPdfViewModel(
    private val filterFileMenuOptionsUseCase: FilterFileMenuOptionsUseCase,
    getFileByIdAsStreamUseCase: GetFileByIdAsStreamUseCase,
    private val contextProvider: ContextProvider,
    private val coroutinesDispatcherProvider: CoroutinesDispatcherProvider,
    ocFile: OCFile,
) : ViewModel() {

    private val _menuOptions: MutableStateFlow<List<FileMenuOption>> = MutableStateFlow(emptyList())
    val menuOptions: StateFlow<List<FileMenuOption>> = _menuOptions

    private val currentFile: Flow<OCFile?> = getFileByIdAsStreamUseCase(GetFileByIdAsStreamUseCase.Params(ocFile.id!!))

    fun getCurrentFile(): Flow<OCFile?> = currentFile

    fun filterMenuOptions(file: OCFile, accountName: String) {
        val shareViaLinkAllowed = contextProvider.getBoolean(R.bool.share_via_link_feature)
        val shareWithUsersAllowed = contextProvider.getBoolean(R.bool.share_with_users_feature)
        val sendAllowed = contextProvider.getString(R.string.send_files_to_other_apps).equals("on", ignoreCase = true)
        viewModelScope.launch(coroutinesDispatcherProvider.io) {
            val result = filterFileMenuOptionsUseCase(
                FilterFileMenuOptionsUseCase.Params(
                    files = listOf(file),
                    accountName = accountName,
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
            result.apply {
                remove(FileMenuOption.RENAME)
                remove(FileMenuOption.MOVE)
                remove(FileMenuOption.COPY)
            }
            _menuOptions.update { result }
        }
    }
}
