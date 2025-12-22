package com.owncloud.android.presentation.files.globalsearch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.owncloud.android.databinding.FilterBottomSheetFragmentBinding
import com.owncloud.android.utils.PreferenceUtils

class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FilterBottomSheetFragmentBinding? = null
    private val binding get() = _binding!!

    private val title: String by lazy { arguments?.getString(ARG_TITLE, "").orEmpty() }
    private val items: List<FilterItem> by lazy { arguments?.getParcelableArrayList(ARG_ITEMS) ?: emptyList() }
    private val selectedIds: Set<String> by lazy { arguments?.getStringArrayList(ARG_SELECTED_IDS)?.toSet() ?: emptySet() }
    private val isMultiSelect: Boolean by lazy { arguments?.getBoolean(ARG_IS_MULTI_SELECT, false) ?: false }

    var filterSelectionListener: FilterSelectionListener? = null

    private val adapter by lazy {
        FilterItemAdapter(
            items = items,
            selectedIds = selectedIds.toMutableSet(),
            isMultiSelect = isMultiSelect,
            onItemClick = { item, isSelected ->
                handleItemClick(item, isSelected)
            }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FilterBottomSheetFragmentBinding.inflate(inflater, container, false)
        return binding.root.apply {
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.filterTitle.text = title
        binding.filterItemsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@FilterBottomSheetFragment.adapter
        }

        if (isMultiSelect) {
            binding.applyButton.visibility = View.VISIBLE
            binding.applyButton.setOnClickListener {
                filterSelectionListener?.onFilterSelected(adapter.getSelectedIds())
                dismiss()
            }
        } else {
            binding.applyButton.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun handleItemClick(item: FilterItem, isSelected: Boolean) {
        if (!isMultiSelect) {
            filterSelectionListener?.onFilterSelected(setOf(item.id))
            dismiss()
        }
    }

    interface FilterSelectionListener {
        fun onFilterSelected(selectedIds: Set<String>)
    }

    companion object {
        const val TAG = "FilterBottomSheetFragment"
        private const val ARG_TITLE = "ARG_TITLE"
        private const val ARG_ITEMS = "ARG_ITEMS"
        private const val ARG_SELECTED_IDS = "ARG_SELECTED_IDS"
        private const val ARG_IS_MULTI_SELECT = "ARG_IS_MULTI_SELECT"

        fun newInstance(
            title: String,
            items: List<FilterItem>,
            selectedIds: Set<String> = emptySet(),
            isMultiSelect: Boolean = false
        ): FilterBottomSheetFragment {
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putParcelableArrayList(ARG_ITEMS, ArrayList(items))
                putStringArrayList(ARG_SELECTED_IDS, ArrayList(selectedIds))
                putBoolean(ARG_IS_MULTI_SELECT, isMultiSelect)
            }
            return FilterBottomSheetFragment().apply { arguments = args }
        }
    }
}


