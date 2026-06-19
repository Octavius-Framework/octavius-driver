package io.github.octaviusframework.driver.query

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.codec.dynamic.ContainerCodec
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.PgTypedParameter
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgComposite
import io.github.octaviusframework.driver.type.containter.PgContainer
import io.github.octaviusframework.driver.type.containter.PgMultirange
import io.github.octaviusframework.driver.type.containter.PgRange

import io.github.octaviusframework.driver.mapping.parameter.ParameterConverterRegistry
import io.github.octaviusframework.driver.mapping.parameter.SerializationContext

data class SerializedParameter(val oid: UInt, val value: ByteArray?)

class ParameterSerializer(
    private val typeRegistry: TypeRegistry,
    private val parameterConverterRegistry: ParameterConverterRegistry
) {
    private val context = object : SerializationContext {
        override fun convert(source: Any, expectedOid: UInt?): Any? {
            return parameterConverterRegistry.convert(source, expectedOid, this, typeRegistry)
        }
    }


    fun serialize(parameter: Any?): ByteArray? {
        if (parameter == null) {
            return null
        }

        if (parameter is PgTyped) {
            val (resolvedOid, _) = typeRegistry.resolveOid(parameter.pgType.name, parameter.pgType.schema, emptyList(), parameter.pgType.isArray)
            return serialize(PgTypedParameter(parameter.value, resolvedOid))
        }

        if (parameter is PgTypedParameter) {
            val paramValue = parameter.value ?: return null
            
            val convertedValue = parameterConverterRegistry.convert(paramValue, parameter.oid, context, typeRegistry) ?: return null
            
            val serializer = typeRegistry.getCodecByOid<Any>(parameter.oid)
            if (serializer != null) {
                return serializer.toBinary(convertedValue)
            }
            
            // Fallback for containers or missing OID serializers
            return serialize(convertedValue)
        }

        val convertedParameter = parameterConverterRegistry.convert(parameter, null, context, typeRegistry) ?: return null

        if (convertedParameter is PgContainer) {
            val writer = PgByteWriter()
            ContainerCodec.serializeContainer(convertedParameter, writer, typeRegistry)
            return writer.toByteArray()
        }

        val serializer = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                details = "Nie znaleziono serializatora dla typu: ${convertedParameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anySerializer = serializer as TypeCodec<Any>
        return anySerializer.toBinary(convertedParameter)
    }

    fun getOid(parameter: Any?): UInt {
        if (parameter == null) return 0u // Unspecified type

        if (parameter is PgTyped) {
            val (resolvedOid, _) = typeRegistry.resolveOid(parameter.pgType.name, parameter.pgType.schema, emptyList(), parameter.pgType.isArray)
            return resolvedOid
        }

        if (parameter is PgTypedParameter) {
            return parameter.oid
        }

        val convertedParameter = parameterConverterRegistry.convert(parameter, null, context, typeRegistry) ?: return 0u

        if (convertedParameter is PgContainer) {
            return when (convertedParameter) {
                is PgComposite -> convertedParameter.type.oid
                is PgArray -> convertedParameter.arrayOid
                is PgRange -> convertedParameter.rangeOid
                is PgMultirange -> convertedParameter.multirangeOid
                else -> 0u
            }
        }

        val serializer = typeRegistry.getCodecByClass(convertedParameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                details = "Nie znaleziono handlera dla typu: ${convertedParameter::class.qualifiedName}"
            )

        return serializer.oid!!
    }

    /**
     * Zwraca pełen obiekt reprezentujący parametr ze wszystkimi informacjami dla QueryExecutor'a.
     */
    fun serializeWithOid(parameter: Any?): SerializedParameter {
        return SerializedParameter(getOid(parameter), serialize(parameter))
    }

    /**
     * Serializuje listę parametrów i zwraca dwie osobne listy: OID'y i ich binarne reprezentacje,
     * ułatwiając bezpośrednie wpięcie do `QueryExecutor.query(...)`.
     */
    fun serializeAll(parameters: List<Any?>): Pair<List<UInt>, List<ByteArray?>> {
        val oids = parameters.map { getOid(it) }
        val values = parameters.map { serialize(it) }
        return oids to values
    }
}
