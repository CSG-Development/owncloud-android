package com.owncloud.android.ui.preview

import android.accounts.Account
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import com.owncloud.android.R
import com.owncloud.android.databinding.PreviewPdfFragmentBinding
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.extensions.collectLatestLifecycleFlow
import com.owncloud.android.extensions.filterMenuOptions
import com.owncloud.android.extensions.goToUrl
import com.owncloud.android.extensions.sendDownloadedFilesByShareSheet
import com.owncloud.android.extensions.showFavoriteStatusSnackbar
import com.owncloud.android.presentation.files.operations.FileOperation
import com.owncloud.android.presentation.files.operations.FileOperationsViewModel
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment
import com.owncloud.android.presentation.files.removefile.RemoveFilesDialogFragment.Companion.TAG_REMOVE_FILES_DIALOG_FRAGMENT
import com.owncloud.android.presentation.previews.PreviewPdfViewModel
import com.owncloud.android.presentation.tags.TagsActivity
import com.owncloud.android.ui.fragment.FileFragment
import com.owncloud.android.utils.PreferenceUtils
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import java.util.Locale

class PreviewPdfFragment : FileFragment() {

    private var account: Account? = null
    private var latestMenuOptions: List<FileMenuOption> = emptyList()

    private val previewPdfViewModel by viewModel<PreviewPdfViewModel> {
        parametersOf(requireArguments().getParcelable(EXTRA_FILE))
    }
    private val fileOperationsViewModel by viewModel<FileOperationsViewModel>()

    private var _binding: PreviewPdfFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file: OCFile?
        if (savedInstanceState == null) {
            val args = requireArguments()
            file = args.getParcelable(EXTRA_FILE)
            account = args.getParcelable(EXTRA_ACCOUNT)
            if (file == null) {
                throw IllegalStateException("Instanced with a NULL OCFile")
            }
            if (account == null) {
                throw IllegalStateException("Instanced with a NULL ownCloud Account")
            }
        } else {
            file = savedInstanceState.getParcelable(EXTRA_FILE)
            account = savedInstanceState.getParcelable(EXTRA_ACCOUNT)
        }
        requireActivity().title = getString(R.string.homecloud_pdf_preview_label)
        setFile(file)
        setHasOptionsMenu(true)
        isOpen = true
        currentFilePreviewing = file
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = PreviewPdfFragmentBinding.inflate(inflater, container, false)
        return binding.root.apply {
            filterTouchesWhenObscured = PreferenceUtils.shouldDisallowTouchesWithOtherVisibleWindows(context)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.pdfViewer.listener = object : PdfViewer.Listener {
            override fun onLoadStateChanged(state: PdfLoadState) {
                when (state) {
                    PdfLoadState.Idle -> showPageNavigationBar(false)
                    PdfLoadState.Loading -> showPageNavigationBar(false)
                    is PdfLoadState.Ready -> {
                        binding.pdfViewer.isVisible = true
                        showPageNavigationBar(true)
                    }
                    PdfLoadState.Error -> {
                        binding.pdfViewer.isVisible = true
                        showPageNavigationBar(false)
                    }
                }
            }

            override fun onPageChanged(navigation: PdfPageNavigationState) {
                updatePageNavigationUi(navigation)
            }

            override fun onZoomChanged(zoom: PdfZoomState) {
                updateZoomUi(zoom)
            }

            override fun onLoadError() {
                showPreviewError()
            }

            override fun onExternalLinkClicked(uri: Uri) {
                openExternalPdfLink(uri)
            }
        }

        setupPageNavigationControls()
        setupZoomControls()

        collectLatestLifecycleFlow(previewPdfViewModel.getCurrentFile()) { currentFile ->
            if (currentFile != null) {
                file = currentFile
                requestFilterMenuOptions()
                requireActivity().invalidateOptionsMenu()
            } else {
                finish()
            }
        }

        collectLatestLifecycleFlow(previewPdfViewModel.menuOptions) { menuOptions ->
            latestMenuOptions = menuOptions
            requireActivity().invalidateOptionsMenu()
        }

        requestLoadPreview()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.apply {
            putParcelable(EXTRA_FILE, file)
            putParcelable(EXTRA_ACCOUNT, account)
        }
    }

