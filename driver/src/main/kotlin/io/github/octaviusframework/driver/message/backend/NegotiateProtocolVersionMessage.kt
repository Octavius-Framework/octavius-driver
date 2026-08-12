package io.github.octaviusframework.driver.message.backend

/**
 * Message sent by the server to negotiate the protocol version.
 * Introduced in PostgreSQL 14 to handle minor version upgrades.
 */
internal class NegotiateProtocolVersionMessage(
    val newestMinorVersion: Int,
    val unrecognizedOptions: List<String>
) : BackendMessage
