package io.github.octaviusframework.query

import io.github.octaviusframework.container.*
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage
import io.github.octaviusframework.io.PgByteWriter
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeSerializer
import io.github.octaviusframework.types.TypeRegistry

data class SerializedParameter(val oid: UInt, val value: ByteArray?)

class ParameterSerializer(private val typeRegistry: TypeRegistry) {

    fun serialize(parameter: Any?): ByteArray? {
        if (parameter == null) {
            return null
        }

        if (parameter is PgTypedParameter) {
            if (parameter.value == null) return null
            
            val serializer = typeRegistry.getSerializerByOid<Any>(parameter.oid)
            if (serializer != null) {
                return serializer.toBinary(parameter.value)
            }
            
            // Fallback for containers or missing OID serializers
            return serialize(parameter.value)
        }

        if (parameter is PgContainer) {
            val writer = PgByteWriter()
            ContainerSerializers.serializeContainer(parameter, writer, typeRegistry)
            return writer.toByteArray()
        }

        val serializer = typeRegistry.getSerializerByClass(parameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                details = "Nie znaleziono serializatora dla typu: ${parameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anySerializer = serializer as TypeSerializer<Any>
        return anySerializer.toBinary(parameter)
    }

    fun getOid(parameter: Any?): UInt {
        if (parameter == null) return 0u // Unspecified type

        if (parameter is PgTypedParameter) {
            return parameter.oid
        }

        if (parameter is PgContainer) {
            return when (parameter) {
                is PgComposite -> parameter.type.oid
                is PgArray -> parameter.arrayOid
                is PgRange -> parameter.rangeOid
                is PgMultirange -> parameter.multirangeOid
                else -> 0u
            }
        }

        val serializer = typeRegistry.getSerializerByClass(parameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_SERIALIZER,
                details = "Nie znaleziono handlera dla typu: ${parameter::class.qualifiedName}"
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
