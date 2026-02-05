package com.owncloud.android.presentation.appupdate

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R

/**
 * Dialog fragment to show app update suggestion to user.
 * The dialog is skippable - user can dismiss it and continue using the app.
 */
class AppUpdateDialogFragment : DialogFragment() {

    private var listener: AppUpdateDialogListener? = null

    private val latestVersion: String by lazy { arguments?.getString(ARG_LATEST_VERSION) ?: throw IllegalArgumentException("latestVersion is null") }
    private val releaseDate: String by lazy { arguments?.getString(ARG_RELEASE_DATE).orEmpty()}
    private val updateUrl: String by lazy { arguments?.getString(ARG_UPDATE_URL) ?: throw IllegalArgumentException("updateUrl is null") }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message = getString(R.string.homecloud_app_update_dialog_message, latestVersion, releaseDate)

        val builder = MaterialAlertDialogBuilder(requireActivity())
            .setTitle(R.string.homecloud_app_update_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.homecloud_app_update_dialog_update) { _, _ ->
                openUpdateUrl()
                listener?.onUpdateClicked()
            }
            .setNegativeButton(R.string.homecloud_app_update_dialog_skip) { _, _ ->
                listener?.onSkipClicked()
            }

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)

        return dialog
    }

    private fun openUpdateUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, updateUrl.toUri())
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this.requireContext(), "Can't open update url", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        listener?.onSkipClicked()
    }

    interface AppUpdateDialogListener {
        fun onUpdateClicked()
        fun onSkipClicked()
    }

    companion object {
        const val TAG = "AppUpdateDialogFragment"
        private const val ARG_LATEST_VERSION = "arg_latest_version"
        private const val ARG_RELEASE_DATE = "arg_release_notes"
        private const val ARG_UPDATE_URL = "arg_update_url"

        fun newInstance(
            latestVersion: String,
            releaseDate: String? = null,
            updateUrl: String,
            listener: AppUpdateDialogListener? = null
        ): AppUpdateDialogFragment {
            return AppUpdateDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().apply {
                    putString(ARG_LATEST_VERSION, latestVersion)
                    putString(ARG_RELEASE_DATE, releaseDate)
                    putString(ARG_UPDATE_URL, updateUrl)
                }
            }
        }
    }
}
