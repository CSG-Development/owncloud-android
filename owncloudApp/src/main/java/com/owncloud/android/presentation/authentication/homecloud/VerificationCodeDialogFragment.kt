package com.owncloud.android.presentation.authentication.homecloud

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.owncloud.android.R
import com.owncloud.android.databinding.AccountDialogCodeBinding
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * A reusable DialogFragment for verification code input.
 *
 * This dialog handles the verification code flow for remote access authentication:
 * - Shows a code input view for 6-digit verification codes
 * - Manages loading states for initiating and verifying codes
 * - Handles errors (wrong code, expired code)
 * - Provides resend functionality
 * - Supports skip action
 *
 * Usage:
 * ```kotlin
 * VerificationCodeDialogFragment.newInstance(email = "user@example.com").apply {
 *     setListener(object : VerificationCodeDialogListener {
 *         override fun onCodeVerified() {
 *             // Handle successful verification
 *         }
 *         override fun onSkipped() {
 *             // Handle skip
 *         }
 *         override fun onDismissed() {
 *             // Handle dismiss
 *         }
 *     })
 * }.show(supportFragmentManager, VerificationCodeDialogFragment.TAG)
 * ```
 */
class VerificationCodeDialogFragment : DialogFragment() {

    private var _binding: AccountDialogCodeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VerificationCodeViewModel by viewModel()

    private var listener: VerificationCodeDialogListener? = null
    private var isHandled = false

    /**
     * Listener interface for verification code dialog events.
     */
    interface VerificationCodeDialogListener {
        /**
         * Called when the verification code is successfully verified.
         */
        fun onCodeVerified()

        /**
         * Called when the user chooses to skip verification.
         */
        fun onSkipped()

        /**
         * Called when the dialog is dismissed without completing verification.
         */
        fun onDismissed()
    }

    companion object {
        const val TAG = "VerificationCodeDialogFragment"
        private const val ARG_EMAIL = "email"

        /**
         * Creates a new instance of [VerificationCodeDialogFragment].
         *
         * @param email The email address to send the verification code to.
         * @return A new instance of [VerificationCodeDialogFragment].
         */
        fun newInstance(email: String): VerificationCodeDialogFragment {
            return VerificationCodeDialogFragment().apply {
                arguments = bundleOf(ARG_EMAIL to email)
            }
        }
    }

    /**
     * Sets the listener for dialog events.
     * Can be chained during dialog creation.
     */
    fun setListener(listener: VerificationCodeDialogListener): VerificationCodeDialogFragment {
        this.listener = listener
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent dialog from being dismissed on rotation
        retainInstance = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = AccountDialogCodeBinding.inflate(requireActivity().layoutInflater)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .create()

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        observeState()
    }

    private fun setupViews() {
        binding.codeEditVerification.onCodeChangedListener = { code ->
            binding.allowButton.isEnabled =
                code.length == binding.codeEditVerification.getFilledCodeLength() &&
                        viewModel.state.value.isAllowButtonEnabled
            // Clear error when user starts typing
            if (viewModel.state.value.error != null) {
                viewModel.clearError()
            }
        }

        binding.allowButton.setOnClickListener {
            viewModel.onCodeEntered(binding.codeEditVerification.getCode())
        }

        binding.resendButton.setOnClickListener {
            binding.codeEditVerification.clearCode()
            viewModel.onResendClicked()
        }

        binding.skipButton.setOnClickListener {
            viewModel.onSkipClicked()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        updateUi(state)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUi(state: VerificationCodeViewModel.VerificationCodeState) {
        // Handle loading state
        when {
            state.isVerifying -> {
                binding.allowButton.visibility = View.INVISIBLE
                binding.resendButton.visibility = View.INVISIBLE
                binding.allowLoading.visibility = View.VISIBLE
            }

            state.error == null -> {
                binding.allowButton.visibility = View.VISIBLE
                binding.allowButton.isEnabled = true
                binding.resendButton.visibility = View.INVISIBLE
                binding.allowLoading.visibility = View.INVISIBLE
                binding.codeEditVerification.clearError()
            }

            state.error is VerificationCodeViewModel.VerificationCodeError.WrongCode -> {
                binding.allowButton.visibility = View.VISIBLE
                binding.allowButton.isEnabled = false
                binding.resendButton.visibility = View.INVISIBLE
                binding.allowLoading.visibility = View.INVISIBLE
                binding.codeEditVerification.setError(getString(R.string.homecloud_incorrect_code))
            }

            state.error is VerificationCodeViewModel.VerificationCodeError.CodeExpired -> {
                binding.allowButton.visibility = View.INVISIBLE
                binding.resendButton.visibility = View.VISIBLE
                binding.allowLoading.visibility = View.INVISIBLE
                binding.codeEditVerification.setError(getString(R.string.homecloud_expired_code))
            }

            else -> {
                binding.allowButton.visibility = View.INVISIBLE
                binding.allowButton.isEnabled = false
                binding.resendButton.visibility = View.VISIBLE
                binding.allowLoading.visibility = View.INVISIBLE
                binding.codeEditVerification.setError(getString(R.string.homecloud_code_unknown_error))
            }
        }
    }

    private fun handleEvent(event: VerificationCodeViewModel.VerificationCodeEvent) {
        when (event) {
            VerificationCodeViewModel.VerificationCodeEvent.CodeVerified -> {
                isHandled = true
                listener?.onCodeVerified()
                dismiss()
            }

            VerificationCodeViewModel.VerificationCodeEvent.Skipped -> {
                isHandled = true
                listener?.onSkipped()
                dismiss()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        super.dismiss()
        if (!isHandled) {
            listener?.onDismissed()
        }
    }
}
