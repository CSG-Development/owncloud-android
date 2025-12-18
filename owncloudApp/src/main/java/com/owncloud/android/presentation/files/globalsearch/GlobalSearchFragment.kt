package com.owncloud.android.presentation.files.globalsearch

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.owncloud.android.R
import com.owncloud.android.databinding.GlobalSearchFragmentBinding
import com.owncloud.android.domain.files.model.FileListOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
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
import com.owncloud.android.presentation.files.filelist.FileListAdapter
import com.owncloud.android.presentation.files.filelist.MainFileListFragment
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.presentation.files.renamefile.RenameFileDialogFragment
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.activity.FolderPickerActivity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class GlobalSearchFragment : Fragment(),
    FileListAdapter.FileListAdapterListener,
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: GlobalSearchFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val globalSearchViewModel: GlobalSearchViewModel by viewModel()
    private val fileOperationsViewModel by sharedViewModel<FileOperationsViewModel>()

    private val capabilityViewModel: CapabilityViewModel by activityViewModel {
        parametersOf(
            AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        )
    }

    private val layoutManager: StaggeredGridLayoutManager by lazy {
        if (globalSearchViewModel.isGridModeSetAsPreferred()) {
            StaggeredGridLayoutManager(
                ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root),
                RecyclerView.VERTICAL
            )
        } else {
            StaggeredGridLayoutManager(1, RecyclerView.VERTICAL)
        }
    }
    private lateinit var fileListAdapter: FileListAdapter
    private lateinit var viewType: ViewType

    private var isMultiPersonal = false

    // Action Mode related
    private var actionMode: ActionMode? = null
    private var statusBarColor: Int? = null
    private var menu: Menu? = null
    private var checkedFiles: List<OCFile> = emptyList()

    private val actionModeCallback: ActionMode.Callback = object : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            setDrawerStatus(enabled = false)
            actionMode = mode

            val inflater = requireActivity().menuInflater
            inflater.inflate(R.menu.file_actions_menu, menu)
            this@GlobalSearchFragment.menu = menu

            mode?.invalidate()

            val window = activity?.window
            statusBarColor = window?.statusBarColor ?: -1

            (requireActivity() as? MainFileListFragment.FileActions)?.setBottomBarVisibility(false)

            binding.optionsLayout.visibility = View.GONE

            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val checkedFilesWithSyncInfo = fileListAdapter.getCheckedItems()
            val checkedCount = checkedFilesWithSyncInfo.size
            val title = resources.getQuantityString(
                R.plurals.items_selected_count,
                checkedCount,
                checkedCount
            )
            mode?.title = title

            checkedFiles = checkedFilesWithSyncInfo.map { it.file }

            val checkedFilesSync = checkedFilesWithSyncInfo.map {
                OCFileSyncInfo(
                    fileId = it.file.id!!,
                    uploadWorkerUuid = it.uploadWorkerUuid,
                    downloadWorkerUuid = it.downloadWorkerUuid,
                    isSynchronizing = it.isSynchronizing
                )
            }

            val displaySelectAll = checkedCount != fileListAdapter.itemCount - 1 // -1 because one of them is the footer
            globalSearchViewModel.filterMenuOptions(
                checkedFiles, checkedFilesSync,
                displaySelectAll, isMultiselection = true
            )
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean =
            onFileActionChosen(item?.itemId)

        override fun onDestroyActionMode(mode: ActionMode?) {
            setDrawerStatus(enabled = true)
            actionMode = null

            statusBarColor?.let { requireActivity().window.statusBarColor = it }

            (requireActivity() as? MainFileListFragment.FileActions)?.setBottomBarVisibility(true)

            binding.optionsLayout.visibility = View.VISIBLE

            fileListAdapter.clearSelection()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = GlobalSearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        isMultiPersonal = capabilityViewModel.checkMultiPersonal()
        initViews()
        subscribeToViewModels()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.forEach { it.isVisible = false }
    }

    private fun initViews() {
        viewType = if (globalSearchViewModel.isGridModeSetAsPreferred()) ViewType.VIEW_TYPE_GRID else ViewType.VIEW_TYPE_LIST

        binding.optionsLayout.viewTypeSelected = viewType
        binding.optionsLayout.sortTypeSelected = globalSearchViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = globalSearchViewModel.getSortOrder()

        binding.recyclerViewMainFileList.layoutManager = layoutManager

        fileListAdapter = FileListAdapter(
            context = requireContext(),
            layoutManager = layoutManager,
            isPickerMode = false,
            listener = this,
            isMultiPersonal = isMultiPersonal
        )

        binding.recyclerViewMainFileList.adapter = fileListAdapter

        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)

        showInitialState()
    }

    fun updateSearchQuery(query: String) {
        globalSearchViewModel.updateSearchQuery(query)
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(globalSearchViewModel.searchUiState) { uiState ->
            when (uiState) {
                is GlobalSearchViewModel.SearchUiState.Initial -> {
                    showInitialState()
                }

                is GlobalSearchViewModel.SearchUiState.Loading -> {
                    showLoading()
                }

                is GlobalSearchViewModel.SearchUiState.Success -> {
                    showResults(uiState.results)
                }

                is GlobalSearchViewModel.SearchUiState.Empty -> {
                    showEmptyState()
                }

                is GlobalSearchViewModel.SearchUiState.Error -> {
                    showError(uiState.message)
                }
            }
        }

        // Observe menu options for multiselection
        collectLatestLifecycleFlow(globalSearchViewModel.menuOptions) { menuOptions ->
            val hasWritePermission = if (checkedFiles.size == 1) {
                checkedFiles.first().hasWritePermission
            } else {
                false
            }
            menu?.filterMenuOptions(menuOptions, hasWritePermission)
        }

        collectLatestLifecycleFlow(fileOperationsViewModel.disableSelectionModeEvent) {
            disableSelectionMode()
        }
    }

    private fun showInitialState() {
        binding.recyclerViewMainFileList.isVisible = false
        binding.transfersListEmpty.root.isVisible = true
        binding.transfersListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_search_2)
        binding.transfersListEmpty.listEmptyDatasetTitle.setText(R.string.homecloud_global_search_initial_title)
        binding.transfersListEmpty.listEmptyDatasetSubTitle.text = ""
    }

    private fun showLoading() {
        binding.recyclerViewMainFileList.isVisible = false
        binding.transfersListEmpty.root.isVisible = false
    }

    private fun showResults(results: List<OCFileWithSyncInfo>) {
        binding.recyclerViewMainFileList.isVisible = true
        binding.transfersListEmpty.root.isVisible = false

        fileListAdapter.updateFileList(
            filesToAdd = results,
            fileListOption = FileListOption.GLOBAL_SEARCH,
        ) {
            binding.recyclerViewMainFileList.post { binding.recyclerViewMainFileList.scrollToPosition(0) }
        }
    }

    private fun showEmptyState() {
        binding.recyclerViewMainFileList.isVisible = false
        binding.transfersListEmpty.root.isVisible = true
        binding.transfersListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_search_2)
        binding.transfersListEmpty.listEmptyDatasetTitle.setText(R.string.homecloud_global_search_empty_title)
        binding.transfersListEmpty.listEmptyDatasetSubTitle.setText(R.string.homecloud_global_search_empty_subtitle)
    }

    private fun showError(message: String) {
        binding.recyclerViewMainFileList.isVisible = false
        binding.transfersListEmpty.root.isVisible = true
        binding.transfersListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_search)
        binding.transfersListEmpty.listEmptyDatasetTitle.text = message
        binding.transfersListEmpty.listEmptyDatasetSubTitle.text = ""
    }

    private fun setDrawerStatus(enabled: Boolean) {
        (activity as? FileActivity)?.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )
    }

    private fun toggleSelection(position: Int) {
        fileListAdapter.toggleSelection(position)
        updateActionModeAfterTogglingSelected()
    }

    private fun updateActionModeAfterTogglingSelected() {
        val selectedItems = fileListAdapter.selectedItemCount
        if (selectedItems == 0) {
            actionMode?.finish()
        } else {
            if (actionMode == null) {
                actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
            }
            actionMode?.apply {
                title = selectedItems.toString()
                invalidate()
            }
        }
    }

    private fun disableSelectionMode() {
        fileListAdapter.clearSelection()
        updateActionModeAfterTogglingSelected()
    }

    private fun onFileActionChosen(menuId: Int?): Boolean {
        val checkedFilesWithSyncInfo = fileListAdapter.getCheckedItems()

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
        val fileActions = requireActivity() as? MainFileListFragment.FileActions

        return when (menuId) {
            R.id.action_share_file -> {
                fileActions?.onShareFileClicked(singleFile)
                disableSelectionMode()
                true
            }

            R.id.action_open_file_with -> {
                fileActions?.openFile(singleFile)
                disableSelectionMode()
                true
            }

            R.id.action_rename_file -> {
                val dialog = RenameFileDialogFragment.newInstance(singleFile)
                dialog.show(requireActivity().supportFragmentManager, RenameFileDialogFragment.FRAGMENT_TAG_RENAME_FILE)
                disableSelectionMode()
                true
            }

            R.id.action_see_details -> {
                disableSelectionMode()
                fileActions?.showDetails(singleFile)
                true
            }

            R.id.action_sync_file -> {
                syncFiles(listOf(singleFile))
                true
            }

            R.id.action_send_file -> {
                if (!singleFile.isAvailableLocally) {
                    Timber.d("%s : File must be downloaded", singleFile.remotePath)
                    fileActions?.initDownloadForSending(singleFile)
                } else {
                    fileActions?.sendDownloadedFile(singleFile)
                }
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

            else -> false
        }
    }

    private fun onCheckedFilesActionChosen(menuId: Int?, checkedFiles: List<OCFile>): Boolean {
        val fileActions = requireActivity() as? MainFileListFragment.FileActions

        return when (menuId) {
            R.id.file_action_select_all -> {
                fileListAdapter.selectAll()
                updateActionModeAfterTogglingSelected()
                true
            }

            R.id.action_select_inverse -> {
                fileListAdapter.selectInverse()
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

            R.id.action_cancel_sync -> {
                fileActions?.cancelFileTransference(checkedFiles)
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

    // FileListAdapterListener implementation
    override fun onItemClick(ocFileWithSyncInfo: OCFileWithSyncInfo, position: Int) {
        if (actionMode != null) {
            toggleSelection(position)
            return
        }

        val file = ocFileWithSyncInfo.file
        val fileActions = requireActivity() as? MainFileListFragment.FileActions
        fileActions?.onFileClicked(file)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLongItemClick(position: Int): Boolean {
        if (requireContext().isLandscapeMode && !requireContext().isTablet) return false

        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
            // Notify all when enabling multiselection for the first time to show checkboxes on every single item.
            fileListAdapter.notifyDataSetChanged()
        }
        toggleSelection(position)
        return true
    }

    override fun onThreeDotButtonClick(fileWithSyncInfo: OCFileWithSyncInfo) {
        val fileActions = requireActivity() as? MainFileListFragment.FileActions
        fileActions?.showDetails(fileWithSyncInfo.file)
    }

    // SortOptionsListener implementation
    override fun onSortTypeListener(sortType: SortType, sortOrder: SortOrder) {
        val sortBottomSheetFragment = SortBottomSheetFragment.newInstance(sortType, sortOrder)
        sortBottomSheetFragment.sortDialogListener = this
        sortBottomSheetFragment.show(childFragmentManager, SortBottomSheetFragment.TAG)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewTypeListener(viewType: ViewType) {
        binding.optionsLayout.viewTypeSelected = viewType

        if (viewType == ViewType.VIEW_TYPE_LIST) {
            globalSearchViewModel.setListModeAsPreferred()
            layoutManager.spanCount = 1
        } else {
            globalSearchViewModel.setGridModeAsPreferred()
            layoutManager.spanCount = ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root)
        }

        fileListAdapter.notifyDataSetChanged()
    }

    override fun onSortSelected(sortType: SortType) {
        binding.optionsLayout.sortTypeSelected = sortType
        globalSearchViewModel.updateSortTypeAndOrder(sortType, binding.optionsLayout.sortOrderSelected)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
