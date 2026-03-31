package com.owncloud.android.presentation.tags

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.owncloud.android.R

class TagDialogFragment : DialogFragment() {

    private var listener: TagDialogListener? = null
    private var tagId: String? = null

    interface TagDialogListener {
        fun onTagNameSet(tagName: String, tagId: String?)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val isEdit = tagId != null
        val currentName = arguments?.getString(ARG_CURRENT_NAME).orEmpty()
        val existingTagNames = arguments?.getStringArrayList(ARG_EXISTING_NAMES) ?: arrayListOf()

        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.edit_box_dialog, null)

        val inputText: EditText = view.findViewById(R.id.user_input)
        val inputLayout: TextInputLayout = view.findViewById(R.id.edit_box_input_text_layout)

        inputLayout.hint = getString(if (isEdit) R.string.tags_edit_hint else R.string.tags_add_hint)
        inputText.setText(currentName)
        inputText.setSelection(currentName.length)
        inputText.requestFocus()

        val titleRes = if (isEdit) R.string.tags_edit else R.string.tags_add

        val builder = MaterialAlertDialogBuilder(requireActivity())
            .setView(view)
            .setTitle(getString(titleRes))
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)

        val alertDialog = builder.create()

        alertDialog.setOnShowListener {
            val okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val initialError = validate(currentName, existingTagNames)
            okButton.isEnabled = currentName.isNotBlank() && initialError == null

            okButton.setOnClickListener {
                val name = inputText.text.toString().trim()
                listener?.onTagNameSet(name, tagId)
                dismiss()
            }
        }

        inputText.doOnTextChanged { text, _, _, _ ->
            val name = text?.toString().orEmpty().trim()
            val okButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            if (name.isBlank()) {
                inputLayout.error = null
                okButton.isEnabled = false
            } else {
                val error = validate(name, existingTagNames)
                inputLayout.error = error
                okButton.isEnabled = error == null
            }
        }

        alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        return alertDialog
    }

    private fun validate(name: String, existingTagNames: List<String>): String? = when {
        specialCharRegex.containsMatchIn(name) -> getString(R.string.tags_error_special_characters)
        name.length > MAX_TAG_LENGTH -> getString(R.string.tags_error_max_length)
        existingTagNames.any { it.equals(name, ignoreCase = true) } -> getString(R.string.tags_error_already_exists)
        else -> null
    }

    companion object {
        const val TAG_DIALOG_FRAGMENT = "TAG_DIALOG_FRAGMENT"
        private const val ARG_CURRENT_NAME = "ARG_CURRENT_NAME"
        private const val ARG_EXISTING_NAMES = "ARG_EXISTING_NAMES"
        private const val MAX_TAG_LENGTH = 30
        private val specialCharRegex = Regex("[^\\p{L}\\p{N} _\\-]")

        fun newAddInstance(listener: TagDialogListener, existingTagNames: List<String> = emptyList()): TagDialogFragment =
            TagDialogFragment().apply {
                this.listener = listener
                arguments = Bundle().apply {
                    putStringArrayList(ARG_EXISTING_NAMES, ArrayList(existingTagNames))
                }
            }

        fun newEditInstance(tagId: String, currentName: String, listener: TagDialogListener, existingTagNames: List<String> = emptyList()): TagDialogFragment =
            TagDialogFragment().apply {
                this.listener = listener
                this.tagId = tagId
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_NAME, currentName)
                    putStringArrayList(ARG_EXISTING_NAMES, ArrayList(existingTagNames))
                }
            }
    }
}
