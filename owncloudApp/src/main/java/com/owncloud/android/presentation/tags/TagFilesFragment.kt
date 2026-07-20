package com.owncloud.android.presentation.tags

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.owncloud.android.R
import com.owncloud.android.databinding.TagFilesFragmentBinding
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.common.FileOptionsBottomSheetHelper
import com.owncloud.android.presentation.common.UIResult
import com.owncloud.android.presentation.files.SortBottomSheetFragment
import com.owncloud.android.presentation.files.SortOptionsView
import com.owncloud.android.presentation.files.SortOrder
import com.owncloud.android.presentation.files.SortType
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.ColumnQuantity
import com.owncloud.android.presentation.files.filelist.MainFileListFragment
import com.owncloud.android.presentation.files.filelist.compose.FileListLayoutMode
import com.owncloud.android.presentation.files.filelist.compose.setFileListContent
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment.Companion.TAG_REMOVE_FILES_DIALOG_FRAGMENT
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class TagFilesFragment : Fragment(),
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: TagFilesFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val tagFilesViewModel: TagFilesViewModel by viewModel()
    private val fileOperationsViewModel: FileOperationsViewModel by activityViewModel()

    private var fileSingleFile: OCFileWithSyncInfo? = null
    private var filesToRemove: List<OCFile> = emptyList()

    private val listScrollState = LazyListState()
    private val gridScrollState = LazyGridState()

    private val serverTagId: String by lazy { arguments?.getString(ARG_SERVER_TAG_ID).orEmpty() }
    val tagName: String by lazy { "“${arguments?.getString(ARG_TAG_NAME).orEmpty()}”" }

    private var accountName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TagFilesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        initViews()
        subscribeToViewModels()

        accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        accountName?.let { tagFilesViewModel.loadFiles(it, serverTagId) }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        menu.forEach { it.isVisible = false }
    }

    private fun initViews() {
        if (tagFilesViewModel.isGridModeSetAsPreferred()) {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_GRID
            tagFilesViewModel.setGridModeAsPreferred()
            tagFilesViewModel.updateGridColumns(
                ColumnQuantity(requireContext()).calculateNoOfColumns(binding.root)
            )
        } else {
            binding.optionsLayout.viewTypeSelected = ViewType.VIEW_TYPE_LIST
            tagFilesViewModel.setListModeAsPreferred()
        }
        binding.optionsLayout.sortTypeSelected = tagFilesViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = tagFilesViewModel.getSortOrder()
        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)

        setupComposeFileList()
    }

    private fun setupComposeFileList() {
        val account = AccountUtils.getCurrentOwnCloudAccount(requireContext())
        binding.composeViewTagFiles.setFileListContent(
            uiStateFlow = tagFilesViewModel.composeUiState,
            account = account,
            listState = listScrollState,
            gridState = gridScrollState,
            onItemClick = ::onComposeItemClick,
            onThreeDotClick = ::onComposeThreeDotClick,
            onRefresh = ::reloadFiles,
        )
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(tagFilesViewModel.menuOptionsSingleFile) { menuOptions ->
            fileSingleFile?.let { fileWithSyncInfo ->
                FileOptionsBottomSheetHelper.show(
                    fragment = this,
                    file = fileWithSyncInfo.file,
                    menuOptions = menuOptions,
                    fileOperationsViewModel = fileOperationsViewModel,
                    fileActions = requireActivity() as? MainFileListFragment.FileActions,
                    onRemoveSelected = { file -> filesToRemove = listOf(file) },
                )
                fileSingleFile = null
            }
        }
        fileOperationsViewModel.renameFileLiveData.observe(viewLifecycleOwner) { event ->
            if (event?.peekContent() is UIResult.Success) reloadFiles()
        }
        fileOperationsViewModel.moveFileLiveData.observe(viewLifecycleOwner) { event ->
            if (event?.peekContent() is UIResult.Success) reloadFiles()
        }
        fileOperationsViewModel.copyFileLiveData.observe(viewLifecycleOwner) { event ->
            if (event?.peekContent() is UIResult.Success) reloadFiles()
        }
        fileOperationsViewModel.removeFileLiveData.observe(viewLifecycleOwner) { event ->
            if (event?.peekContent() is UIResult.Success) reloadFiles()
        }
        collectLatestLifecycleFlow(fileOperationsViewModel.checkIfFileIsLocalAndNotAvailableOfflineSharedFlow) { uiResult ->
            val fileActivity = requireActivity() as FileActivity
            when (uiResult) {
                is UIResult.Loading -> fileActivity.showLoadingDialog(R.string.common_loading)
                is UIResult.Success -> {
                    fileActivity.dismissLoadingDialog()
                    uiResult.data?.let { isLocalAndNotAvailableOffline ->
                        RemoveFilesDialogFragment.newInstance(ArrayList(filesToRemove), isLocalAndNotAvailableOffline)
                            .show(requireActivity().supportFragmentManager, TAG_REMOVE_FILES_DIALOG_FRAGMENT)
                    }
                }
                is UIResult.Error -> {
                    fileActivity.dismissLoadingDialog()
                }
            }
        }
        collectLatestLifecycleFlow(tagFilesViewModel.scrollToTopEvents) {
            scrollFileListToTop()
        }
    }

    private fun scrollFileListToTop() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (tagFilesViewModel.composeUiState.value.layoutMode) {
                FileListLayoutMode.List -> listScrollState.scrollToItem(0)
                FileListLayoutMode.Grid -> gridScrollState.scrollToItem(0)
            }
        }
    }

    private fun reloadFiles() {
        accountName?.let { tagFilesViewModel.loadFiles(it, serverTagId) }
    }

    private fun findFileWithSyncInfo(fileId: Long): OCFileWithSyncInfo? =
        tagFilesViewModel.composeUiState.value.findFile(fileId)

    private fun onComposeItemClick(fileId: Long) {
        val file = findFileWithSyncInfo(fileId)?.file ?: return
        val fileDisplayActivity = requireActivity() as? FileDisplayActivity
        if (file.isFolder) {
            fileDisplayActivity?.startFolderPreview(file)
        } else {
            fileDisplayActivity?.onFileClicked(file)
        }
    }

    private fun onComposeThreeDotClick(fileId: Long) {
        val fileWithSyncInfo = findFileWithSyncInfo(fileId) ?: return
        fileSingleFile = fileWithSyncInfo
        tagFilesViewModel.filterMenuOptionsForSingleFile(fileWithSyncInfo)
    }

    override fun onSortTypeListener(sortType: SortType, sortOrder: SortOrder) {
        val sortBottomSheetFragment = SortBottomSheetFragment.newInstance(sortType, sortOrder)
        sortBottomSheetFragment.sortDialogListener = this
        sortBottomSheetFragment.show(childFragmentManager, SortBottomSheetFragment.TAG)
    }

    override fun onViewTypeListener(viewType: ViewType) {
        binding.optionsLayout.viewTypeSelected = viewType

        if (viewType == ViewType.VIEW_TYPE_LIST) {
            tagFilesViewModel.setListModeAsPreferred()
        } else {
            tagFilesViewModel.setGridModeAsPreferred()
            tagFilesViewModel.updateGridColumns(
                ColumnQuantity(requireContext()).calculateNoOfColumns(binding.root)
            )
        }
    }

    override fun onSortSelected(sortType: SortType) {
        binding.optionsLayout.sortTypeSelected = sortType
        tagFilesViewModel.updateSortTypeAndOrder(sortType, binding.optionsLayout.sortOrderSelected)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SERVER_TAG_ID = "ARG_SERVER_TAG_ID"
        private const val ARG_TAG_NAME = "ARG_TAG_NAME"

        fun newInstance(serverTagId: String, tagName: String): TagFilesFragment =
            TagFilesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SERVER_TAG_ID, serverTagId)
                    putString(ARG_TAG_NAME, tagName)
                }
            }
    }
}
