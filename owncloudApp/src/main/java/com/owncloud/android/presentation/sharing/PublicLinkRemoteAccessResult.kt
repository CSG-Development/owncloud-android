package com.owncloud.android.presentation.sharing

sealed class PublicLinkRemoteAccessResult {
    data object Ready : PublicLinkRemoteAccessResult()

    data object AuthenticationRequired : PublicLinkRemoteAccessResult()

    data object Unavailable : PublicLinkRemoteAccessResult()
}
