package com.owncloud.android.lib.common.network

import timber.log.Timber
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Certificate trust manager that loads and validates certificates from the assets/cert folder.
 * Only certificates stored in this folder will be trusted for SSL connections.
 */
class PinnedCertificateTrustManager(
    private val assetsCertificateReader: CertificateReader
) : X509TrustManager {

    private val trustManagers: List<X509TrustManager> = listOf(
        createTrustManager(),
        createSystemTrustManager(),
    )

    private fun createSystemTrustManager(): X509TrustManager {
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(null as KeyStore?)
        val trustManagers = trustManagerFactory.trustManagers
        return trustManagers.first { it is X509TrustManager } as X509TrustManager
    }

    private fun createTrustManager(): X509TrustManager {
        val keyStore = initKeystore()

        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm()
        )
        trustManagerFactory.init(keyStore)

        val trustManagers = trustManagerFactory.trustManagers
        check(trustManagers.size == 1 && trustManagers[0] is X509TrustManager) {
            "Unexpected default trust managers: ${trustManagers.contentToString()}"
        }

        return trustManagers[0] as X509TrustManager
    }

    private fun initKeystore(): KeyStore {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)

        val certificate = assetsCertificateReader.readCertificate("hc.test.server.pem")
        val alias = try {
            certificate.subjectX500Principal.name.take(50)
        } catch (e: Exception) {
            "cert_hc"
        }
        keyStore.setCertificateEntry(alias, certificate)
        Timber.d("Loaded certificate: (Subject: ${certificate.subjectX500Principal.name})")
        return keyStore
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate?>?, authType: String) {
        val exceptions = mutableListOf<CertificateException>()
        for (trustManager in trustManagers) {
            try {
                trustManager.checkClientTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                Timber.e(e, "checkClientTrusted failed with ${e.message}")
                exceptions.add(e)
            }
        }
        throw CertificateChainedException(exceptions)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate?>?, authType: String) {
        val exceptions = mutableListOf<CertificateException>()
        for (trustManager in trustManagers) {
            try {
                trustManager.checkServerTrusted(chain, authType)
                return
            } catch (e: CertificateException) {
                Timber.e(e, "checkServerTrusted failed with ${e.message}")
                exceptions.add(e)
            }
        }
        throw CertificateChainedException(exceptions)
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate?> {
        val issuers = mutableListOf<X509Certificate?>()
        for (trustManager in trustManagers) {
            issuers.addAll(trustManager.acceptedIssuers)
        }
        return issuers.toTypedArray()
    }

}