    override fun onDestroyView() {
        binding.pdfViewer.listener = null
        _binding = null
        isOpen = false
        currentFilePreviewing = null
        super.onDestroyView()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_share_file -> {
                mContainerActivity.fileOperationsHelper.showShareFile(file)
                true
            }

            R.id.action_open_file_with -> {
                openFile()
                true
            }

            R.id.action_remove_file -> {
                RemoveFilesDialogFragment.newInstance(file).show(requireFragmentManager(), TAG_REMOVE_FILES_DIALOG_FRAGMENT)
                true
            }

            R.id.action_manage_tags -> {
                startActivity(TagsActivity.startForManageTags(requireContext(), file))
                true
            }

            R.id.action_see_details -> {
                seeDetails()
                true
            }

            R.id.action_send_file -> {
                requireActivity().sendDownloadedFilesByShareSheet(listOf(file))
                true
            }

            R.id.action_sync_file -> {
                account?.let { fileOperationsViewModel.performOperation(FileOperation.SynchronizeFileOperation(file, it.name)) }
                true
            }

            R.id.action_set_available_offline -> {
                val fileToSetAsAvailableOffline = ArrayList<OCFile>()
                fileToSetAsAvailableOffline.add(file)
                fileOperationsViewModel.performOperation(FileOperation.SetFilesAsAvailableOffline(fileToSetAsAvailableOffline))
                Snackbar.make(requireView(), R.string.confirmation_set_available_offline, Snackbar.LENGTH_LONG).show()
                true
            }

            R.id.action_unset_available_offline -> {
                val fileToUnsetAsAvailableOffline = ArrayList<OCFile>()
                fileToUnsetAsAvailableOffline.add(file)
                fileOperationsViewModel.performOperation(FileOperation.UnsetFilesAsAvailableOffline(fileToUnsetAsAvailableOffline))
                Snackbar.make(requireView(), R.string.confirmation_unset_available_offline, Snackbar.LENGTH_LONG).show()
                true
            }

            R.id.action_set_favorite -> {
                file.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(FileOperation.SetFileFavoriteStatus(fileId, isFavorite = true))
                    file = file.copy(isFavorite = true)
                    requireView().showFavoriteStatusSnackbar(isFavorite = true)
                }
                requireActivity().invalidateOptionsMenu()
                true
            }

            R.id.action_unset_favorite -> {
                file.id?.let { fileId ->
                    fileOperationsViewModel.performOperation(FileOperation.SetFileFavoriteStatus(fileId, isFavorite = false))
                    file = file.copy(isFavorite = false)
                    requireView().showFavoriteStatusSnackbar(isFavorite = false)
                }
                requireActivity().invalidateOptionsMenu()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun onFileMetadataChanged(updatedFile: OCFile?) {
        updatedFile?.let {
            file = updatedFile
        }
        requestFilterMenuOptions()
        requireActivity().invalidateOptionsMenu()
    }

    override fun onFileMetadataChanged() {
        mContainerActivity.storageManager?.let {
            file = it.getFileByPath(file.remotePath)
        }
        requestFilterMenuOptions()
        requireActivity().invalidateOptionsMenu()
    }

    override fun onFileContentChanged() {
        _binding?.pdfViewer?.reload()
    }

    override fun updateViewForSyncInProgress() {
        // Nothing to do here, sync is not shown in previews
    }

    override fun updateViewForSyncOff() {
        // Nothing to do here, sync is not shown in previews
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        requestFilterMenuOptions()
        menu.filterMenuOptions(latestMenuOptions, file.hasWritePermission)

        menu.findItem(R.id.action_search)?.apply {
            isVisible = false
            isEnabled = false
        }

        setRolesAccessibilityToMenuItems(menu)
    }

    private fun setRolesAccessibilityToMenuItems(menu: Menu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            menu.findItem(R.id.action_see_details)?.contentDescription =
                "${getString(R.string.actionbar_see_details)} ${getString(R.string.button_role_accessibility)}"
        }
    }

    private fun requestFilterMenuOptions() {
        mContainerActivity.storageManager?.let {
            previewPdfViewModel.filterMenuOptions(file, it.account.name)
        }
    }

    private fun requestLoadPreview() {
        val storagePath = file.storagePath
        if (storagePath.isNullOrBlank()) {
            showPreviewError()
            return
        }
        binding.pdfViewer.post {
            binding.pdfViewer.isVisible = true
            binding.pdfViewer.loadPdf(storagePath)
        }
    }

    private fun setupPageNavigationControls() {
        val buttonRole = getString(R.string.button_role_accessibility)
        binding.pdfPagePrevious.contentDescription =
            "${getString(R.string.homecloud_pdf_preview_page_previous)} $buttonRole"
        binding.pdfPageNext.contentDescription =
            "${getString(R.string.homecloud_pdf_preview_page_next)} $buttonRole"

        binding.pdfPagePrevious.setOnClickListener {
            binding.pdfViewer.previousPage()
        }
        binding.pdfPageNext.setOnClickListener {
            binding.pdfViewer.nextPage()
        }
    }

