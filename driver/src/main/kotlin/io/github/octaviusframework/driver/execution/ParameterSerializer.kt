package io.github.octaviusframework.driver.execution

import io.github.octaviusframework.driver.io.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.encodeSafely
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionReason
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.registry.TypeManager
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid

/**
 * The longest a single parameter may serialise to.
 *
 * `MaxAllocSize` is `2^30 - 1`, and every variable-length value carries a four-byte header inside
 * that budget - two of whose bits are flags, which is why the same number bounds the length field
 * itself.
 */
private const val MAX_PARAMETER_LENGTH = 1073741819

class ParameterSerializer internal constructor(
    private val typeManager: TypeManager,
    private val parameterMapper: ParameterMapper
) {
    private val codecDictionary = typeManager.codecDictionary

    internal fun serializeAll(parameters: Array<out Any?>, writer: PgByteWriter): IntArray {
        writer.clear()
        val size = parameters.size
        val oids = IntArray(size)

        for (i in 0 until size) {
            val marker = writer.reserveLengthInt()
            oids[i] = serializeValue(parameters[i], writer, marker)
            val length = writer.position - marker - 4
            if (length > MAX_PARAMETER_LENGTH) {
                throw InvalidOperationException(
                    InvalidOperationExceptionReason.INVALID_ARGUMENT,
                    "Parameter \$${i + 1} serialized to $length bytes; PostgreSQL refuses a value above $MAX_PARAMETER_LENGTH"
                )
            }
        }

        return oids
    }

    private fun serializeValue(parameter: Any?, writer: PgByteWriter, marker: Int): Int {
        var oid = UNRESOLVED_OID
        var value = parameter

        if (value is PgTyped) {
            oid = typeManager.resolveOid(value.pgType.name, value.pgType.schema, value.pgType.isArray)
            value = value.value
        }

        if (value != null && value is PgContainer) {
            return writeKnown(value, value.containerOid, writer, marker)
        }

        value = parameterMapper.convert(value, oid)

        if (value is PgTyped) {
            oid = typeManager.resolveOid(value.pgType.name, value.pgType.schema, value.pgType.isArray)
            value = value.value
        }

        if (value == null) {
            writer.updatePosition(marker)
            writer.writeInt(-1)
            return oid
        }

        if (value is PgContainer) {
            return writeKnown(value, value.containerOid, writer, marker)
        }

        return if (oid.isKnownOid) {
            writeKnown(value, oid, writer, marker)
        } else {
            writeStandard(value, writer, marker)
        }
    }

    private fun writeKnown(value: Any, oid: Int, writer: PgByteWriter, marker: Int): Int {
        val codec = codecDictionary.getCodecByOid<Any>(oid)
            ?: throw TypeException(TypeExceptionReason.MISSING_CODEC, oid = oid, details = "Codec not found")

        codec.encodeSafely(value, writer)
        writer.fillLengthInt(marker)
        return oid
    }

    private fun writeStandard(value: Any, writer: PgByteWriter, marker: Int): Int {
        val codec = codecDictionary.getCodecByClass(value::class)
            ?: throw TypeException(TypeExceptionReason.MISSING_CODEC, details = "Codec not found for: ${value::class.qualifiedName}")

        @Suppress("UNCHECKED_CAST")
        (codec as TypeCodec<Any>).encodeSafely(value, writer)
        writer.fillLengthInt(marker)

        return codecDictionary.getOidForCodec(codec) ?: typeManager.resolveOid(codec.pgTypeName, codec.pgSchema)
    }
}
