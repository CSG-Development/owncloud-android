package com.owncloud.android.presentation.tags

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.chip.Chip
import com.google.android.material.shape.CornerFamily
import com.owncloud.android.MainApp
import com.owncloud.android.R
import com.owncloud.android.databinding.FragmentManageTagsBinding
import com.owncloud.android.datamodel.ThumbnailsCacheManager
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.extensions.showErrorInSnackbar
import com.owncloud.android.presentation.authentication.AccountUtils
import com.owncloud.android.presentation.tags.ManageTagsViewModel.ManageTagsUiState
import com.owncloud.android.ui.custom.FilterableAutoCompleteTextView
import com.owncloud.android.ui.fragment.FileFragment
import com.owncloud.android.utils.MimetypeIconUtil
import org.koin.androidx.viewmodel.ext.android.viewModel
import timber.log.Timber

class ManageTagsFragment : FileFragment() {

    private var _binding: FragmentManageTagsBinding? = null

    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val manageTagsViewModel: ManageTagsViewModel by viewModel()

    private var isExpanded = false
    private var overflowChips: List<Chip> = emptyList()
    private var toggleChip: Chip? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentManageTagsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        file = requireArguments().getParcelable(ARG_FILE) ?: throw IllegalArgumentException("File is required in arguments!")

        requireActivity().title = getString(R.string.manage_tags_option)
        setHasOptionsMenu(true)

