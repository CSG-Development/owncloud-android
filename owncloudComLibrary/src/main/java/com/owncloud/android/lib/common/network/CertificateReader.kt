package com.owncloud.android.lib.common.network

import java.security.cert.Certificate

interface CertificateReader {

    fun readCertificates(): List<Certificate>
}