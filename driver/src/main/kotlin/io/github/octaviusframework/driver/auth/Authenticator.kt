package io.github.octaviusframework.driver.auth

import io.github.octaviusframework.driver.message.translator.ExceptionTranslator
import io.github.octaviusframework.driver.exception.InitializationException
import io.github.octaviusframework.driver.exception.InitializationExceptionReason
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.backend.*
import io.github.oshai.kotlinlogging.KotlinLogging



/**
 * Handles the authentication process during the PostgreSQL connection startup phase.
 * It manages the state machine for exchanging authentication messages with the server.
 */
internal object Authenticator {

    private val logger = KotlinLogging.logger {}

    /**
     * Authenticates the user with the PostgreSQL server using the provided credentials.
     * Only SCRAM-SHA-256 authentication is supported, with or without channel binding.
     *
     * @param stream The underlying PostgreSQL communication stream used for message exchange.
     * @param password The password for the user, can be null if not required.
     * @param channelBinding How hard to insist that the exchange be bound to the TLS channel.
     * @throws InitializationException If authentication fails, protocol is violated, or unsupported mechanism is requested.
     */
    fun authenticate(stream: PgStream, password: String?, channelBinding: ChannelBinding) {
        var boundToChannel = false

        while (true) {
            val msg = stream.receiveMessage()

            when (msg) {
                is AuthenticationMessage.Ok -> {
                    // A server can wave a connection through without asking anything - `trust`, or a
                    // client certificate already accepted during the handshake. That is a perfectly
                    // good login, but it is not a bound one, and REQUIRE means REQUIRE.
                    if (channelBinding == ChannelBinding.REQUIRE && !boundToChannel) {
                        throw InitializationException(
                            InitializationExceptionReason.UNSUPPORTED_MECHANISM,
                            details = "channelBinding=require, but the server accepted this connection without " +
                                "a channel-bound authentication exchange."
                        )
                    }
                    logger.trace { "Authentication successful!" }
                    // Loop will continue to consume ParameterStatus until ReadyForQuery
                }

                is AuthenticationMessage.SASL -> {
                    boundToChannel = ScramSha256Authenticator.authenticate(
                        stream, password, msg.mechanisms, channelBinding
                    )
                }

                is AuthenticationMessage.CleartextPassword -> {
                    throw InitializationException(
                        InitializationExceptionReason.UNSUPPORTED_PASSWORD_ENCRYPTION,
                        details = "Server requested CleartextPassword, only SCRAM is supported"
                    )
                }

                is AuthenticationMessage.MD5Password -> {
                    throw InitializationException(
                        InitializationExceptionReason.UNSUPPORTED_PASSWORD_ENCRYPTION,
                        details = "Server requested MD5Password, only SCRAM is supported"
                    )
                }

                is ErrorResponseMessage -> {
                    throw ExceptionTranslator.translate(msg)
                }

                is NegotiateProtocolVersionMessage -> {
                    throw InitializationException(
                        InitializationExceptionReason.UNSUPPORTED_SERVER_VERSION,
                        details = "Server does not support the requested protocol version 3.2. Highest minor version supported by this server is 3.${msg.newestMinorVersion}."
                    )
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
