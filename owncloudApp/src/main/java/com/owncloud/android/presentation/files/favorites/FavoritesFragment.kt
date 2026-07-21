package com.owncloud.android.presentation.files.favorites

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import com.owncloud.android.R
import com.owncloud.android.databinding.FavoritesFragmentBinding
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.extensions.filterMenuOptions
import com.owncloud.android.extensions.isLandscapeMode
import com.owncloud.android.extensions.isTablet
import com.owncloud.android.extensions.sendDownloadedFilesByShareSheet
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.capabilities.CapabilityViewModel
import com.owncloud.android.presentation.files.SortBottomSheetFragment
import com.owncloud.android.presentation.files.SortOptionsView
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.ColumnQuantity
import com.owncloud.android.presentation.files.filelist.FileListActionModeController
import com.owncloud.android.presentation.files.filelist.compose.setFileListContent
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.activity.FolderPickerActivity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class FavoritesFragment : Fragment(),
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: FavoritesFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val favoritesViewModel: FavoritesViewModel by viewModel()
    private val fileOperationsViewModel: FileOperationsViewModel by activityViewModel()

    private val capabilityViewModel: CapabilityViewModel by activityViewModel {
        parametersOf(
            AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        )
    }

    private val actionModeController = FileListActionModeController(
        object : FileListActionModeController.Host {
            override fun requireAppCompatActivity(): AppCompatActivity =
                requireActivity() as AppCompatActivity

            override fun getCheckedItems(): List<OCFileWithSyncInfo> =
                this@FavoritesFragment.getCheckedItems()

            override fun clearSelection() {
                favoritesViewModel.clearSelection()
            }

            override fun onActionItemClicked(itemId: Int?): Boolean =
                onFileActionChosen(itemId)

            override fun onPrepareMultiSelect(checkedItems: List<OCFileWithSyncInfo>, menu: Menu?) {
                val displaySelectAll =
                    checkedItems.size != favoritesViewModel.composeUiState.value.selectableFileIds().size
                favoritesViewModel.filterMenuOptions(
                    checkedItems.map { it.file },
                    FileListActionModeController.toSyncInfoList(checkedItems),
                    displaySelectAll,
                    isMultiselection = true,
                )
            }

            override fun onEnterMultiSelect() {
                binding.optionsLayout.visibility = View.GONE
            }

            override fun onExitMultiSelect() {
                binding.optionsLayout.visibility = View.VISIBLE
            }
        }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FavoritesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        favoritesViewModel.setMultiPersonal(capabilityViewModel.checkMultiPersonal())
        initViews()
        subscribeToViewModels()

        val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        accountName?.let { favoritesViewModel.loadFavorites(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.forEach { it.isVisible = false }
    }

    private fun initViews() {
        if (favoritesViewModel.isGridModeSetAsPreferred()) {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_GRID
            favoritesViewModel.setGridModeAsPreferred()
            favoritesViewModel.updateGridColumns(
                ColumnQuantity(requireContext()).calculateNoOfColumns(binding.root)
            )
        } else {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_LIST
            favoritesViewModel.setListModeAsPreferred()
        }
        binding.optionsLayout.sortTypeSelected = favoritesViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = favoritesViewModel.getSortOrder()
        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)

        setupComposeFileList()
    }

    private fun setupComposeFileList() {
        val account = AccountUtils.getCurrentOwnCloudAccount(requireContext())
        binding.composeViewFavorites.setFileListContent(
            uiStateFlow = favoritesViewModel.composeUiState,
            account = account,
            scrollToTopEvents = favoritesViewModel.scrollToTopEvents,
            onItemClick = ::onComposeItemClick,
            onItemLongClick = ::onComposeItemLongClick,
            onSelectionBecameEmpty = { actionModeController.finish() },
        )
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(favoritesViewModel.menuOptions) { menuOptions ->
            val checkedFiles = actionModeController.checkedFiles
            val hasWritePermission = if (checkedFiles.size == 1) {
                checkedFiles.first().hasWritePermission
            } else {
                false
            }
            actionModeController.menu?.filterMenuOptions(menuOptions, hasWritePermission)
        }

        collectLatestLifecycleFlow(fileOperationsViewModel.disableSelectionModeEvent) {
            disableSelectionMode()
        }
    }

    private fun getCheckedItems(): List<OCFileWithSyncInfo> =
        favoritesViewModel.composeUiState.value.checkedItems()

    private fun findFileWithSyncInfo(fileId: Long): OCFileWithSyncInfo? =
        favoritesViewModel.composeUiState.value.findFile(fileId)

    private fun toggleSelection(fileId: Long) {
        favoritesViewModel.toggleSelection(fileId)
        updateActionModeAfterTogglingSelected()
    }

    private fun updateActionModeAfterTogglingSelected() {
        actionModeController.syncWithSelectionCount(
            favoritesViewModel.composeUiState.value.selectedItemCount,
        )
    }

    private fun disableSelectionMode() {
        favoritesViewModel.clearSelection()
        updateActionModeAfterTogglingSelected()
    }

    private fun onComposeItemClick(fileId: Long) {
        val ocFileWithSyncInfo = findFileWithSyncInfo(fileId) ?: return
        val file = ocFileWithSyncInfo.file

        if (file.isVirtualFile()) {
            // Favorites has no virtual-file popup host; ignore like adapter (no long-press either).
            return
        }

        if (actionModeController.isActive) {
            toggleSelection(fileId)
            return
        }

        val fileDisplayActivity = requireActivity() as? FileDisplayActivity
        if (file.isFolder) {
            fileDisplayActivity?.startFolderPreview(file)
        } else {
            fileDisplayActivity?.onFileClicked(file)
        }
    }

    private fun onComposeItemLongClick(fileId: Long) {
        if (requireContext().isLandscapeMode && !requireContext().isTablet) return

        val file = findFileWithSyncInfo(fileId)?.file ?: return
        if (file.isVirtualFile()) return

        actionModeController.startIfNeeded()
        toggleSelection(fileId)
    }

    private fun onFileActionChosen(menuId: Int?): Boolean {
        val checkedFilesWithSyncInfo = getCheckedItems()

        if (checkedFilesWithSyncInfo.isEmpty()) {
            return false
        } else if (checkedFilesWithSyncInfo.size == 1) {
            val singleFile = checkedFilesWithSyncInfo.first().file
            if (onSingleFileActionChosen(menuId, singleFile)) {
                return true
            }
        }

        val checkedFiles = checkedFilesWithSyncInfo.map { it.file }
        return onCheckedFilesActionChosen(menuId, checkedFiles)
    }

    private fun onSingleFileActionChosen(menuId: Int?, singleFile: OCFile): Boolean {
        return when (menuId) {
            R.id.action_see_details -> {
                disableSelectionMode()
                (requireActivity() as? FileDisplayActivity)?.showDetails(singleFile)
                true
            }

            R.id.action_set_available_offline -> {
                fileOperationsViewModel.performOperation(FileOperation.SetFilesAsAvailableOffline(listOf(singleFile)))
                if (singleFile.isFolder) {
                    fileOperationsViewModel.performOperation(
                        FileOperation.SynchronizeFolderOperation(
                            folderToSync = singleFile,
                            accountName = singleFile.owner,
                            isActionSetFolderAvailableOfflineOrSynchronize = true,
                        )
                    )
                } else {
                    fileOperationsViewModel.performOperation(FileOperation.SynchronizeFileOperation(singleFile, singleFile.owner))
                }
                true
            }

            R.id.action_unset_available_offline -> {
                fileOperationsViewModel.performOperation(FileOperation.UnsetFilesAsAvailableOffline(listOf(singleFile)))
                true
            }

            R.id.action_sync_file -> {
                syncFiles(listOf(singleFile))
                true
            }

            R.id.action_send_file -> {
                requireActivity().sendDownloadedFilesByShareSheet(listOf(singleFile))
                true
            }

            else -> false
        }
    }

    private fun onCheckedFilesActionChosen(menuId: Int?, checkedFiles: List<OCFile>): Boolean {
        return when (menuId) {
            R.id.file_action_select_all -> {
                favoritesViewModel.selectAll()
                updateActionModeAfterTogglingSelected()
                true
            }

            R.id.action_select_inverse -> {
                favoritesViewModel.selectInverse()
                updateActionModeAfterTogglingSelected()
                true
            }

            R.id.action_remove_file -> {
                val dialog = RemoveFilesDialogFragment.newInstance(ArrayList(checkedFiles), false)
                dialog.show(requireActivity().supportFragmentManager, RemoveFilesDialogFragment.TAG_REMOVE_FILES_DIALOG_FRAGMENT)
                true
            }

            R.id.action_download_file,
            R.id.action_sync_file -> {
                syncFiles(checkedFiles)
                true
            }

            R.id.action_set_available_offline -> {
                fileOperationsViewModel.performOperation(FileOperation.SetFilesAsAvailableOffline(checkedFiles))
                checkedFiles.forEach { ocFile ->
                    if (ocFile.isFolder) {
                        fileOperationsViewModel.performOperation(FileOperation.SynchronizeFolderOperation(ocFile, ocFile.owner))
                    } else {
                        fileOperationsViewModel.performOperation(FileOperation.SynchronizeFileOperation(ocFile, ocFile.owner))
                    }
                }
                true
            }

            R.id.action_unset_available_offline -> {
                fileOperationsViewModel.performOperation(FileOperation.UnsetFilesAsAvailableOffline(checkedFiles))
                true
            }

            R.id.action_send_file -> {
                requireActivity().sendDownloadedFilesByShareSheet(checkedFiles)
                true
            }

            R.id.action_move -> {
                val action = Intent(activity, FolderPickerActivity::class.java)
                action.putParcelableArrayListExtra(FolderPickerActivity.EXTRA_FILES, ArrayList(checkedFiles))
                action.putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.MOVE)
                requireActivity().startActivityForResult(action, FileDisplayActivity.REQUEST_CODE__MOVE_FILES)
                disableSelectionMode()
                true
            }

            R.id.action_copy -> {
                val action = Intent(activity, FolderPickerActivity::class.java)
                action.putParcelableArrayListExtra(FolderPickerActivity.EXTRA_FILES, ArrayList(checkedFiles))
                action.putExtra(FolderPickerActivity.EXTRA_PICKER_MODE, FolderPickerActivity.PickerMode.COPY)
                requireActivity().startActivityForResult(action, FileDisplayActivity.REQUEST_CODE__COPY_FILES)
                disableSelectionMode()
                true
            }

            R.id.action_set_favorite -> {
                checkedFiles.firstOrNull()?.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(FileOperation.SetFileFavoriteStatus(fileId, isFavorite = true))
                }
                disableSelectionMode()
                true
            }

            R.id.action_unset_favorite -> {
                checkedFiles.firstOrNull()?.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(FileOperation.SetFileFavoriteStatus(fileId, isFavorite = false))
                }
                disableSelectionMode()
                true
            }

            else -> false
        }
    }

    private fun syncFiles(files: List<OCFile>) {
        for (file in files) {
            if (file.isFolder) {
                fileOperationsViewModel.performOperation(
                    FileOperation.SynchronizeFolderOperation(
                        folderToSync = file,
                        accountName = file.owner,
                        isActionSetFolderAvailableOfflineOrSynchronize = true,
                    )
                )
            } else {
                fileOperationsViewModel.performOperation(FileOperation.SynchronizeFileOperation(fileToSync = file, accountName = file.owner))
            }
        }
    }

    override fun onSortTypeListener(sortType: SortType, sortOrder: SortOrder) {
        val sortBottomSheetFragment = SortBottomSheetFragment.newInstance(sortType, sortOrder)
        sortBottomSheetFragment.sortDialogListener = this
        sortBottomSheetFragment.show(childFragmentManager, SortBottomSheetFragment.TAG)
    }

    override fun onViewTypeListener(viewType: ViewType) {
        binding.optionsLayout.viewTypeSelected = viewType

        if (viewType == ViewType.VIEW_TYPE_LIST) {
            favoritesViewModel.setListModeAsPreferred()
        } else {
            favoritesViewModel.setGridModeAsPreferred()
            favoritesViewModel.updateGridColumns(
                ColumnQuantity(requireContext()).calculateNoOfColumns(binding.root)
            )
        }
    }

    override fun onSortSelected(sortType: SortType) {
        binding.optionsLayout.sortTypeSelected = sortType
        favoritesViewModel.updateSortTypeAndOrder(sortType, binding.optionsLayout.sortOrderSelected)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FavoritesFragment = FavoritesFragment()
    }
}
