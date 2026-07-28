package io.github.octaviusframework.driver.exception


enum class NetworkExceptionMessage {
    CONNECTION_ERROR,
    CONNECTION_TIMEOUT,
    CONNECTION_CLOSED,
    CONNECTION_CLOSED_BY_PEER,
    CONNECTION_ABORTED
}

class NetworkException(
    val messageEnum: NetworkExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = "08006"
) : OctaviusException(messageEnum.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: NetworkExceptionMessage): String =
    when (messageEnum) {
        NetworkExceptionMessage.CONNECTION_ERROR -> "A network error occurred while communicating with the database."
        NetworkExceptionMessage.CONNECTION_TIMEOUT -> "The network connection to the database timed out."
        NetworkExceptionMessage.CONNECTION_CLOSED -> "Operation cannot be performed because the connection is closed."
        NetworkExceptionMessage.CONNECTION_CLOSED_BY_PEER -> "The connection was unexpectedly closed by the peer."
        NetworkExceptionMessage.CONNECTION_ABORTED -> "The connection was explicitly aborted by the client."
    }