        setupFileName()
        setupImagePreview()
        setupTagSearch()
        setupSelectedTags()
        observeErrors()
        manageTagsViewModel.loadTagsForFile(file.owner, file.fileId ?: 0L, file.id ?: 0L)
    }

    override fun updateViewForSyncInProgress() {}
    override fun updateViewForSyncOff() {}
    override fun onFileMetadataChanged(updatedFile: OCFile?) {}
    override fun onFileMetadataChanged() {}
    override fun onFileContentChanged() {}

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.manage_tags_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_close_manage_tags -> {
                requireActivity().onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFileName() {
        binding.fileNameTitle.text = file.fileName
    }

    private fun setupImagePreview() {
        val imageView = binding.imagePreview
        if (file.isImage) {
            val tagId = file.remoteId.toString()
            var thumbnail: Bitmap? = ThumbnailsCacheManager.getBitmapFromDiskCache(tagId)
            if (thumbnail != null && !file.needsToUpdateThumbnail) {
                imageView.setImageBitmap(thumbnail)
            } else {
                val account = AccountUtils.getAccounts(requireContext()).firstOrNull { it.name == file.owner }
                if (account != null && ThumbnailsCacheManager.cancelPotentialThumbnailWork(file, imageView)) {
                    val task = ThumbnailsCacheManager.ThumbnailGenerationTask(imageView, account)
                    if (thumbnail == null) thumbnail = ThumbnailsCacheManager.mDefaultImg
                    val asyncDrawable = ThumbnailsCacheManager.AsyncThumbnailDrawable(
                        MainApp.appContext.resources, thumbnail, task
                    )
                    imageView.setImageDrawable(asyncDrawable)
                    task.execute(file)
                } else {
                    imageView.setImageResource(MimetypeIconUtil.getFileTypeIconId(file.mimeType, file.fileName))
                }
            }
        } else {
            imageView.setImageResource(MimetypeIconUtil.getFileTypeIconId(file.mimeType, file.fileName))
        }
    }

    private fun setupTagSearch() {
        manageTagsViewModel.loadAllTagsForAccount(file.owner)

        collectLatestLifecycleFlow(manageTagsViewModel.allTagsUiState) { _ ->
            updateTagDropdown()
        }

        binding.tagSearchEditText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                updateTagDropdown()
                binding.tagSearchEditText.showDropDown()
            }
        }

        binding.tagSearchEditText.addTextChangedListener {
            updateTagDropdown()
            binding.tagSearchEditText.showDropDown()
        }

        binding.tagSearchEditText.setOnItemSelectedListener { item ->
            Timber.d("Selected item: $item")
            manageTagsViewModel.assignTagToFile(file.owner, file.id ?: 0L, file.fileId ?: 0L, item.id)
        }
        binding.tagSearchEditText.setOnAddItemClickListener { item ->
            Timber.d("Added item: $item")
            manageTagsViewModel.createTagAndAssignToFile(file.owner, file.id ?: 0L, file.fileId ?: 0L, item)
        }
    }

    private fun observeErrors() {
        collectLatestLifecycleFlow(manageTagsViewModel.errorEvent) { throwable ->
            showErrorInSnackbar(R.string.manage_tags_operation_error, throwable)
        }
    }

    private fun setupSelectedTags() {
        collectLatestLifecycleFlow(manageTagsViewModel.uiState) { state ->
            when (state) {
                is ManageTagsUiState.Loading -> showManageTagsLoading()
                is ManageTagsUiState.Success -> {
                    if (state.tags.isEmpty()) {
                        showManageTagsEmpty()
                    } else {
                        showManageTagsContent(state.tags)
                    }
                }

                is ManageTagsUiState.Error -> showManageTagsEmpty()
            }
        }
    }

    private fun showManageTagsLoading() {
        binding.manageTagsLoading.isVisible = true
        binding.selectedTagsScroll.isVisible = false
        binding.manageTagsEmpty.root.isVisible = false
    }

    private fun showManageTagsEmpty() {
        binding.manageTagsLoading.isVisible = false
        binding.selectedTagsScroll.isVisible = false
        binding.manageTagsEmpty.root.isVisible = true
        binding.selectedTagsChipGroup.removeAllViews()
        isExpanded = false
        overflowChips = emptyList()
        toggleChip = null

        binding.manageTagsEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_tag_big)
        binding.manageTagsEmpty.listEmptyDatasetTitle.textSize = 20f
        binding.manageTagsEmpty.listEmptyDatasetTitle.setTypeface(null, Typeface.NORMAL)
        binding.manageTagsEmpty.listEmptyDatasetTitle.setText(R.string.manage_tags_empty_title)
        binding.manageTagsEmpty.listEmptyDatasetSubTitle.isVisible = false
        updateTagDropdown()
    }

    private fun showManageTagsContent(tags: List<OCTag>) {
        binding.manageTagsLoading.isVisible = false
        binding.manageTagsEmpty.root.isVisible = false
        binding.selectedTagsScroll.isVisible = true
        renderTags(tags)
    }

    private fun renderTags(tags: List<OCTag>) {
        binding.selectedTagsChipGroup.removeAllViews()
        isExpanded = false
        overflowChips = emptyList()
        toggleChip = null

        tags.forEach { tag ->
            val chip = createChip(
                text = tag.displayName.orEmpty(),
                closeIconVisible = true,
                closeButtonClick = {
                    tag.id?.let { tagId ->
                        manageTagsViewModel.removeTagFromFile(file.owner, file.id ?: 0L, file.fileId ?: 0L, tagId)
                    }
                }
            )
            binding.selectedTagsChipGroup.addView(chip)
        }

        if (tags.isNotEmpty()) {
            binding.selectedTagsChipGroup.viewTreeObserver.addOnGlobalLayoutListener(
                object : ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (!binding.selectedTagsChipGroup.viewTreeObserver.isAlive) return
                        binding.selectedTagsChipGroup.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        setupExpandCollapse()
                    }
                }
            )
        }
        updateTagDropdown()
    }

    private fun setupExpandCollapse() {
        val chipGroup = binding.selectedTagsChipGroup
        val tagChips = chipGroup.children.toList().filterIsInstance<Chip>()
        if (tagChips.isEmpty()) return

        val rowHeight = tagChips.first().height
        if (rowHeight == 0 || chipGroup.height <= rowHeight * MAX_VISIBLE_ROWS) return

        val threshold = rowHeight * MAX_VISIBLE_ROWS
        overflowChips = tagChips.filter { it.top >= threshold }
        if (overflowChips.isEmpty()) return

        overflowChips.forEach { it.isVisible = false }

        val more = createChip(
            text = getString(R.string.manage_tags_more, overflowChips.size),
            closeIconVisible = false,
            chipClick = { toggleExpand() }
        )
        chipGroup.addView(more)
        toggleChip = more
    }

    private fun createChip(
        text: String,
        closeIconVisible: Boolean,
        chipClick: () -> Unit = {},
        closeButtonClick: () -> Unit = {},
    ): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            this.isCloseIconVisible = closeIconVisible
            if (closeIconVisible) {
                setCloseIconTintResource(R.color.homecloud_tag_content)
            }
            setTextColor(resources.getColor(R.color.homecloud_tag_content, null))
            setEnsureMinTouchTargetSize(false)
            setChipBackgroundColorResource(R.color.homecloud_tag_background)
            shapeAppearanceModel = shapeAppearanceModel
                .toBuilder()
                .setAllCorners(CornerFamily.ROUNDED, 32f * resources.displayMetrics.density)
                .build()
            this.setOnCloseIconClickListener {
                closeButtonClick()
            }
            this.setOnClickListener {
                chipClick()
            }
        }
    }

    private fun toggleExpand() {
        isExpanded = !isExpanded
        overflowChips.forEach { it.isVisible = isExpanded }
        toggleChip?.text = if (isExpanded) {
            getString(R.string.manage_tags_show_less)
        } else {
            getString(R.string.manage_tags_more, overflowChips.size)
        }
    }

    private fun updateTagDropdown() {
        val query = binding.tagSearchEditText.text?.toString()?.trim().orEmpty()
        val appliedTagIds = (manageTagsViewModel.uiState.value as? ManageTagsUiState.Success)
            ?.tags?.mapNotNull { it.id }?.toSet() ?: emptySet()

        val allTagsState = manageTagsViewModel.allTagsUiState.value
        val available = (allTagsState as? ManageTagsViewModel.AllTagsUiState.Success)
            ?.tags
            ?.filter { it.id != null }
            ?.filter { it.id !in appliedTagIds }
            ?.filter { tag -> query.isEmpty() || tag.displayName?.contains(query, ignoreCase = true) == true }
            ?: emptyList()

        val items: List<FilterableAutoCompleteTextView.DropdownItem<OCTag?>> = when {
            available.isNotEmpty() -> available.map { tag ->
                FilterableAutoCompleteTextView.DropdownItem(
                    id = tag.id.orEmpty(),
                    text = tag.displayName.orEmpty(),
                    data = tag
                )
            }

            query.isNotEmpty() -> listOf(
                FilterableAutoCompleteTextView.DropdownItem(
                    id = FilterableAutoCompleteTextView.ITEM_ID_ADD,
                    text = getString(R.string.manage_tags_add_tag, query),
                    data = null
                )
            )

            else -> listOf(
                FilterableAutoCompleteTextView.DropdownItem(
                    id = FilterableAutoCompleteTextView.ITEM_ID_EMPTY_STATE,
                    text = getString(R.string.manage_tags_no_tags_available),
                    data = null
                )
            )
        }

        binding.tagSearchEditText.setDropdownItems(items)
//        if (binding.tagSearchEditText.hasFocus()) {
//            binding.tagSearchEditText.showDropDown()
//        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_FILE = "arg_file"
        private const val MAX_VISIBLE_ROWS = 4

        fun newInstance(file: OCFile): ManageTagsFragment =
            ManageTagsFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_FILE, file)
                }
            }
    }
}
