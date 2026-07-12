package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.container.PgRange
import io.github.octaviusframework.driver.container.PgRecord
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterMapper
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager

data class SerializedParameter(val oid: Int, val value: ByteArray?)

class ParameterSerializer(
    private val typeManager: TypeManager,
    private val parameterMapper: ParameterMapper
) {
    private val typeRegistry = typeManager.registry

    fun serialize(parameter: Any?): ByteArray? {
        return serializeWithOid(parameter).value
    }

    fun getOid(parameter: Any?): Int {
        return serializeWithOid(parameter).oid
    }

    /**
     * Returns a complete object representing the parameter with all information for the QueryExecutor.
     * Combines OID resolution and value serialization into a single pass to avoid redundant type conversions.
     */
    private data class SerializationResult(val oid: Int, val isNull: Boolean)

    private fun serializeValue(parameter: Any?, writer: PgByteWriter): SerializationResult {
        if (parameter == null) return SerializationResult(0, true)

        val convertedParameter = parameterMapper.convert(parameter) ?: return SerializationResult(0, true)

        if (convertedParameter is PgTyped) {
            val paramValue = convertedParameter.value ?: return SerializationResult(0, true)
            val resolvedOid = typeManager.resolveOid(
                convertedParameter.pgType.name,
                convertedParameter.pgType.schema,
                convertedParameter.pgType.isArray
            )
            
            val convertedValue = parameterMapper.convert(paramValue, resolvedOid) ?: return SerializationResult(resolvedOid, true)
            
            val codec = typeRegistry.getCodecByOid<Any>(resolvedOid)
            if (codec != null) {
                if (!codec.kotlinClass.isInstance(convertedValue)) {
                    throw OctaviusTypeException(
                        TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                        oid = resolvedOid,
                        details = "Type mismatch. Attempting to serialize value of type ${convertedValue::class.qualifiedName} using codec for ${codec.kotlinClass.qualifiedName}"
                    )
                }
                codec.toBinary(convertedValue, writer)
                return SerializationResult(resolvedOid, false)
            }
            
            // Fallback for containers or missing OID codecs
            val fallback = serializeValue(convertedValue, writer)
            return SerializationResult(resolvedOid, fallback.isNull)
        }

        if (convertedParameter is PgContainer) {
            val oid = when (convertedParameter) {
                is PgComposite -> convertedParameter.type.oid
                is PgArray -> convertedParameter.arrayOid
                is PgRange -> convertedParameter.rangeOid
                is PgMultirange -> convertedParameter.multirangeOid
                is PgRecord -> 2249
                else -> 0
            }
            ContainerCodec.serializeContainer(convertedParameter, writer, typeRegistry)
            return SerializationResult(oid, false)
        }

        val codec = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                details = "Nie znaleziono serializatora (codecu) dla typu: ${convertedParameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anyCodec = codec as TypeCodec<Any>
        anyCodec.toBinary(convertedParameter, writer)
        
        val oid = typeRegistry.getOidForCodec(codec) ?: 0
        return SerializationResult(oid, false)
    }

    /**
     * Returns a complete object representing the parameter with all information for the QueryExecutor.
     * Combines OID resolution and value serialization into a single pass to avoid redundant type conversions.
     */
    fun serializeWithOid(parameter: Any?): SerializedParameter {
        val writer = PgByteWriter()
        val result = serializeValue(parameter, writer)
        return if (result.isNull) {
            SerializedParameter(result.oid, null)
        } else {
            SerializedParameter(result.oid, writer.toByteArray())
        }
    }

    /**
     * Serializes all parameters into a single byte array representing the payload for BindMessage.
     * Each non-null parameter value is prefixed with its 4-byte length.
     * Null parameters are represented by a 4-byte -1 length.
     */
    fun serializeAll(parameters: List<Any?>): Pair<List<Int>, ByteArray> {
        val oids = ArrayList<Int>(parameters.size)
        val writer = PgByteWriter()

        for (param in parameters) {
            val marker = writer.reserveLengthInt()
            val result = serializeValue(param, writer)
            oids.add(result.oid)

            if (result.isNull) {
                writer.updatePosition(marker)
                writer.writeInt(-1)
            } else {
                writer.fillLengthInt(marker)
            }
        }

        return oids to writer.toByteArray()
    }
}

