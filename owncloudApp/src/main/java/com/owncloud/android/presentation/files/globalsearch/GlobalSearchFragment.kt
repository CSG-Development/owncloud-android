package com.owncloud.android.presentation.files.globalsearch

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.forEach
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.owncloud.android.R
import com.owncloud.android.databinding.GlobalSearchFragmentBinding
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.domain.files.model.isVirtualFile
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.extensions.filterMenuOptions
import com.owncloud.android.extensions.isLandscapeMode
import com.owncloud.android.extensions.isTablet
import com.owncloud.android.extensions.sendDownloadedFilesByShareSheet
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.capabilities.CapabilityViewModel
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.files.SortBottomSheetFragment
import com.owncloud.android.presentation.files.SortOptionsView
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.ColumnQuantity
import com.owncloud.android.presentation.files.filelist.MainFileListFragment
import com.owncloud.android.presentation.files.filelist.compose.FileList
import com.owncloud.android.presentation.files.filelist.compose.FileListItemUiModel
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.rememberFileListThumbnail
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.presentation.files.renamefile.RenameFileDialogFragment
import com.owncloud.android.ui.activity.BaseActivity
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import com.owncloud.android.ui.activity.FolderPickerActivity
import com.owncloud.android.utils.PreferenceUtils
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber

