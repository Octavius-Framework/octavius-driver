package io.github.octaviusframework.driver.exception

enum class TypeExceptionReason {
    TYPE_NOT_FOUND,
    NOT_A_CONTAINER,
    MISSING_CODEC,
    ANONYMOUS_RECORD_NOT_SUPPORTED,
    NESTED_PGTYPED_NOT_ALLOWED
}

class TypeException(
    val reason: TypeExceptionReason,
    val oid: Int? = null,
    val typeName: String? = null,
    val details: String? = null,
    cause: Throwable? = null,
    sqlState: String? = null
) : OctaviusException("TYPE_EXCEPTION:${reason.name}", cause, sqlState) {
    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (oid != null) appendLine("OID: $oid")
        if (typeName != null) appendLine("Type Name: $typeName")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: TypeExceptionReason): String =
    when (reason) {
        TypeExceptionReason.TYPE_NOT_FOUND -> "The specified type was not found in the TypeRegistry."
        TypeExceptionReason.NOT_A_CONTAINER -> "The type with the specified OID is not a valid container type (Composite, Array, etc.)."
        TypeExceptionReason.MISSING_CODEC -> "Missing codec for the specific OID when parsing or serializing."
        TypeExceptionReason.ANONYMOUS_RECORD_NOT_SUPPORTED -> "PostgreSQL does not support receiving anonymous composite types (record) as input parameters."
        TypeExceptionReason.NESTED_PGTYPED_NOT_ALLOWED -> "Nested PgTyped is not allowed. You cannot wrap a PgTyped instance within another PgTyped."
    }
