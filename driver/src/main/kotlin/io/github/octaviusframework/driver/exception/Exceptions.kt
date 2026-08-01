package io.github.octaviusframework.driver.exception

/**
 * Base exception for all errors in the Octavius JDBC driver.
 */
open class OctaviusException(
    message: String,
    cause: Throwable? = null,
    val sqlState: String? = null
) : RuntimeException(message, cause) {
    
    var queryContext: QueryContext? = null

    open fun getDetailedMessage(): String? = null

    override fun toString(): String {
        val detailedMsg = getDetailedMessage()?.let { "DETAILS: $it\n" } ?: ""
        val nestedError = cause?.toString() ?: "No cause available"
        val sqlStateSection = sqlState?.let { "SQLSTATE: $it\n" } ?: ""
        val contextSection = queryContext?.let { "$it\n" } ?: ""
        val causeSection = """
CAUSE:
------------------------------------------------------------
$nestedError
------------------------------------------------------------
"""

        return """
------------------------------------------------------------
ERROR: ${this::class.simpleName}
$sqlStateSection
MESSAGE: $message
${detailedMsg}$contextSection------------------------------------------------------------
$causeSection
"""
    }
}

// ------------------- MAPPING & REFLECTION -------------------

enum class MappingExceptionMessage {
    UNKNOWN_ENUM_VALUE,
    NULL_FOR_NON_NULLABLE_ATTRIBUTE,
    MISSING_ATTRIBUTE,
    NO_CONVERTER_FOUND,
    INVALID_RECORD_FORMAT,
    COLUMN_NOT_FOUND,
    COLUMN_INDEX_OUT_OF_BOUNDS,
    USER_CONVERTER_ERROR
}

class OctaviusMappingException(
    val messageEnum: MappingExceptionMessage,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(messageEnum.name, cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("message: ${generateDeveloperMessage(messageEnum)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(messageEnum: MappingExceptionMessage): String =
    when (messageEnum) {
        MappingExceptionMessage.UNKNOWN_ENUM_VALUE -> "The specified enum value could not be found."
        MappingExceptionMessage.NULL_FOR_NON_NULLABLE_ATTRIBUTE -> "Attempted to map a null database value to a non-nullable Kotlin property."
        MappingExceptionMessage.MISSING_ATTRIBUTE -> "A required non-nullable attribute is missing from the database result."
        MappingExceptionMessage.NO_CONVERTER_FOUND -> "No converter was found for the specified types."
        MappingExceptionMessage.INVALID_RECORD_FORMAT -> "The record data is in an invalid format."
        MappingExceptionMessage.COLUMN_NOT_FOUND -> "The requested column was not found in the row metadata."
        MappingExceptionMessage.COLUMN_INDEX_OUT_OF_BOUNDS -> "The requested column index is out of bounds for the row."
        MappingExceptionMessage.USER_CONVERTER_ERROR -> "An exception was thrown by a user-provided converter or mapper."
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
    ANONYMOUS_RECORD_NOT_SUPPORTED,
    VALUE_OUT_OF_RANGE,
    UNKNOWN_OID,
    UNSUPPORTED_OID,
    USER_CODEC_ERROR
}

class TypeException(
    val messageEnum: TypeExceptionMessage,
    val oid: Int? = null,
    val typeName: String? = null,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException(messageEnum.name, cause, sqlState) {
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
        TypeExceptionMessage.VALUE_OUT_OF_RANGE -> "The value is out of range for the PostgreSQL or Kotlin type."
        TypeExceptionMessage.UNKNOWN_OID -> "The specified OID is unknown to the TypeRegistry."
        TypeExceptionMessage.UNSUPPORTED_OID -> "The specified OID is known but not supported."
        TypeExceptionMessage.USER_CODEC_ERROR -> "An exception was thrown by a user-provided codec."
    }

