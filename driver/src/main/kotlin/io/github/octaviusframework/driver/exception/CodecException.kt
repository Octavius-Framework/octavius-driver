package io.github.octaviusframework.driver.exception

import io.github.octaviusframework.driver.type.PgType
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
 * @property pgType The PostgreSQL type associated with the operation, if known.
 * @property oid The PostgreSQL OID associated with the operation, if known.
 * @property kotlinClass The Kotlin class associated with the operation, if known.
 */
class CodecException(
    val action: CodecAction,
    val value: Any?,
    val pgType: PgType? = null,
    val oid: Int? = null,
    val kotlinClass: KClass<*>? = null,
    cause: Throwable? = null
) : OctaviusException("CODEC_EXCEPTION:${action.name}", cause) {

    override fun getDetailedMessage(): String = buildString {
        val actionStr = if (action == CodecAction.ENCODING) "encode" else "decode"
        val typeInfo = pgType?.name ?: oid?.toString() ?: "unknown"
        val classInfo = kotlinClass?.qualifiedName ?: "unknown"

        appendLine("message: Failed to $actionStr value [${formatValue(value)}] for PostgreSQL type '$typeInfo' and Kotlin class '$classInfo'")
        if (pgType != null) appendLine("PgType: ${pgType.name}")
        if (oid != null) appendLine("OID: $oid")
        if (kotlinClass != null) appendLine("Kotlin Class: ${kotlinClass.qualifiedName}")
    }

    companion object {
        private fun formatValue(value: Any?): String {
            if (value == null) return "null"
            if (value is ByteArray) {
                return "ByteArray(${value.size} bytes)"
            }
            val str = value.toString()
            return if (str.length > 100) str.substring(0, 100) + "..." else str
        }
    }
}
