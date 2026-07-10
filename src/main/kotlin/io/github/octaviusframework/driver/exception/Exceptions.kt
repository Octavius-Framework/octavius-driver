package io.github.octaviusframework.driver.exception

/**
 * Base exception for all errors in the Octavius JDBC driver.
 */
open class OctaviusException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {
    
    open fun getDetailedMessage(): String? = null

    override fun toString(): String {
        val detailedMsg = getDetailedMessage()?.let { "DETAILS: $it\n" } ?: ""
        val nestedError = cause?.toString() ?: "No cause available"
        val causeSection = """
CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
"""

        return """
------------------------------------------------------------
ERROR: ${this::class.simpleName}
MESSAGE: $message
${detailedMsg}------------------------------------------------------------
$causeSection
"""
    }
}

// ------------------- TYPE SYSTEM & CONTAINERS -------------------

enum class TypeExceptionMessage {
    TYPE_NOT_FOUND,
    NOT_A_CONTAINER,
    MISSING_CODEC,
    CASTING_ERROR,
    ATTRIBUTE_NOT_FOUND,
    NOT_ENOUGH_DATA,
    INVALID_PARAMETER_TYPE,
    ANONYMOUS_RECORD_NOT_SUPPORTED
}

class OctaviusTypeException(
    val messageEnum: TypeExceptionMessage,
    val oid: Int? = null,
    val typeName: String? = null,
    val details: String? = null,
    cause: Throwable? = null
) : OctaviusException(messageEnum.name, cause) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (oid != null) appendLine("OID: $oid")
        if (typeName != null) appendLine("Type Name: $typeName")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: TypeExceptionMessage): String =
    when (messageEnum) {
        TypeExceptionMessage.TYPE_NOT_FOUND -> "The specified type was not found in the TypeRegistry."
        TypeExceptionMessage.NOT_A_CONTAINER -> "The type with the specified OID is not a valid container type (Composite, Array, etc.)."
        TypeExceptionMessage.MISSING_CODEC -> "Missing codec for the specific OID when parsing or serializing."
        TypeExceptionMessage.CASTING_ERROR -> "Type casting error when converting database value to Kotlin type."
        TypeExceptionMessage.ATTRIBUTE_NOT_FOUND -> "Requested attribute/column was not found in the composite type."
        TypeExceptionMessage.NOT_ENOUGH_DATA -> "Not enough data in the buffer to parse the container (e.g., array header)."
        TypeExceptionMessage.INVALID_PARAMETER_TYPE -> "Invalid parameter type provided for the specified PostgreSQL type (OID)."
        TypeExceptionMessage.ANONYMOUS_RECORD_NOT_SUPPORTED -> "PostgreSQL does not support receiving anonymous composite types (record) as input parameters."
    }

// ------------------- JDBC SPECIFIC -------------------

enum class JdbcExceptionMessage {
    CONNECTION_CLOSED,
    UNSUPPORTED_ISOLATION_LEVEL,
    AUTO_COMMIT_VIOLATION,
    INVALID_TIMEOUT,
    UNWRAP_ERROR,
    FEATURE_NOT_SUPPORTED,
    INVALID_URL,
    SSL_ERROR,
    UNSUPPORTED_SERVER_VERSION,
    INVALID_SAVEPOINT,
    STATEMENT_CLOSED,
    NULL_SQL,
    UNKNOWN_TRANSACTION_STATE
}

class OctaviusJdbcException(
    val messageEnum: JdbcExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null
) : OctaviusException(messageEnum.name, cause) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: JdbcExceptionMessage): String =
    when (messageEnum) {
        JdbcExceptionMessage.CONNECTION_CLOSED -> "Operation cannot be performed because the connection is closed."
        JdbcExceptionMessage.UNSUPPORTED_ISOLATION_LEVEL -> "The requested transaction isolation level is not supported."
        JdbcExceptionMessage.AUTO_COMMIT_VIOLATION -> "Operation (like setting a savepoint or commit/rollback) is not allowed when auto-commit is enabled."
        JdbcExceptionMessage.INVALID_TIMEOUT -> "Timeout value cannot be negative."
        JdbcExceptionMessage.UNWRAP_ERROR -> "Cannot unwrap the connection/statement to the requested interface."
        JdbcExceptionMessage.FEATURE_NOT_SUPPORTED -> "This feature is not supported by the Octavius JDBC Driver."
        JdbcExceptionMessage.INVALID_URL -> "Invalid JDBC URL provided."
        JdbcExceptionMessage.SSL_ERROR -> "SSL negotiation failed or is not supported by the server."
        JdbcExceptionMessage.UNSUPPORTED_SERVER_VERSION -> "Unsupported PostgreSQL server version. Octavius requires version 18 or higher."
        JdbcExceptionMessage.INVALID_SAVEPOINT -> "Invalid savepoint operation."
        JdbcExceptionMessage.STATEMENT_CLOSED -> "Operation cannot be performed because the statement is closed."
        JdbcExceptionMessage.NULL_SQL -> "SQL string cannot be null."
        JdbcExceptionMessage.UNKNOWN_TRANSACTION_STATE -> "Unknown transaction state."
    }

// ------------------- STATEMENT -------------------

enum class BadStatementExceptionMessage {
    SYNTAX_ERROR,
    UNCLOSED_QUOTE,
    UNCLOSED_DOLLAR_QUOTE,
    UNCLOSED_COMMENT
}

class BadStatementException(
    val messageEnum: BadStatementExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null
) : OctaviusException(messageEnum.name, cause) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: BadStatementExceptionMessage): String =
    when (messageEnum) {
        BadStatementExceptionMessage.SYNTAX_ERROR -> "The SQL statement contains a syntax error."
        BadStatementExceptionMessage.UNCLOSED_QUOTE -> "The SQL statement contains an unclosed string or identifier quote."
        BadStatementExceptionMessage.UNCLOSED_DOLLAR_QUOTE -> "The SQL statement contains an unclosed dollar-quoted string."
        BadStatementExceptionMessage.UNCLOSED_COMMENT -> "The SQL statement contains an unclosed multi-line comment."
    }

