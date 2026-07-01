package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.mapping.parameter.ParameterConverterRegistry
import io.github.octaviusframework.driver.mapping.parameter.SerializationContext
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.containter.*

data class SerializedParameter(val oid: UInt, val value: ByteArray?)

class ParameterSerializer(
    private val typeManager: TypeManager,
    private val parameterConverterRegistry: ParameterConverterRegistry
) {
    private val typeRegistry = typeManager.registry

    private val context = object : SerializationContext {
        override fun convert(source: Any, expectedOid: UInt?): Any? {
            return parameterConverterRegistry.convert(source, expectedOid, this, typeManager)
        }
    }


    fun serialize(parameter: Any?): ByteArray? {
        if (parameter == null) {
            return null
        }

        if (parameter is PgTyped) {
            val paramValue = parameter.value ?: return null
            val (resolvedOid, _) = typeRegistry.resolveOid(parameter.pgType.name, parameter.pgType.schema, emptyList(), parameter.pgType.isArray)
            
            val convertedValue = parameterConverterRegistry.convert(paramValue, resolvedOid, context, typeManager) ?: return null
            
            val codec = typeRegistry.getCodecByOid<Any>(resolvedOid)
            if (codec != null) {
                if (!codec.kotlinClass.isInstance(convertedValue)) {
                    throw OctaviusTypeException(
                        TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                        oid = resolvedOid,
                        details = "Type mismatch. Attempting to serialize value of type ${convertedValue::class.qualifiedName} using codec for ${codec.kotlinClass.qualifiedName}"
                    )
                }
                return codec.toBinary(convertedValue)
            }
            
            // Fallback for containers or missing OID codecs
            return serialize(convertedValue)
        }

        val convertedParameter = parameterConverterRegistry.convert(parameter, null, context, typeManager) ?: return null

        if (convertedParameter is PgTyped) {
            return serialize(convertedParameter)
        }

        if (convertedParameter is PgContainer) {
            val writer = PgByteWriter()
            ContainerCodec.serializeContainer(convertedParameter, writer, typeRegistry)
            return writer.toByteArray()
        }

        val codec = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                details = "Nie znaleziono serializatora dla typu: ${convertedParameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anyCodec = codec as TypeCodec<Any>
        return anyCodec.toBinary(convertedParameter)
    }

    fun getOid(parameter: Any?): UInt {
        if (parameter == null) return 0u // Unspecified type

        if (parameter is PgTyped) {
            val (resolvedOid, _) = typeRegistry.resolveOid(parameter.pgType.name, parameter.pgType.schema, emptyList(), parameter.pgType.isArray)
            return resolvedOid
        }



        val convertedParameter = parameterConverterRegistry.convert(parameter, null, context, typeManager) ?: return 0u

        if (convertedParameter is PgTyped) {
            return getOid(convertedParameter)
        }

        if (convertedParameter is PgContainer) {
            return when (convertedParameter) {
                is PgComposite -> convertedParameter.type.oid
                is PgArray -> convertedParameter.arrayOid
                is PgRange -> convertedParameter.rangeOid
                is PgMultirange -> convertedParameter.multirangeOid
                else -> 0u
            }
        }

        val codec = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                details = "Nie znaleziono handlera dla typu: ${convertedParameter::class.qualifiedName}"
            )

        return codec.oid!!
    }

    /**
     * Returns a complete object representing the parameter with all information for the QueryExecutor.
     */
    fun serializeWithOid(parameter: Any?): SerializedParameter {
        return SerializedParameter(getOid(parameter), serialize(parameter))
    }

    /**
     * Serializes the list of parameters and returns two separate lists: OIDs and their binary representations,
     * facilitating direct integration into `QueryExecutor.query(...)`.
     */
    fun serializeAll(parameters: List<Any?>): Pair<List<UInt>, List<ByteArray?>> {
        val oids = parameters.map { getOid(it) }
        val values = parameters.map { serialize(it) }
        return oids to values
    }
}
