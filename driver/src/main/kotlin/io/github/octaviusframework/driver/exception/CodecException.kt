package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.util.formatDiagnosticValue
import kotlin.reflect.KClass

/**
 * Represents the action (encoding or decoding) during which a codec error occurred.
 */
enum class CodecAction { ENCODING, DECODING }

/**
 * Exception thrown when encoding or decoding a value to/from a PostgreSQL representation fails.
 *
 * @property action Indicates whether the failure happened during encoding or decoding.
 * @property value The value that failed to process.
 * @property name The PostgreSQL type name associated with the operation, if known.
 * @property schema The PostgreSQL type schema associated with the operation, if known.
 * @property oid The PostgreSQL OID associated with the operation, if known.
 * @property kotlinClass The Kotlin class associated with the operation, if known.
 * @param cause The underlying exception that caused this failure.
 */
class CodecException(
    val action: CodecAction,
    val value: Any?,
    val name: String,
    val schema: String = "",
    val oid: Int? = null,
    val kotlinClass: KClass<*>? = null,
    cause: Exception
) : OctaviusException("CODEC_EXCEPTION:${action.name}", cause = cause) {

    override fun getDetailedMessage(): String = buildString {
        val actionStr = if (action == CodecAction.ENCODING) "encode" else "decode"
        val typeInfo = name
        val classInfo = kotlinClass?.qualifiedName ?: "unknown"

        appendLine("message: Failed to $actionStr value [${formatDiagnosticValue(value)}] for PostgreSQL type '$typeInfo' and Kotlin class '$classInfo'")
        appendLine("Name: $name")
        if (schema.isNotEmpty()) appendLine("Schema: $schema")
        if (oid != null) appendLine("OID: $oid")
        if (kotlinClass != null) appendLine("Kotlin Class: ${kotlinClass.qualifiedName}")
    }

}
