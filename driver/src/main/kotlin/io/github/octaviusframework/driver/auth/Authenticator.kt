package io.github.octaviusframework.driver.auth

import io.github.octaviusframework.driver.exception.AuthExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusAuthException
import io.github.octaviusframework.driver.exception.ExceptionTranslator
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.SASLInitialResponse
import io.github.octaviusframework.driver.message.frontend.SASLResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.charset.StandardCharsets
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Handles the authentication process during the PostgreSQL connection startup phase.
 * It manages the state machine for exchanging authentication messages with the server.
 *
 * @property stream The underlying PostgreSQL communication stream used for message exchange.
 */
internal class Authenticator(private val stream: PgStream) {

    /**
     * Authenticates the user with the PostgreSQL server using the provided credentials.
     * Currently, only SCRAM-SHA-256 authentication is supported.
     *
     * @param user The username used for authentication.
     * @param password The password for the user, can be null if not required.
     * @throws OctaviusAuthException If authentication fails, protocol is violated, or unsupported mechanism is requested.
     */
    fun authenticate(user: String, password: String?) {
        while (true) {
            val msg = stream.receiveMessage()

            when (msg) {
                is AuthenticationMessage.Ok -> {
                    logger.trace { "Authentication successful!" }
                    // Loop will continue to consume ParameterStatus until ReadyForQuery
                }

                is AuthenticationMessage.SASL -> {
                    val mechs = msg.mechanisms
                    if (!mechs.contains("SCRAM-SHA-256")) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.UNSUPPORTED_MECHANISM, details = "Supported: $mechs"
                        )
                    }

                    val clientNonce = ScramSha256Authenticator.generateClientNonce()
                    val clientFirstMessageBare = "n=,r=$clientNonce"
                    val clientFirstMessage = "n,,$clientFirstMessageBare"

                    stream.sendMessage(SASLInitialResponse("SCRAM-SHA-256", clientFirstMessage))
                    stream.flush()

                    // Waiting for SASLContinue
                    val continueMsg = stream.receiveMessage()
                    if (continueMsg !is AuthenticationMessage.SASLContinue) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.PROTOCOL_VIOLATION,
                            details = "Expected SASLContinue, got: $continueMsg"
                        )
                    }

                    val serverFirstMessage = String(continueMsg.data, StandardCharsets.UTF_8)

                    // Parsing serverFirstMessage (r=..., s=..., i=...)
                    val parts = serverFirstMessage.split(",")
                    val params = parts.associate { it.substring(0, 1) to it.substring(2) }

                    val serverNonce = params["r"] ?: throw OctaviusAuthException(
                        AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Missing r in serverFirstMessage"
                    )
                    val saltB64 = params["s"] ?: throw OctaviusAuthException(
                        AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Missing s in serverFirstMessage"
                    )
                    val iterationsStr = params["i"] ?: throw OctaviusAuthException(
                        AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Missing i in serverFirstMessage"
                    )

                    val salt = Base64.getDecoder().decode(saltB64)
                    val iterations = iterationsStr.toInt()

                    val clientFinalMessageWithoutProof = "c=biws,r=$serverNonce"

                    val scramResult = ScramSha256Authenticator.computeSignatures(
                        password ?: "",
                        salt,
                        iterations,
                        clientFirstMessageBare,
                        serverFirstMessage,
                        clientFinalMessageWithoutProof
                    )

                    val clientFinalMessage = "$clientFinalMessageWithoutProof,p=${scramResult.clientProof}"
                    stream.sendMessage(SASLResponse(clientFinalMessage))
                    stream.flush()

                    // Server should then send SASLFinal
                    val finalMsg = stream.receiveMessage()
                    if (finalMsg is ErrorResponseMessage) {
                        throw ExceptionTranslator.translate(finalMsg)
                    }
                    if (finalMsg !is AuthenticationMessage.SASLFinal) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.PROTOCOL_VIOLATION,
                            details = "Expected SASLFinal, got: $finalMsg"
                        )
                    }

                    val serverFinalMessage = String(finalMsg.data, StandardCharsets.UTF_8)
                    val serverFinalParts = serverFinalMessage.split(",")
                    val serverParams = serverFinalParts.filter { it.length >= 3 }.associate { it.substring(0, 1) to it.substring(2) }
                    val serverSignature = serverParams["v"] ?: throw OctaviusAuthException(
                        AuthExceptionMessage.MISSING_PROTOCOL_PARAMETER, details = "Missing v in serverFinalMessage"
                    )

                    if (serverSignature != scramResult.expectedServerSignature) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS,
                            details = "Invalid server signature"
                        )
                    }
                }

                is AuthenticationMessage.CleartextPassword -> {
                    throw OctaviusAuthException(
                        AuthExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION,
                        details = "Server requested CleartextPassword, only SCRAM is supported"
                    )
                }

                is AuthenticationMessage.MD5Password -> {
                    throw OctaviusAuthException(
                        AuthExceptionMessage.UNSUPPORTED_PASSWORD_ENCRYPTION,
                        details = "Server requested MD5Password, only SCRAM is supported"
                    )
                }

                is ErrorResponseMessage -> {
                    throw ExceptionTranslator.translate(msg)
                }

                is BackendKeyDataMessage -> {
                    stream.processId = msg.processId
                    stream.secretKey = msg.secretKey
                    logger.trace { "Received process keys: ${msg.processId}" }
                }

                is ReadyForQueryMessage -> {
                    logger.trace { "Logged in successfully! Server ready for queries." }
                    return // End of login phase
                }

                is ParameterStatusMessage -> {
                    logger.trace { "Received session parameter: ${msg.name} = ${msg.value}" }
                }

                else -> {
                    logger.trace { "Ignoring unexpected message: $msg" }
                }
            }
        }
    }
}
