package com.owncloud.android.lib.common.network

import android.content.res.AssetManager
import timber.log.Timber
import java.io.IOException
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

class AssetsCertificateReader(private val assetManager: AssetManager) : CertificateReader {

    companion object {
        private const val CERT_FOLDER = "cert"
        private val CERT_EXTENSIONS = listOf(".cer", ".pem", ".crt")
    }

    private val certificateFactory = CertificateFactory.getInstance("X.509")

    override fun readCertificates(): List<Certificate> {
        val certificates = mutableListOf<Certificate>()
        try {
            val list = assetManager.list(CERT_FOLDER)
            if (!list.isNullOrEmpty()) {
                list.forEach { fileName ->
                    if (CERT_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }) {
                        assetManager.open("${CERT_FOLDER}/$fileName").use { inputStream ->
                            val certificate = certificateFactory.generateCertificate(inputStream)
                            certificates.add(certificate)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        }
        return certificates
    }
}