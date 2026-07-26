package io.github.octaviusframework.driver.exception

enum class UnsupportedFeatureExceptionMessage {
    UNSUPPORTED_ISOLATION_LEVEL,
    INVALID_TIMEOUT,
    UNWRAP_ERROR,
    FEATURE_NOT_SUPPORTED,
    NULL_SQL
}

class UnsupportedFeatureException(
    val messageEnum: UnsupportedFeatureExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null
) : OctaviusException(messageEnum.name, cause, null) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: UnsupportedFeatureExceptionMessage): String =
    when (messageEnum) {
        UnsupportedFeatureExceptionMessage.UNSUPPORTED_ISOLATION_LEVEL -> "The requested transaction isolation level is not supported."
        UnsupportedFeatureExceptionMessage.INVALID_TIMEOUT -> "Timeout value cannot be negative."
        UnsupportedFeatureExceptionMessage.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        UnsupportedFeatureExceptionMessage.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius Driver."
        UnsupportedFeatureExceptionMessage.NULL_SQL -> "SQL string cannot be null."
    }
