package com.owncloud.android.lib.common.network

import java.security.cert.CertificateException

class CertificateChainedException(
    private val exceptions: List<CertificateException>
) : CertificateException() {

    override val message: String
        get() = exceptions.joinToString { it.message.orEmpty() }
}