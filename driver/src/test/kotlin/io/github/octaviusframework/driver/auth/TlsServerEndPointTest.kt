package io.github.octaviusframework.driver.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.*

/**
 * Checks the `tls-server-end-point` hash against OpenSSL rather than against itself.
 *
 * Every expected value below came out of:
 *
 * ```
 * openssl x509 -in cert-sha256.pem -outform DER | openssl dgst -sha256 -binary | openssl base64
 * ```
 *
 * which is the whole point of the exercise: OpenSSL is the implementation sitting on the other
 * side of a real exchange, and a hash the two sides disagree on is an authentication failure with
 * nothing in the message to explain it.
 */
class TlsServerEndPointTest {

    private fun hashOf(fixture: String): String {
        val certificate = javaClass.getResourceAsStream("/channelbinding/$fixture").use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
        return Base64.getEncoder().encodeToString(TlsServerEndPoint.hash(certificate))
    }

    @Test
    fun `hashes a SHA-256 signed certificate with SHA-256`() {
        assertEquals("VWQeR6oRgqHibz2BzADLIdeGhNiVU0W1MgFbfHcA7kI=", hashOf("cert-sha256.pem"))
    }

    @Test
    fun `follows a certificate up to SHA-384`() {
        assertEquals(
            "7cvrOQqIpK1ScAnsT+3P3MYkbYCUtGjBmBs06sV7PPCcX4wzfTnOSWtFRRFfs3/u",
            hashOf("cert-sha384.pem")
        )
    }

    @Test
    fun `promotes a SHA-1 signature to SHA-256`() {
        // RFC 5929 refuses to bind to a broken digest, so the same bytes are hashed with SHA-256
        // instead - which is why this differs from the SHA-1 of the certificate.
        assertEquals("X15PMexD/8hx+ptNQ0sEptqE7H3+cibkLUL4uDnI8b8=", hashOf("cert-sha1.pem"))
    }
}
