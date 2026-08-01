package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager

class ParameterSerializer(
    private val typeManager: TypeManager,
    private val parameterMapper: ParameterMapper
) {
    private val typeRegistry = typeManager.registry

    fun serializeAll(parameters: List<Any?>): Pair<IntArray, ByteArray> {
        val size = parameters.size
        val oids = IntArray(size)
        val writer = PgByteWriter()

        for (i in 0 until size) {
            val marker = writer.reserveLengthInt()
            oids[i] = serializeValue(parameters[i], writer, marker)
        }

        return oids to writer.toByteArray()
    }

    private fun serializeValue(parameter: Any?, writer: PgByteWriter, marker: Int): Int {
        var oid = 0
        var value = parameter

        if (value is PgTyped) {
            oid = typeManager.resolveOid(value.pgType.name, value.pgType.schema, value.pgType.isArray)
            value = value.value
        }

        if (value != null && value is PgContainer) {
            return writeKnown(value, value.containerOid, writer, marker)
        }

        value = parameterMapper.convert(value, if (oid == 0) null else oid)

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

        return if (oid != 0) {
            writeKnown(value, oid, writer, marker)
        } else {
            writeStandard(value, writer, marker)
        }
    }

    private fun writeKnown(value: Any, oid: Int, writer: PgByteWriter, marker: Int): Int {
        val codec = typeRegistry.getCodecByOid<Any>(oid)
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, oid = oid, details = "Codec not found")

        if (!codec.kotlinClass.isInstance(value)) {
            throw OctaviusTypeException(
                TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                oid = oid,
                details = "Type mismatch: ${value::class.qualifiedName} != ${codec.kotlinClass.qualifiedName}"
            )
        }

        codec.toBinary(value, writer)
        writer.fillLengthInt(marker)
        return oid
    }

    private fun writeStandard(value: Any, writer: PgByteWriter, marker: Int): Int {
        val codec = typeRegistry.getCodecByClass(value::class)
            ?: throw OctaviusTypeException(TypeExceptionMessage.MISSING_CODEC, details = "Codec not found for: ${value::class.qualifiedName}")

        @Suppress("UNCHECKED_CAST")
        (codec as TypeCodec<Any>).toBinary(value, writer)
        writer.fillLengthInt(marker)

        return typeRegistry.getOidForCodec(codec) ?: 0
    }
}