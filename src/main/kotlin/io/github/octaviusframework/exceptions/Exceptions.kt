package io.github.octaviusframework.exceptions

enum class BadStatementExceptionMessage {
    SYNTAX_ERROR
}

class BadStatementException(
    val messageType: BadStatementExceptionMessage,
    override val cause: Throwable? = null
) : RuntimeException(messageType.name, cause)
