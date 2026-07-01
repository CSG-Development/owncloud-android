package com.owncloud.android.presentation.trash

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import com.owncloud.android.databinding.TrashFragmentBinding
import com.owncloud.android.domain.trash.model.HCTrashItem
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.extensions.showErrorInSnackbar
import com.owncloud.android.extensions.showMessageInSnackbar
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

        binding.trashActionRestore.setOnClickListener {
            if (binding.trashActionRestore.isEnabled) {
                // TODO: restore action
            }
        }
        binding.trashActionDelete.setOnClickListener {
            if (binding.trashActionDelete.isEnabled) {
                showDeleteConfirmationDialog()
            }
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

        collectLatestLifecycleFlow(trashViewModel.deleteEvent) { event ->
            when (event) {
                is TrashViewModel.TrashDeleteEvent.Success -> {
                    showMessageInSnackbar(
                        resources.getQuantityString(
                            R.plurals.homecloud_trash_delete_success,
                            event.deletedCount,
                            event.deletedCount,
                        ),
                    )
                }
                is TrashViewModel.TrashDeleteEvent.Error -> {
                    showErrorInSnackbar(R.string.homecloud_trash_delete_error, event.throwable)
                }
            }
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

    private fun updateSelectionUi(items: List<TrashItemUi>) {
        val itemCount = items.size
        val selectedCount = items.count { it.isSelected }
        binding.trashSelectAllCheckbox.setImageResource(
            if (itemCount > 0 && selectedCount == itemCount) {
                R.drawable.ic_checkbox_marked
            } else {
                R.drawable.ic_checkbox_blank_outline
            },
        )
        toolbarListener?.onSelectionChanged(itemCount, selectedCount)
        trashListAdapter.notifyDataSetChanged()
        updateBottomActionBar(selectedCount)
    }

    private fun updateBottomActionBar(selectedCount: Int) {
        val hasItems = trashViewModel.itemCount > 0
        val isSuccessState = trashViewModel.trashUiState.value is TrashViewModel.TrashUiState.Success
        val visible = hasItems && isSuccessState
        val enabled = selectedCount > 0

        binding.trashBottomActionBar.isVisible = visible
        binding.trashActionRestore.isEnabled = enabled
        binding.trashActionDelete.isEnabled = enabled
    }

    private fun showLoading() {
        binding.swipeRefreshTrash.isRefreshing = true
        binding.swipeRefreshTrash.isVisible = true
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = false
        binding.trashSelectAllRow.isVisible = false
        binding.trashInfoBanner.isVisible = true
        updateBottomActionBar(selectedCount = 0)
    }

    private fun showResults(items: List<TrashItemUi>) {
        binding.swipeRefreshTrash.isRefreshing = false
        binding.swipeRefreshTrash.isVisible = true
        binding.recyclerViewTrash.isVisible = true
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = false
        binding.trashInfoBanner.isVisible = true
        binding.trashSelectAllRow.isVisible = true
        trashListAdapter.updateItems(items)
        updateSelectionUi(items)
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
        updateBottomActionBar(selectedCount = 0)
    }

    private fun showNotSupported() {
        binding.swipeRefreshTrash.isRefreshing = false
        binding.swipeRefreshTrash.isVisible = false
        binding.trashListEmpty.root.isVisible = false
        binding.trashNotSupported.isVisible = true
        binding.trashInfoBanner.isVisible = false
        binding.trashSelectAllRow.isVisible = false
        updateBottomActionBar(selectedCount = 0)
    }

    private fun showDeleteConfirmationDialog() {
        val selectedItems = trashViewModel.getSelectedItems()
        if (selectedItems.isEmpty()) return

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getDeleteConfirmTitle(selectedItems))
            .setMessage(R.string.homecloud_trash_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.trash_action_delete) { _, _ ->
                trashViewModel.deleteSelectedItems()
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(ContextCompat.getColor(requireContext(), R.color.homecloud_error))
    }

    private fun getDeleteConfirmTitle(selectedItems: List<HCTrashItem>): String =
        resources.getQuantityString(
            R.plurals.homecloud_trash_delete_confirm_title_files,
            selectedItems.size,
            selectedItems.size,
        )

    interface TrashToolbarListener {
        fun onSelectionChanged(itemCount: Int, selectedCount: Int)
        fun onViewTypeChanged(viewType: ViewType)
    }

    companion object {
        fun newInstance(): TrashFragment = TrashFragment()
    }
}