    private fun setupZoomControls() {
        val buttonRole = getString(R.string.button_role_accessibility)
        binding.pdfZoomIn.contentDescription =
            "${getString(R.string.homecloud_pdf_preview_zoom_in)} $buttonRole"
        binding.pdfZoomOut.contentDescription =
            "${getString(R.string.homecloud_pdf_preview_zoom_out)} $buttonRole"

        binding.pdfZoomIn.setOnClickListener {
            binding.pdfViewer.zoomIn()
        }
        binding.pdfZoomOut.setOnClickListener {
            binding.pdfViewer.zoomOut()
        }
        binding.pdfZoomLabel.setOnClickListener { anchorView ->
            showZoomPopupMenu(anchorView)
        }
    }

    private fun showZoomPopupMenu(anchorView: View) {
        val popupMenu = PopupMenu(requireContext(), anchorView)
        popupMenu.setForceShowIcon(true)
        popupMenu.menuInflater.inflate(R.menu.pdf_preview_zoom_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.pdf_zoom_fit_width -> {
                    binding.pdfViewer.fitWidth()
                    true
                }
                R.id.pdf_zoom_fit_page -> {
                    binding.pdfViewer.fitPage()
                    true
                }
                R.id.pdf_zoom_preset_25 -> {
                    binding.pdfViewer.setZoomPreset(25)
                    true
                }
                R.id.pdf_zoom_preset_50 -> {
                    binding.pdfViewer.setZoomPreset(50)
                    true
                }
                R.id.pdf_zoom_preset_75 -> {
                    binding.pdfViewer.setZoomPreset(75)
                    true
                }
                R.id.pdf_zoom_preset_100 -> {
                    binding.pdfViewer.setZoomPreset(100)
                    true
                }
                R.id.pdf_zoom_preset_150 -> {
                    binding.pdfViewer.setZoomPreset(150)
                    true
                }
                R.id.pdf_zoom_preset_200 -> {
                    binding.pdfViewer.setZoomPreset(200)
                    true
                }
                R.id.pdf_zoom_preset_400 -> {
                    binding.pdfViewer.setZoomPreset(400)
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun updateZoomUi(zoomState: PdfZoomState) {
        binding.pdfZoomLabel.text = getString(
            R.string.homecloud_pdf_preview_zoom_percent,
            zoomState.displayPercent,
        )
        binding.pdfZoomIn.isEnabled = zoomState.canZoomIn
        binding.pdfZoomOut.isEnabled = zoomState.canZoomOut
    }

    private fun updatePageNavigationUi(navigation: PdfPageNavigationState) {
        binding.pdfPageIndicator.text = getString(
            R.string.homecloud_pdf_preview_page_indicator,
            navigation.currentPage + 1,
            navigation.pageCount,
        )
        binding.pdfPagePrevious.isEnabled = navigation.canGoPrevious
        binding.pdfPageNext.isEnabled = navigation.canGoNext
    }

    private fun showPageNavigationBar(isVisible: Boolean) {
        binding.pdfPageNavigationBar.isVisible = isVisible
    }

    private fun showPreviewError() {
        Snackbar.make(requireView(), R.string.homecloud_pdf_preview_failed, Snackbar.LENGTH_LONG).show()
    }

    private fun openExternalPdfLink(uri: Uri) {
        val scheme = uri.scheme?.lowercase(Locale.US)
        if (scheme != "http" && scheme != "https") {
            Timber.w("Ignoring non-http PDF link: %s", uri)
            return
        }
        if (uri.host.isNullOrBlank()) {
            Timber.w("Ignoring PDF link with empty host: %s", uri)
            return
        }
        Timber.d("Opening PDF link in browser: %s", uri)
        requireActivity().goToUrl(uri.toString())
    }

    private fun openFile() {
        mContainerActivity.fileOperationsHelper.openFile(file)
        finish()
    }

    private fun seeDetails() {
        mContainerActivity.showDetails(file)
    }

    private fun finish() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    companion object {
        private const val EXTRA_FILE = "FILE"
        private const val EXTRA_ACCOUNT = "ACCOUNT"

        var isOpen = false
        var currentFilePreviewing: OCFile? = null

        fun newInstance(file: OCFile, account: Account): PreviewPdfFragment {
            val args = Bundle().apply {
                putParcelable(EXTRA_FILE, file)
                putParcelable(EXTRA_ACCOUNT, account)
            }

            return PreviewPdfFragment().apply {
                arguments = args
            }
        }

        fun canBePreviewed(file: OCFile?): Boolean =
            file != null && file.isAvailableLocally && file.isPdf
    }
}
