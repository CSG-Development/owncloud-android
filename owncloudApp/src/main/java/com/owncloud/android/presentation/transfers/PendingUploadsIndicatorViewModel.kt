package com.owncloud.android.presentation.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.owncloud.android.domain.transfers.model.OCTransfer
import com.owncloud.android.domain.transfers.model.TransferStatus
import com.owncloud.android.domain.transfers.usecases.GetAllTransfersAsStreamUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PendingUploadsIndicatorState(
    val isVisible: Boolean,
)

class PendingUploadsIndicatorViewModel(
    getAllTransfersAsStreamUseCase: GetAllTransfersAsStreamUseCase,
) : ViewModel() {

    private val pendingTransfersFlow = getAllTransfersAsStreamUseCase(Unit).map { transfers ->
        transfers.filter { transfer ->
            transfer.status == TransferStatus.TRANSFER_QUEUED ||
                    transfer.status == TransferStatus.TRANSFER_IN_PROGRESS
        }
    }

    val state: StateFlow<PendingUploadsIndicatorState> =
        pendingTransfersFlow.map { pendingTransfers ->
            toIndicatorState(pendingTransfers)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PendingUploadsIndicatorState(isVisible = false),
        )

    private fun toIndicatorState(
        pendingTransfers: List<OCTransfer>,
    ): PendingUploadsIndicatorState {
        return PendingUploadsIndicatorState(isVisible = pendingTransfers.isNotEmpty())
    }
}
