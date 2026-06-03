package io.github.octaviusframework.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ScramSha256Authenticator {
    private const val HMAC_SHA256 = "HmacSHA256"

    fun generateClientNonce(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes).replace(Regex("[^a-zA-Z0-9]"), "")
    }

    fun computeClientProof(password: String, salt: ByteArray, iterations: Int, clientFirstMessageBare: String, serverFirstMessage: String, clientFinalMessageWithoutProof: String): String {
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

        return Base64.getEncoder().encodeToString(clientProof)
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
