package com.owncloud.android.presentation.tags

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import com.owncloud.android.databinding.TagsFragmentBinding
import com.owncloud.android.domain.tags.model.OCTag
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.presentation.authentication.AccountUtils
import org.koin.androidx.viewmodel.ext.android.viewModel

class TagsFragment : Fragment(), TagsAdapter.TagsAdapterListener, TagDialogFragment.TagDialogListener {

    private var _binding: TagsFragmentBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private val tagsViewModel: TagsViewModel by viewModel()

    private val tagsAdapter: TagsAdapter by lazy {
        TagsAdapter(listener = this)
    }

    private var lastCreatedTagName: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TagsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        initViews()
        subscribeToViewModels()

        val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
        accountName?.let { tagsViewModel.loadTags(it) }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.tags_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_tag -> {
                TagDialogFragment.newAddInstance(this, existingTagNames())
                    .show(parentFragmentManager, TagDialogFragment.TAG_DIALOG_FRAGMENT)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initViews() {
        binding.recyclerViewTags.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewTags.adapter = tagsAdapter
        binding.recyclerViewTags.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))

        binding.swipeRefreshTags.setOnRefreshListener {
            val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name
            accountName?.let { tagsViewModel.loadTags(it) }
        }
    }

    private fun subscribeToViewModels() {
        collectLatestLifecycleFlow(tagsViewModel.tagsUiState) { uiState ->
            when (uiState) {
                is TagsViewModel.TagsUiState.Loading -> showLoading()
                is TagsViewModel.TagsUiState.Success -> showResults(uiState.tags)
                is TagsViewModel.TagsUiState.Empty -> showEmptyState()
                is TagsViewModel.TagsUiState.Error -> showEmptyState()
            }
        }
    }

    private fun showLoading() {
        binding.swipeRefreshTags.isRefreshing = true
        binding.tagsListEmpty.root.isVisible = false
    }

    private fun showResults(tags: List<OCTag>) {
        binding.swipeRefreshTags.isRefreshing = false
        binding.swipeRefreshTags.isVisible = true
        binding.tagsListEmpty.root.isVisible = false
        val createdTagName = lastCreatedTagName
        tagsAdapter.submitList(tags) {
            if (createdTagName != null) {
                lastCreatedTagName = null
                val position = tags.indexOfFirst { it.displayName == createdTagName }
                if (position >= 0) binding.recyclerViewTags.scrollToPosition(position)
            }
        }
    }

    private fun showEmptyState() {
        binding.swipeRefreshTags.isRefreshing = false
        binding.tagsListEmpty.root.isVisible = true
        binding.tagsListEmpty.listEmptyDatasetIcon.setImageResource(R.drawable.ic_tag_big)
        binding.tagsListEmpty.listEmptyDatasetTitle.textSize = 20f
        binding.tagsListEmpty.listEmptyDatasetTitle.setTypeface(null, Typeface.NORMAL)
        binding.tagsListEmpty.listEmptyDatasetTitle.setText(R.string.tags_empty_title)
        binding.tagsListEmpty.listEmptyDatasetSubTitle.isVisible = false
    }

    override fun onTagClick(tag: OCTag) {
        val tagId = tag.id ?: return
        val tagsActivity = requireActivity() as? TagsActivity
        tagsActivity?.showTagFiles(tagId, tag.displayName.orEmpty())
    }

    override fun onEditTag(tag: OCTag) {
        val tagId = tag.id ?: return
        val otherNames = existingTagNames().filter { !it.equals(tag.displayName.orEmpty(), ignoreCase = true) }
        TagDialogFragment.newEditInstance(tagId, tag.displayName.orEmpty(), this, otherNames)
            .show(parentFragmentManager, TagDialogFragment.TAG_DIALOG_FRAGMENT)
    }

    private fun existingTagNames(): List<String> =
        tagsAdapter.currentList.mapNotNull { it.displayName }

    override fun onTagNameSet(tagName: String, tagId: String?) {
        val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name ?: return
        if (tagId != null) {
            tagsViewModel.updateTag(accountName, tagId, tagName)
        } else {
            lastCreatedTagName = tagName
            tagsViewModel.createTag(accountName, tagName)
        }
    }

    override fun onDeleteTag(tag: OCTag) {
        val accountName = AccountUtils.getCurrentOwnCloudAccount(requireContext())?.name ?: return
        val tagId = tag.id ?: return

        val tagName = " \"${tag.displayName.orEmpty()}\" "
        val message = SpannableStringBuilder().apply {
            append(getString(R.string.tags_remove_confirmation_prefix))
            val start = length
            append(tagName)
            setSpan(StyleSpan(Typeface.BOLD), start, length, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE)
            append(getString(R.string.tags_remove_confirmation_suffix))
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.tags_remove_title))
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.tags_remove_button) { _, _ ->
                tagsViewModel.deleteTag(accountName, tagId)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): TagsFragment = TagsFragment()
    }
}
