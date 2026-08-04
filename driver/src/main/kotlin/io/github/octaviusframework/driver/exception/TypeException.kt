package io.github.octaviusframework.driver.exception

enum class TypeExceptionMessage {
    TYPE_NOT_FOUND,
    NOT_A_CONTAINER,
    MISSING_CODEC,
    ATTRIBUTE_NOT_FOUND,
    ANONYMOUS_RECORD_NOT_SUPPORTED,
    UNKNOWN_OID,
    UNSUPPORTED_OID,
    NESTED_PGTYPED_NOT_ALLOWED
}

class TypeException(
    val messageEnum: TypeExceptionMessage,
    val oid: Int? = null,
    val typeName: String? = null,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("TYPE_EXCEPTION:${messageEnum.name}", cause, sqlState) {
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
        TypeExceptionMessage.ATTRIBUTE_NOT_FOUND -> "Requested attribute/column was not found in the composite type."
        TypeExceptionMessage.ANONYMOUS_RECORD_NOT_SUPPORTED -> "PostgreSQL does not support receiving anonymous composite types (record) as input parameters."
        TypeExceptionMessage.UNKNOWN_OID -> "The specified OID is unknown to the TypeRegistry."
        TypeExceptionMessage.UNSUPPORTED_OID -> "The specified OID is known but not supported."
        TypeExceptionMessage.NESTED_PGTYPED_NOT_ALLOWED -> "Nested PgTyped is not allowed. You cannot wrap a PgTyped instance within another PgTyped."
    }
