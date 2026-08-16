package io.github.octaviusframework.driver.auth

import io.github.octaviusframework.driver.exception.InitializationException
import io.github.octaviusframework.driver.exception.InitializationExceptionReason
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.X509Certificate

/**
 * Computes the `tls-server-end-point` channel binding data of RFC 5929 - the hash of the server's
 * certificate that a `SCRAM-SHA-256-PLUS` exchange is bound to.
 *
 * PostgreSQL implements no other binding type, so this is the whole of the subject.
 */
internal object TlsServerEndPoint {

    /**
     * Hashes the DER encoding of [certificate] with the digest named by the certificate's own
     * signature algorithm, which is the rule the server follows to arrive at the same bytes.
     *
     * @throws InitializationException if the certificate cannot be encoded or the digest is
     *   unavailable in this JVM - either way there is nothing to bind to.
     */
    fun hash(certificate: X509Certificate): ByteArray {
        val algorithm = digestFor(certificate.sigAlgName)
        return try {
            MessageDigest.getInstance(algorithm).digest(certificate.encoded)
        } catch (e: GeneralSecurityException) {
            throw InitializationException(
                InitializationExceptionReason.SSL_ERROR,
                "Could not hash the server certificate with $algorithm for channel binding. " +
                    "The certificate is signed with ${certificate.sigAlgName}.",
                e
            )
        }
    }

    /**
     * SHA-256 covers every case the rule leaves open: RFC 5929 demands it in place of a broken MD5
     * or SHA-1 signature, and it is the only sane reading of a signature algorithm whose name
     * carries no digest at all.
     */
    private fun digestFor(signatureAlgorithm: String): String {
        val name = signatureAlgorithm.uppercase().replace("-", "")
        return when {
            name.startsWith("SHA512") -> "SHA-512"
            name.startsWith("SHA384") -> "SHA-384"
            name.startsWith("SHA224") -> "SHA-224"
            else -> "SHA-256"
        }
    }
}
