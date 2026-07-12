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
    fun serializeWithOid(parameter: Any?): SerializedParameter {
        if (parameter == null) return SerializedParameter(0, null)

        val convertedParameter = parameterMapper.convert(parameter) ?: return SerializedParameter(0, null)

        if (convertedParameter is PgTyped) {
            val paramValue = convertedParameter.value ?: return SerializedParameter(0, null)
            val (resolvedOid, _) = typeManager.resolveOid(
                convertedParameter.pgType.name,
                convertedParameter.pgType.schema,
                convertedParameter.pgType.isArray
            )
            
            val convertedValue = parameterMapper.convert(paramValue, resolvedOid) ?: return SerializedParameter(resolvedOid, null)
            
            val codec = typeRegistry.getCodecByOid<Any>(resolvedOid)
            if (codec != null) {
                if (!codec.kotlinClass.isInstance(convertedValue)) {
                    throw OctaviusTypeException(
                        TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                        oid = resolvedOid,
                        details = "Type mismatch. Attempting to serialize value of type ${convertedValue::class.qualifiedName} using codec for ${codec.kotlinClass.qualifiedName}"
                    )
                }
                val writer = PgByteWriter()
                codec.toBinary(convertedValue, writer)
                return SerializedParameter(resolvedOid, writer.toByteArray())
            }
            
            // Fallback for containers or missing OID codecs
            val fallback = serializeWithOid(convertedValue)
            return SerializedParameter(resolvedOid, fallback.value)
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
            val writer = PgByteWriter()
            ContainerCodec.serializeContainer(convertedParameter, writer, typeRegistry)
            return SerializedParameter(oid, writer.toByteArray())
        }

        val codec = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                details = "Nie znaleziono serializatora (codecu) dla typu: ${convertedParameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anyCodec = codec as TypeCodec<Any>
        val writer = PgByteWriter()
        anyCodec.toBinary(convertedParameter, writer)
        
        val oid = typeRegistry.getOidForCodec(codec) ?: 0
        return SerializedParameter(oid, writer.toByteArray())
    }

    /**
     * Serializes the list of parameters and returns two separate lists: OIDs and their binary representations,
     * facilitating direct integration into `QueryExecutor`.
     */
    fun serializeAll(parameters: List<Any?>): Pair<List<Int>, List<ByteArray?>> {
        val oids = ArrayList<Int>(parameters.size)
        val values = ArrayList<ByteArray?>(parameters.size)

        for (param in parameters) {
            val serialized = serializeWithOid(param)
            oids.add(serialized.oid)
            values.add(serialized.value)
        }

        return oids to values
    }
}

