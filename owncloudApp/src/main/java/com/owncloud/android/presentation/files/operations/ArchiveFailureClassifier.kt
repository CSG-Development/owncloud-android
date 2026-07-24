package com.owncloud.android.presentation.files.operations

import com.owncloud.android.domain.exceptions.ArchivePathTraversalException
import com.owncloud.android.domain.exceptions.CancelledException
import com.owncloud.android.domain.exceptions.DuplicateArchiveEntryException
import com.owncloud.android.domain.exceptions.ForbiddenException
import com.owncloud.android.domain.exceptions.InvalidArchiveException
import com.owncloud.android.domain.exceptions.InvalidCharacterException
import com.owncloud.android.domain.exceptions.InvalidLocalFileNameException
import com.owncloud.android.domain.exceptions.LocalFileNotFoundException
import com.owncloud.android.domain.exceptions.LocalStorageFullException
import com.owncloud.android.domain.exceptions.LocalStorageNotCopiedException
import com.owncloud.android.domain.exceptions.NetworkErrorException
import com.owncloud.android.domain.exceptions.NoConnectionWithServerException
import com.owncloud.android.domain.exceptions.NoNetworkConnectionException
import com.owncloud.android.domain.exceptions.QuotaExceededException
import com.owncloud.android.domain.exceptions.ServerConnectionTimeoutException
import com.owncloud.android.domain.exceptions.ServerNotReachableException
import com.owncloud.android.domain.exceptions.ServerResponseTimeoutException
import com.owncloud.android.domain.exceptions.SpecificForbiddenException
import com.owncloud.android.domain.exceptions.UnsupportedArchiveFormatException
import com.owncloud.android.domain.exceptions.validation.FileNameException

object ArchiveFailureClassifier {

    fun classify(throwable: Throwable): ArchiveFailureType? {
        if (throwable is CancelledException) return null

        return when (throwable) {
            is InvalidArchiveException,
            is UnsupportedArchiveFormatException,
            -> ArchiveFailureType.CORRUPT

            is LocalStorageFullException,
            is LocalStorageNotCopiedException,
            is QuotaExceededException,
            -> ArchiveFailureType.INSUFFICIENT_STORAGE

            is ArchivePathTraversalException,
            is DuplicateArchiveEntryException,
            is InvalidCharacterException,
            is InvalidLocalFileNameException,
            is FileNameException,
            -> ArchiveFailureType.INVALID_NAMES

            is NoNetworkConnectionException,
            is NoConnectionWithServerException,
            is NetworkErrorException,
            is ServerConnectionTimeoutException,
            is ServerResponseTimeoutException,
            is ServerNotReachableException,
            -> ArchiveFailureType.NETWORK

            is LocalFileNotFoundException,
            is ForbiddenException,
            is SpecificForbiddenException,
            -> ArchiveFailureType.FILE_ACCESS

            else -> ArchiveFailureType.UNEXPECTED
        }
    }
}
