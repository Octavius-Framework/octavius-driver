package io.github.octaviusframework.driver.ssl

import java.io.FileInputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.net.ssl.*

object PgSslUpgrader {

    fun upgrade(socket: Socket, host: String, port: Int, config: SslConfiguration): SSLSocket {
        val sslContext = SSLContext.getInstance("TLSv1.2")
        
        val trustManagers = createTrustManagers(config)
        val keyManagers = createKeyManagers(config)
        
        sslContext.init(keyManagers, trustManagers, SecureRandom())
        
        val factory = sslContext.socketFactory
        val sslSocket = factory.createSocket(socket, host, port, true) as SSLSocket
        
        if (config.mode == SslMode.VERIFY_FULL) {
            val sslParams = sslContext.defaultSSLParameters
            sslParams.endpointIdentificationAlgorithm = "HTTPS"
            sslSocket.sslParameters = sslParams
        }
        
        sslSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3").filter { it in sslSocket.supportedProtocols }.toTypedArray()
        
        sslSocket.startHandshake()
        return sslSocket
    }

    private fun createTrustManagers(config: SslConfiguration): Array<TrustManager>? {
        if (config.mode == SslMode.DISABLE) return null
        
        if (config.mode == SslMode.PREFER || config.mode == SslMode.REQUIRE) {
            return arrayOf(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            })
        }
        
        if (config.rootCertPath != null) {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = FileInputStream(config.rootCertPath).use { cf.generateCertificates(it) }
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            for ((index, cert) in certs.withIndex()) {
                keyStore.setCertificateEntry("ca-$index", cert)
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            return tmf.trustManagers
        }
        
        return null // Use JVM default
    }

    private fun createKeyManagers(config: SslConfiguration): Array<KeyManager>? {
        if (config.certPath == null || config.keyPath == null) return null
        
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val certs = FileInputStream(config.certPath).use { cf.generateCertificates(it) }
            
            val keyBytes = Files.readAllBytes(Paths.get(config.keyPath))
            var keyString = String(keyBytes, Charsets.UTF_8)
            
            keyString = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("\\s+".toRegex(), "")
                
            val decoded = Base64.getDecoder().decode(keyString)
            
            val keySpec = PKCS8EncodedKeySpec(decoded)
            val kf = KeyFactory.getInstance("RSA")
            val privateKey = kf.generatePrivate(keySpec)
            
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            keyStore.load(null, null)
            
            val password = config.keyPassword?.toCharArray() ?: CharArray(0)
            keyStore.setKeyEntry("client", privateKey, password, certs.toTypedArray())
            
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, password)
            return kmf.keyManagers
        } catch (e: Exception) {
            throw RuntimeException("Failed to load client certificate or private key. Ensure the key is in PKCS8 format without password, or use a supported format.", e)
        }
    }
}
