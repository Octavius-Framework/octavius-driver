package io.github.octaviusframework.driver.exception


/**
 * Represents specific types of network-related errors that can occur during database communication.
 */
enum class NetworkExceptionReason {
    CONNECTION_ERROR,
    CONNECTION_TIMEOUT,
    CONNECTION_CLOSED,
    CONNECTION_CLOSED_BY_PEER,
    CONNECTION_ABORTED
}

/**
 * Exception thrown when a network error disrupts communication with the database server.
 *
 * This can occur during active queries or while the connection is idle, typically resulting from
 * broken pipes, connection timeouts, or sudden closures by the server or intermediary network devices.
 *
 * @property reason The specific type of network failure.
 * @property details Additional, human-readable details about the failure.
 */
class NetworkException(
    val reason: NetworkExceptionReason,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = "08006"
) : OctaviusException("NETWORK_EXCEPTION:${reason.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: NetworkExceptionReason): String =
    when (reason) {
        NetworkExceptionReason.CONNECTION_ERROR -> "A network error occurred while communicating with the database."
        NetworkExceptionReason.CONNECTION_TIMEOUT -> "The network connection to the database timed out."
        NetworkExceptionReason.CONNECTION_CLOSED -> "Operation cannot be performed because the connection is closed."
        NetworkExceptionReason.CONNECTION_CLOSED_BY_PEER -> "The connection was unexpectedly closed by the peer."
        NetworkExceptionReason.CONNECTION_ABORTED -> "The connection was explicitly aborted by the client."
    }

