package io.github.octaviusframework.driver.auth

import io.github.octaviusframework.driver.exception.AuthExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusAuthException
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.backend.*
import io.github.octaviusframework.driver.message.frontend.SASLInitialResponse
import io.github.octaviusframework.driver.message.frontend.SASLResponse
import java.nio.charset.StandardCharsets

internal class Authenticator(private val stream: PgStream) {

    fun authenticate(user: String, password: String?) {
        while (true) {
            val msg = stream.receiveMessage()

            when (msg) {
                is AuthenticationMessage.Ok -> {
                    println("Authentication successful!")
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

                    val salt = java.util.Base64.getDecoder().decode(saltB64)
                    val iterations = iterationsStr.toInt()

                    val clientFinalMessageWithoutProof = "c=biws,r=$serverNonce"

                    val proof = ScramSha256Authenticator.computeClientProof(
                        password ?: "",
                        salt,
                        iterations,
                        clientFirstMessageBare,
                        serverFirstMessage,
                        clientFinalMessageWithoutProof
                    )

                    val clientFinalMessage = "$clientFinalMessageWithoutProof,p=$proof"
                    stream.sendMessage(SASLResponse(clientFinalMessage))
                    stream.flush()

                    // Server should then send SASLFinal
                    val finalMsg = stream.receiveMessage()
                    if (finalMsg is ErrorResponseMessage) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS, details = finalMsg.message
                        )
                    }
                    if (finalMsg !is AuthenticationMessage.SASLFinal) {
                        throw OctaviusAuthException(
                            AuthExceptionMessage.PROTOCOL_VIOLATION,
                            details = "Expected SASLFinal, got: $finalMsg"
                        )
                    }
                    // In theory we could verify server signature (v=...), but for simplicity we proceed
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
                    throw OctaviusAuthException(
                        AuthExceptionMessage.SERVER_REJECTED_CREDENTIALS,
                        details = "Error from server during connection: ${msg.message}"
                    )
                }

                is BackendKeyDataMessage -> {
                    println("Received process keys: ${msg.processId}")
                }

                is ReadyForQueryMessage -> {
                    println("Logged in successfully! Server ready for queries.")
                    return // End of login phase
                }

                is ParameterStatusMessage -> {
                    println("Received session parameter: ${msg.name} = ${msg.value}")
                }

                else -> {
                    println("Ignoring unexpected message: $msg")
                }
            }
        }
    }
}