class GlobalSearchFragment : Fragment(),
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: GlobalSearchFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val globalSearchViewModel: GlobalSearchViewModel by viewModel()
    private val fileOperationsViewModel by activityViewModel<FileOperationsViewModel>()

    private val capabilityViewModel: CapabilityViewModel by activityViewModel {
        parametersOf(
            AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        )
    }

    private val listScrollState = LazyListState()
    private val gridScrollState = LazyGridState()

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
            val checkedFilesWithSyncInfo = getCheckedItems()
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

            val displaySelectAll = checkedCount != globalSearchViewModel.composeUiState.value.selectableFileIds().size
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

            globalSearchViewModel.clearSelection()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = GlobalSearchFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        globalSearchViewModel.setMultiPersonal(capabilityViewModel.checkMultiPersonal())
        initViews()
        subscribeToViewModels()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.forEach { it.isVisible = false }
    }

    private fun initViews() {
        if (globalSearchViewModel.isGridModeSetAsPreferred()) {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_GRID
            globalSearchViewModel.setGridModeAsPreferred()
            globalSearchViewModel.updateGridColumns(
                ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root)
            )
        } else {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_LIST
            globalSearchViewModel.setListModeAsPreferred()
        }
        binding.optionsLayout.sortTypeSelected = globalSearchViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = globalSearchViewModel.getSortOrder()
        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)

        setupFilterButtons()
        setupComposeFileList()
    }

    private fun setupComposeFileList() {
        val account = AccountUtils.getCurrentOwnCloudAccount(requireContext())
        binding.composeViewGlobalSearch.apply {
            filterTouchesWhenObscured =
                PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val composeState by globalSearchViewModel.composeUiState.collectAsState()
                LaunchedEffect(composeState.hasSelection) {
                    if (!composeState.hasSelection && actionMode != null) {
                        actionMode?.finish()
                    }
                }
                HomeCloudTheme {
                    val filesById = remember(composeState.folderContent) {
                        composeState.folderContent.associateBy { it.file.id }
                    }
                    val filesByIdState = rememberUpdatedState(filesById)
                    val accountState = rememberUpdatedState(account)
                    val onItemClick = remember<(FileListItemUiModel) -> Unit> {
                        { onComposeItemClick(it.fileId) }
                    }
                    val onItemLongClick = remember<(FileListItemUiModel) -> Unit> {
                        { onComposeItemLongClick(it.fileId) }
                    }
                    val onThreeDotClick = remember<(FileListItemUiModel) -> Unit> {
                        { onComposeThreeDotClick(it.fileId) }
                    }
                    val thumbnail: @Composable (FileListItemUiModel) -> Bitmap? =
                        remember {
                            { item ->
                                val file = filesByIdState.value[item.fileId]?.file
                                rememberFileListThumbnail(
                                    file = file?.takeUnless { it.isFolder || it.isVirtualFile() },
                                    account = accountState.value,
                                )
                            }
                        }
                    FileList(
                        content = composeState.content,
                        layoutMode = composeState.layoutMode,
                        selectedIds = composeState.selectedIds,
                        gridColumns = composeState.gridColumns,
                        listState = listScrollState,
                        gridState = gridScrollState,
                        pullToRefreshEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                        thumbnail = thumbnail,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        onThreeDotClick = onThreeDotClick,
                    )
                }
            }
        }
    }

    private fun setupFilterButtons() {
        binding.searchFilters.filterTypeButton.setOnClickListener { showTypeFilterBottomSheet() }
        binding.searchFilters.filterDateButton.setOnClickListener { showDateFilterBottomSheet() }
        binding.searchFilters.filterSizeButton.setOnClickListener { showSizeFilterBottomSheet() }
        binding.searchFilters.filterTagsButton.setOnClickListener { loadTags() }
    }

    private fun loadTags() {
        val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name ?: return
        globalSearchViewModel.loadTagsForAccount(accountName)
    }

    private fun showTypeFilterBottomSheet() {
        val items = TypeFilter.entries.map { filter ->
            FilterItem(
                id = filter.id,
                label = getString(filter.labelResId),
                iconResId = filter.iconResId
            )
        }

        val bottomSheet = FilterBottomSheetFragment.newInstance(
            title = getString(R.string.homecloud_global_search_filter_type),
            items = items,
            selectedIds = globalSearchViewModel.getFiltersState().selectedTypes.map { it.id }.toSet(),
            isMultiSelect = false
        )

        bottomSheet.filterSelectionListener = object : FilterBottomSheetFragment.FilterSelectionListener {
            override fun onFilterSelected(selectedIds: Set<String>) {
                globalSearchViewModel.updateTypeFilters(selectedIds.mapNotNull { TypeFilter.fromId(it) }.toSet())
            }
        }

        bottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
    }

    private fun showDateFilterBottomSheet() {
        val items = DateFilter.entries.map { filter ->
            FilterItem(
                id = filter.id,
                label = getString(filter.labelResId),
                iconResId = filter.iconResId
            )
        }

        val bottomSheet = FilterBottomSheetFragment.newInstance(
            title = getString(R.string.homecloud_global_search_filter_date),
            items = items,
            selectedIds = setOf(globalSearchViewModel.getFiltersState().dateFilter.id),
            isMultiSelect = false
        )

        bottomSheet.filterSelectionListener = object : FilterBottomSheetFragment.FilterSelectionListener {
            override fun onFilterSelected(selectedIds: Set<String>) {
                selectedIds.firstOrNull()?.let { id ->
                    globalSearchViewModel.updateDateFilterById(id)
                }
            }
        }

        bottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
    }

    private fun showSizeFilterBottomSheet() {
        val items = SizeFilter.entries.map { filter ->
            FilterItem(
                id = filter.id,
                label = getString(filter.labelResId),
                iconResId = filter.iconResId
            )
        }

        val bottomSheet = FilterBottomSheetFragment.newInstance(
            title = getString(R.string.homecloud_global_search_filter_size),
            items = items,
            selectedIds = setOf(globalSearchViewModel.getFiltersState().sizeFilter.id),
            isMultiSelect = false
        )

        bottomSheet.filterSelectionListener = object : FilterBottomSheetFragment.FilterSelectionListener {
            override fun onFilterSelected(selectedIds: Set<String>) {
                selectedIds.firstOrNull()?.let { id ->
                    globalSearchViewModel.updateSizeFilterById(id)
                }
            }
        }

        bottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
    }

    private fun showTagFilterBottomSheet(tags: List<OCTag>) {
        if (tags.isEmpty()) return

        val items = tags.map { tag ->
            FilterItem(
                id = tag.localId.toString(),
                label = tag.displayName.orEmpty(),
                iconResId = null,
            )
        }

        val bottomSheet = FilterBottomSheetFragment.newInstance(
            title = getString(R.string.homecloud_global_search_filter_tags),
            items = items,
            selectedIds = globalSearchViewModel.getFiltersState().selectedTags.map { it.localId.toString() }.toSet(),
            isMultiSelect = true,
            searchHint = getString(R.string.tags_search_hint)
        )

        bottomSheet.filterSelectionListener = object : FilterBottomSheetFragment.FilterSelectionListener {
            override fun onFilterSelected(selectedIds: Set<String>) {
                globalSearchViewModel.updateTagFilters(selectedIds.mapNotNull { it.toLongOrNull() }.toSet())
            }
        }

        bottomSheet.show(childFragmentManager, FilterBottomSheetFragment.TAG)
    }

    private fun updateFilterButtonsUI(filtersState: SearchFiltersState) {
        val filterTypeButton = binding.searchFilters.filterTypeButton
        val filterDateButton = binding.searchFilters.filterDateButton
        val filterSizeButton = binding.searchFilters.filterSizeButton

        filterTypeButton.apply {
            val selectedCount = filtersState.selectedTypes.size
            text = when (selectedCount) {
                0 -> getString(R.string.homecloud_global_search_filter_type)
                1 -> {
                    val typeFilter = filtersState.selectedTypes.firstOrNull()
                    typeFilter?.let { getString(it.labelResId) } ?: getString(R.string.homecloud_global_search_filter_type)
                }

                else -> getString(R.string.homecloud_global_search_filter_type_counter, selectedCount)
            }
            isSelected = selectedCount > 0
        }

        filterDateButton.apply {
            text = if (filtersState.dateFilter == DateFilter.ANY) {
                getString(R.string.homecloud_global_search_filter_date)
            } else {
                getString(filtersState.dateFilter.labelResId)
            }
            isSelected = filtersState.dateFilter != DateFilter.ANY
        }

        filterSizeButton.apply {
            text = if (filtersState.sizeFilter == SizeFilter.ANY) {
                getString(R.string.homecloud_global_search_filter_size)
            } else {
                getString(filtersState.sizeFilter.labelResId)
            }
            isSelected = filtersState.sizeFilter != SizeFilter.ANY
        }

        binding.searchFilters.filterTagsButton.apply {
            val selectedCount = filtersState.selectedTags.size
            text = when (selectedCount) {
                0 -> getString(R.string.homecloud_global_search_filter_tags)
                1 -> {
                    val tag = filtersState.selectedTags.find { it.localId == filtersState.selectedTags.firstOrNull()?.localId }
                    tag?.displayName?.takeIf { it.isNotEmpty() } ?: getString(R.string.homecloud_global_search_filter_tags)
                }

                else -> getString(R.string.homecloud_global_search_filter_tags_counter, selectedCount)
            }
            isSelected = selectedCount > 0
        }
    }

    fun updateSearchQuery(query: String) {
        globalSearchViewModel.updateSearchQuery(query)
    }

    private fun subscribeToViewModels() {
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

        collectLatestLifecycleFlow(globalSearchViewModel.filtersState) { filtersState ->
            updateFilterButtonsUI(filtersState)
        }

        collectLatestLifecycleFlow(globalSearchViewModel.tagsLoading) { isLoading ->
            val activity = requireActivity() as? BaseActivity
            if (isLoading) {
                activity?.showLoadingDialog(R.string.common_loading)
            } else {
                activity?.dismissLoadingDialog()
            }
        }

        collectLatestLifecycleFlow(globalSearchViewModel.openTagsBottomSheetEvent) { tags ->
            showTagFilterBottomSheet(tags)
        }

        collectLatestLifecycleFlow(globalSearchViewModel.scrollToTopEvents) {
            scrollFileListToTop()
        }
    }

    private fun scrollFileListToTop() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (globalSearchViewModel.composeUiState.value.layoutMode) {
                FileListLayoutMode.List -> listScrollState.scrollToItem(0)
                FileListLayoutMode.Grid -> gridScrollState.scrollToItem(0)
            }
        }
    }

    private fun setDrawerStatus(enabled: Boolean) {
        (activity as? FileActivity)?.setDrawerLockMode(
            if (enabled) DrawerLayout.LOCK_MODE_UNLOCKED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED
        )
    }

    private fun getCheckedItems(): List<OCFileWithSyncInfo> =
        globalSearchViewModel.composeUiState.value.checkedItems()

    private fun findFileWithSyncInfo(fileId: Long): OCFileWithSyncInfo? =
        globalSearchViewModel.composeUiState.value.findFile(fileId)

    private fun toggleSelection(fileId: Long) {
        globalSearchViewModel.toggleSelection(fileId)
        updateActionModeAfterTogglingSelected()
    }

    private fun updateActionModeAfterTogglingSelected() {
        val selectedItems = globalSearchViewModel.composeUiState.value.selectedItemCount
        if (selectedItems == 0) {
            actionMode?.finish()
        } else {
            if (actionMode == null) {
                actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
            }
            actionMode?.invalidate()
        }
    }

    private fun disableSelectionMode() {
        globalSearchViewModel.clearSelection()
        updateActionModeAfterTogglingSelected()
    }

    private fun onComposeItemClick(fileId: Long) {
        val ocFileWithSyncInfo = findFileWithSyncInfo(fileId) ?: return
        val file = ocFileWithSyncInfo.file

        if (file.isVirtualFile()) return

        if (actionMode != null) {
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

        if (actionMode == null) {
            actionMode = (requireActivity() as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }
        toggleSelection(fileId)
    }

    private fun onComposeThreeDotClick(fileId: Long) {
        val file = findFileWithSyncInfo(fileId)?.file ?: return
        (requireActivity() as? MainFileListFragment.FileActions)?.showDetails(file)
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
                globalSearchViewModel.selectAll()
                updateActionModeAfterTogglingSelected()
                true
            }

            R.id.action_select_inverse -> {
                globalSearchViewModel.selectInverse()
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

    override fun onSortTypeListener(sortType: SortType, sortOrder: SortOrder) {
        val sortBottomSheetFragment = SortBottomSheetFragment.newInstance(sortType, sortOrder)
        sortBottomSheetFragment.sortDialogListener = this
        sortBottomSheetFragment.show(childFragmentManager, SortBottomSheetFragment.TAG)
    }

    override fun onViewTypeListener(viewType: ViewType) {
        binding.optionsLayout.viewTypeSelected = viewType

        if (viewType == ViewType.VIEW_TYPE_LIST) {
            globalSearchViewModel.setListModeAsPreferred()
        } else {
            globalSearchViewModel.setGridModeAsPreferred()
            globalSearchViewModel.updateGridColumns(
                ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root)
            )
        }
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
