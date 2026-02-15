package com.owncloud.android.presentation.files.favorites

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Typeface
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
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.owncloud.android.R
import com.owncloud.android.databinding.FavoritesFragmentBinding
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
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.activity.FolderPickerActivity
import com.owncloud.android.ui.preview.PreviewImageActivity
import com.owncloud.android.ui.preview.PreviewImageFragment
import com.owncloud.android.ui.preview.PreviewTextFragment
import com.owncloud.android.ui.preview.PreviewVideoActivity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.sharedViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class FavoritesFragment : Fragment(),
    FileListAdapter.FileListAdapterListener,
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: FavoritesFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val favoritesViewModel: FavoritesViewModel by viewModel()
    private val fileOperationsViewModel by sharedViewModel<FileOperationsViewModel>()

    private val capabilityViewModel: CapabilityViewModel by activityViewModel {
        parametersOf(
            AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        )
    }

    private val layoutManager: StaggeredGridLayoutManager by lazy {
        if (favoritesViewModel.isGridModeSetAsPreferred()) {
            StaggeredGridLayoutManager(
                ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root),
                RecyclerView.VERTICAL
            )
        } else {
            StaggeredGridLayoutManager(1, RecyclerView.VERTICAL)
        }
    }

    private val fileListAdapter: FileListAdapter by lazy {
        FileListAdapter(
            context = requireContext(),
            layoutManager = layoutManager,
            isPickerMode = false,
            listener = this,
            isMultiPersonal = isMultiPersonal
        )
    }

    private var isMultiPersonal = false
    private var actionMode: ActionMode? = null
    private var statusBarColor: Int? = null
    private var menu: Menu? = null
    private var checkedFiles: List<OCFile> = emptyList()

    private val actionModeCallback: ActionMode.Callback = object : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            actionMode = mode

            val inflater = requireActivity().menuInflater
            inflater.inflate(R.menu.file_actions_menu, menu)
            this@FavoritesFragment.menu = menu

            mode?.invalidate()

            val window = activity?.window
            statusBarColor = window?.statusBarColor ?: -1

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

            val displaySelectAll = checkedCount != fileListAdapter.itemCount - 1
            favoritesViewModel.filterMenuOptions(
                checkedFiles, checkedFilesSync,
                displaySelectAll, isMultiselection = true
            )
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean =
            onFileActionChosen(item?.itemId)

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null

            statusBarColor?.let { requireActivity().window.statusBarColor = it }

            binding.optionsLayout.visibility = View.VISIBLE

            fileListAdapter.clearSelection()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FavoritesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        isMultiPersonal = capabilityViewModel.checkMultiPersonal()
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
        binding.optionsLayout.viewTypeSelected = if (favoritesViewModel.isGridModeSetAsPreferred()) ViewType.VIEW_TYPE_GRID else ViewType.VIEW_TYPE_LIST
        binding.optionsLayout.sortTypeSelected = favoritesViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = favoritesViewModel.getSortOrder()

        binding.recyclerViewFavorites.layoutManager = layoutManager
        binding.recyclerViewFavorites.adapter = fileListAdapter

        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(favoritesViewModel.favoritesUiState) { uiState ->
            when (uiState) {
                is FavoritesViewModel.FavoritesUiState.Loading -> {
                    showLoading()
                }

                is FavoritesViewModel.FavoritesUiState.Success -> {
                    showResults(uiState.results)
                }

                is FavoritesViewModel.FavoritesUiState.Empty -> {
                    showEmptyState()
                }
            }
        }

        collectLatestLifecycleFlow(favoritesViewModel.menuOptions) { menuOptions ->
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

    private fun showLoading() {
        binding.recyclerViewFavorites.isVisible = false
        binding.favoritesListEmpty.root.isVisible = false
    }

    private fun showResults(results: List<OCFileWithSyncInfo>) {
        binding.recyclerViewFavorites.isVisible = true
        binding.favoritesListEmpty.root.isVisible = false

        fileListAdapter.updateFileList(
            filesToAdd = results,
            fileListOption = FileListOption.FAVORITES,
        ) {
            binding.recyclerViewFavorites.post { binding.recyclerViewFavorites.scrollToPosition(0) }
        }
    }

    private fun showEmptyState() {
        binding.recyclerViewFavorites.isVisible = false
        binding.favoritesListEmpty.root.isVisible = true
        binding.favoritesListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_star_big_gray)
        binding.favoritesListEmpty.listEmptyDatasetTitle.textSize = 20f
        binding.favoritesListEmpty.listEmptyDatasetTitle.setTypeface(null, Typeface.NORMAL)
        binding.favoritesListEmpty.listEmptyDatasetTitle.setText(R.string.favorites_empty_title)
        binding.favoritesListEmpty.listEmptyDatasetSubTitle.setText(R.string.favorites_empty_subtitle)
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
        return when (menuId) {
            R.id.action_see_details -> {
                disableSelectionMode()
                (requireActivity() as? FavoritesActivity)?.showDetails(singleFile)
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
        val account = AccountUtils.getCurrentOwnCloudAccount(requireContext())
        val favoritesActivity = requireActivity() as? FavoritesActivity

        when {
            PreviewImageFragment.canBePreviewed(file) -> {
                // Open image preview activity — back will return to favorites
                val intent = Intent(requireContext(), PreviewImageActivity::class.java).apply {
                    putExtra(FileActivity.EXTRA_FILE, file)
                    putExtra(FileActivity.EXTRA_ACCOUNT, account)
                }
                startActivity(intent)
            }

            PreviewVideoActivity.canBePreviewed(file) -> {
                // Open video preview activity — back will return to favorites
                val intent = Intent(requireContext(), PreviewVideoActivity::class.java).apply {
                    putExtra(PreviewVideoActivity.EXTRA_FILE, file)
                    putExtra(PreviewVideoActivity.EXTRA_ACCOUNT, account)
                    putExtra(PreviewVideoActivity.EXTRA_PLAY_POSITION, 0)
                }
                startActivity(intent)
            }

            PreviewTextFragment.canBePreviewed(file) -> {
                // Open text preview fragment inside FavoritesActivity
                favoritesActivity?.showTextPreview(file)
            }

            else -> {
                // Other files — show file details fragment inside FavoritesActivity
                favoritesActivity?.showDetails(file)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onLongItemClick(position: Int): Boolean {
        if (requireContext().isLandscapeMode && !requireContext().isTablet) return false

        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
            fileListAdapter.notifyDataSetChanged()
        }
        toggleSelection(position)
        return true
    }

    override fun onThreeDotButtonClick(fileWithSyncInfo: OCFileWithSyncInfo) {
        // empty, do not show 3 dots menu
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
            favoritesViewModel.setListModeAsPreferred()
            layoutManager.spanCount = 1
        } else {
            favoritesViewModel.setGridModeAsPreferred()
            layoutManager.spanCount = ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root)
        }

        fileListAdapter.notifyDataSetChanged()
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
