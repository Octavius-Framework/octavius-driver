package io.github.octaviusframework.driver.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Utility object for performing SCRAM-SHA-256 authentication cryptographic operations.
 * It provides functions for generating nonces and computing SCRAM signatures
 * according to RFC 7677.
 */
internal object ScramSha256Authenticator {
    private const val HMAC_SHA256 = "HmacSHA256"

    /**
     * Holds the results of a SCRAM signature computation.
     *
     * @property clientProof The computed client proof (base64 encoded).
     * @property expectedServerSignature The expected server signature for verification (base64 encoded).
     */
    data class ScramResult(val clientProof: String, val expectedServerSignature: String)

    /**
     * Generates a random base64-encoded string to be used as a client nonce.
     * Non-alphanumeric characters are removed.
     *
     * @return A random client nonce string.
     */
    fun generateClientNonce(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes).replace(Regex("[^a-zA-Z0-9]"), "")
    }



    /**
     * Computes the client proof and expected server signature for SCRAM authentication.
     *
     * @param password The user's plaintext password.
     * @param salt The salt provided by the server.
     * @param iterations The iteration count provided by the server.
     * @param clientFirstMessageBare The bare version of the client-first-message (without gs2-header).
     * @param serverFirstMessage The server-first-message received from the server.
     * @param clientFinalMessageWithoutProof The client-final-message up to the "p=" attribute.
     * @return A [ScramResult] containing the computed `clientProof` and `expectedServerSignature`.
     */
    fun computeSignatures(password: String, salt: ByteArray, iterations: Int, clientFirstMessageBare: String, serverFirstMessage: String, clientFinalMessageWithoutProof: String): ScramResult {
        // 1. SaltedPassword = Hi(Normalize(password), salt, i)
        val saltedPassword = pbkdf2(password, salt, iterations)

        // 2. ClientKey = HMAC(SaltedPassword, "Client Key")
        val clientKey = hmac(saltedPassword, "Client Key".toByteArray())

        // 3. StoredKey = H(ClientKey)
        val storedKey = sha256(clientKey)

        // 4. AuthMessage = client-first-message-bare + "," + server-first-message + "," + client-final-message-without-proof
        val authMessage = "$clientFirstMessageBare,$serverFirstMessage,$clientFinalMessageWithoutProof"

        // 5. ClientSignature = HMAC(StoredKey, AuthMessage)
        val clientSignature = hmac(storedKey, authMessage.toByteArray())

        // 6. ClientProof = ClientKey XOR ClientSignature
        val clientProof = ByteArray(clientKey.size)
        for (i in clientKey.indices) {
            clientProof[i] = (clientKey[i].toInt() xor clientSignature[i].toInt()).toByte()
        }

        // 7. ServerKey = HMAC(SaltedPassword, "Server Key")
        val serverKey = hmac(saltedPassword, "Server Key".toByteArray())

        // 8. ServerSignature = HMAC(ServerKey, AuthMessage)
        val serverSignature = hmac(serverKey, authMessage.toByteArray())

        return ScramResult(
            Base64.getEncoder().encodeToString(clientProof),
            Base64.getEncoder().encodeToString(serverSignature)
        )
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        return factory.generateSecret(spec).encoded
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_SHA256)
        mac.init(SecretKeySpec(key, HMAC_SHA256))
        return mac.doFinal(data)
    }

    private fun sha256(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}
