package com.owncloud.android.presentation.trash

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.owncloud.android.R
import com.owncloud.android.databinding.TrashFragmentBinding
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.presentation.files.ViewType
import com.owncloud.android.presentation.files.filelist.ColumnQuantity
import org.koin.androidx.viewmodel.ext.android.viewModel

class TrashFragment : Fragment(), TrashListAdapter.TrashListAdapterListener {

    private var _binding: TrashFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val trashViewModel: TrashViewModel by viewModel()

    private var toolbarListener: TrashToolbarListener? = null

    private val layoutManager: StaggeredGridLayoutManager by lazy {
        if (trashViewModel.isGridModeSetAsPreferred()) {
            StaggeredGridLayoutManager(
                ColumnQuantity(requireContext(), R.layout.grid_item).calculateNoOfColumns(binding.root),
                RecyclerView.VERTICAL,
            )
        } else {
            StaggeredGridLayoutManager(1, RecyclerView.VERTICAL)
        }
    }

    private val trashListAdapter: TrashListAdapter by lazy {
        TrashListAdapter(
            context = requireContext(),
            layoutManager = layoutManager,
            listener = this,
            isSelected = trashViewModel::isSelected,
        )
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        toolbarListener = context as? TrashToolbarListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TrashFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerViewTrash.apply {
            layoutManager = this@TrashFragment.layoutManager
            adapter = trashListAdapter
        }

        binding.trashSelectAllRow.setOnClickListener { toggleSelectAll() }
        binding.trashSelectAllCheckbox.setOnClickListener { toggleSelectAll() }

        binding.swipeRefreshTrash.setOnRefreshListener {
            trashViewModel.loadTrash()
        }

        collectLatestLifecycleFlow(trashViewModel.trashUiState) { state ->
            when (state) {
                is TrashViewModel.TrashUiState.Loading -> showLoading()
                is TrashViewModel.TrashUiState.Success -> showResults(state.items)
                is TrashViewModel.TrashUiState.Empty -> showEmptyState()
                is TrashViewModel.TrashUiState.NotSupported -> showNotSupported()
                is TrashViewModel.TrashUiState.Error -> showEmptyState()
            }
        }

        collectLatestLifecycleFlow(trashViewModel.selectedPositions) {
            updateSelectionUi(it)
        }

        toolbarListener?.onViewTypeChanged(trashViewModel.getCurrentViewType())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun toggleViewType() {
        if (layoutManager.spanCount == 1) {
            trashViewModel.setGridModeAsPreferred()
            layoutManager.spanCount = ColumnQuantity(requireContext(), R.layout.grid_item)
                .calculateNoOfColumns(binding.root)
        } else {
            trashViewModel.setListModeAsPreferred()
            layoutManager.spanCount = 1
        }
        trashListAdapter.notifyDataSetChanged()
        toolbarListener?.onViewTypeChanged(trashViewModel.getCurrentViewType())
    }

    fun clearSelection() {
        if (trashViewModel.hasSelection()) {
            trashViewModel.clearSelection()
        }
    }

    fun hasSelection(): Boolean = trashViewModel.hasSelection()

    override fun onItemClick(position: Int) {
        trashViewModel.toggleSelection(position)
    }

    private fun toggleSelectAll() {
        trashViewModel.toggleSelectAll()
    }

    private fun updateSelectionUi(selectedPositions: Set<Int>) {
        val itemCount = trashViewModel.itemCount
        val selectedCount = selectedPositions.size
        binding.trashSelectAllCheckbox.setImageResource(
            if (trashViewModel.isAllSelected) R.drawable.ic_checkbox_marked else R.drawable.ic_checkbox_blank_outline,
        )
        toolbarListener?.onSelectionChanged(itemCount, selectedCount)
        trashListAdapter.notifyDataSetChanged()
    }

    private fun showLoading() {
        binding.swipeRefreshTrash.isRefreshing = true
        binding.swipeRefreshTrash.isVisible = true
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = false
        binding.trashSelectAllRow.isVisible = false
        binding.trashInfoBanner.isVisible = true
    }

    private fun showResults(items: List<HCTrashItem>) {
        binding.swipeRefreshTrash.isRefreshing = false
        binding.swipeRefreshTrash.isVisible = true
        binding.recyclerViewTrash.isVisible = true
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = false
        binding.trashInfoBanner.isVisible = true
        binding.trashSelectAllRow.isVisible = true
        trashListAdapter.updateItems(items)
        updateSelectionUi(trashViewModel.selectedPositions.value)
    }

    private fun showEmptyState() {
        binding.swipeRefreshTrash.isRefreshing = false
        binding.swipeRefreshTrash.isVisible = true
        binding.recyclerViewTrash.isVisible = false
        binding.trashListEmpty.root.isVisible = true
        binding.trashNotSupported.isVisible = false
        binding.trashInfoBanner.isVisible = true
        binding.trashSelectAllRow.isVisible = false
        binding.trashListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_action_delete_grey)
        binding.trashListEmpty.listEmptyDatasetTitle.textSize = 20f
        binding.trashListEmpty.listEmptyDatasetTitle.setTypeface(null, Typeface.NORMAL)
        binding.trashListEmpty.listEmptyDatasetTitle.setText(R.string.trash_empty_title)
        binding.trashListEmpty.listEmptyDatasetSubTitle.setText(R.string.trash_empty_subtitle)
        updateSelectionUi(trashViewModel.selectedPositions.value)
    }

    private fun showNotSupported() {
        binding.swipeRefreshTrash.isRefreshing = false
        binding.swipeRefreshTrash.isVisible = false
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = true
        binding.trashInfoBanner.isVisible = false
        binding.trashSelectAllRow.isVisible = false
        updateSelectionUi(trashViewModel.selectedPositions.value)
    }

    interface TrashToolbarListener {
        fun onSelectionChanged(itemCount: Int, selectedCount: Int)
        fun onViewTypeChanged(viewType: ViewType)
    }

    companion object {
        fun newInstance(): TrashFragment = TrashFragment()
    }
}
