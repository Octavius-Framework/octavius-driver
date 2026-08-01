package io.github.octaviusframework.driver.exception

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
    USER_CODEC_ERROR,
    NESTED_PGTYPED_NOT_ALLOWED
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
        TypeExceptionMessage.NESTED_PGTYPED_NOT_ALLOWED -> "Nested PgTyped is not allowed. You cannot wrap a PgTyped instance within another PgTyped."
    }
