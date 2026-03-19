package com.owncloud.android.presentation.tags

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.owncloud.android.R
import com.owncloud.android.databinding.TagFilesFragmentBinding
import com.owncloud.android.domain.files.model.FileListOption
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
import com.owncloud.android.presentation.files.filelist.FileListAdapter
import com.owncloud.android.presentation.files.filelist.MainFileListFragment
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment.Companion.TAG_REMOVE_FILES_DIALOG_FRAGMENT
import com.owncloud.android.ui.activity.FileActivity
import com.owncloud.android.ui.activity.FileDisplayActivity
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class TagFilesFragment : Fragment(),
    FileListAdapter.FileListAdapterListener,
    SortBottomSheetFragment.SortDialogListener,
    SortOptionsView.SortOptionsListener {

    private var _binding: TagFilesFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val tagFilesViewModel: TagFilesViewModel by viewModel()
    private val fileOperationsViewModel: FileOperationsViewModel by activityViewModel()

    private var fileSingleFile: OCFileWithSyncInfo? = null
    private var filesToRemove: List<OCFile> = emptyList()

    private val layoutManager: StaggeredGridLayoutManager by lazy {
        if (tagFilesViewModel.isGridModeSetAsPreferred()) {
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
            isMultiPersonal = false,
        )
    }

    private var serverTagId: String = ""
    var tagName: String = ""
        private set
    private var accountName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serverTagId = arguments?.getString(ARG_SERVER_TAG_ID).orEmpty()
        tagName = arguments?.getString(ARG_TAG_NAME).orEmpty()
    }

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
        binding.optionsLayout.viewTypeSelected = if (tagFilesViewModel.isGridModeSetAsPreferred()) ViewType.VIEW_TYPE_GRID else ViewType.VIEW_TYPE_LIST
        binding.optionsLayout.sortTypeSelected = tagFilesViewModel.getSortType()
        binding.optionsLayout.sortOrderSelected = tagFilesViewModel.getSortOrder()

        binding.recyclerViewTagFiles.layoutManager = layoutManager
        binding.recyclerViewTagFiles.adapter = fileListAdapter

        binding.optionsLayout.onSortOptionsListener = this
        binding.optionsLayout.selectAdditionalView(SortOptionsView.AdditionalView.VIEW_TYPE)

        binding.swipeRefreshTagFiles.setOnRefreshListener { reloadFiles() }
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(tagFilesViewModel.uiState) { uiState ->
            when (uiState) {
                is TagFilesViewModel.TagFilesUiState.Loading -> showLoading()
                is TagFilesViewModel.TagFilesUiState.Success -> showResults(uiState.files)
                is TagFilesViewModel.TagFilesUiState.Empty -> showEmptyState()
                is TagFilesViewModel.TagFilesUiState.Error -> showEmptyState()
            }
        }
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
    }

    private fun reloadFiles() {
        accountName?.let { tagFilesViewModel.loadFiles(it, serverTagId) }
    }

    private fun showLoading() {
        binding.swipeRefreshTagFiles.isRefreshing = true
        binding.recyclerViewTagFiles.isVisible = false
        binding.tagFilesListEmpty.root.isVisible = false
    }

    private fun showResults(files: List<OCFileWithSyncInfo>) {
        binding.swipeRefreshTagFiles.isRefreshing = false
        binding.recyclerViewTagFiles.isVisible = true
        binding.tagFilesListEmpty.root.isVisible = false

        fileListAdapter.updateFileList(
            filesToAdd = files,
            fileListOption = FileListOption.TAG_FILES,
        ) {
            binding.recyclerViewTagFiles.post { binding.recyclerViewTagFiles.scrollToPosition(0) }
        }
    }

    private fun showEmptyState() {
        binding.swipeRefreshTagFiles.isRefreshing = false
        binding.recyclerViewTagFiles.isVisible = false
        binding.tagFilesListEmpty.root.isVisible = true
        binding.tagFilesListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_tag_big)
        binding.tagFilesListEmpty.listEmptyDatasetTitle.setText(R.string.tag_files_empty_title)
        binding.tagFilesListEmpty.listEmptyDatasetSubTitle.isVisible = false
    }

    override fun onItemClick(ocFileWithSyncInfo: OCFileWithSyncInfo, position: Int) {
        val file = ocFileWithSyncInfo.file
        val fileDisplayActivity = requireActivity() as? FileDisplayActivity
        if (file.isFolder) {
            fileDisplayActivity?.startFolderPreview(file)
        } else {
            fileDisplayActivity?.onFileClicked(file)
        }
    }

    override fun onLongItemClick(position: Int): Boolean = false

    override fun onThreeDotButtonClick(fileWithSyncInfo: OCFileWithSyncInfo) {
        fileSingleFile = fileWithSyncInfo
        tagFilesViewModel.filterMenuOptionsForSingleFile(fileWithSyncInfo)
    }

    override fun onSortTypeListener(sortType: SortType, sortOrder: SortOrder) {
        val sortBottomSheetFragment = SortBottomSheetFragment.newInstance(sortType, sortOrder)
        sortBottomSheetFragment.sortDialogListener = this
        sortBottomSheetFragment.show(childFragmentManager, SortBottomSheetFragment.TAG)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewTypeListener(viewType: ViewType) {
        binding.optionsLayout.viewTypeSelected = viewType

        if (viewType == ViewType.VIEW_TYPE_LIST) {
            tagFilesViewModel.setListModeAsPreferred()
            layoutManager.spanCount = 1
        } else {
            tagFilesViewModel.setGridModeAsPreferred()
            layoutManager.spanCount = ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root)
        }

        fileListAdapter.notifyDataSetChanged()
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
        const val TAG_TAG_FILES = "TAG_TAG_FILES"
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
