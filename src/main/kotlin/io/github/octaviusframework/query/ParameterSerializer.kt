package io.github.octaviusframework.query

import io.github.octaviusframework.container.*
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage
import io.github.octaviusframework.io.PgByteWriter
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeHandler
import io.github.octaviusframework.types.TypeRegistry

data class SerializedParameter(val oid: UInt, val value: ByteArray?)

class ParameterSerializer(private val typeRegistry: TypeRegistry) {

    fun serialize(parameter: Any?): ByteArray? {
        if (parameter == null) {
            return null
        }

        if (parameter is PgContainer) {
            val writer = PgByteWriter()
            ContainerSerializers.serializeContainer(parameter, writer, typeRegistry)
            return writer.toByteArray()
        }

        val handler = typeRegistry.getHandlerByClass(parameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_HANDLER,
                details = "Nie znaleziono handlera dla typu: ${parameter::class.qualifiedName}"
            )

        @Suppress("UNCHECKED_CAST")
        val anyHandler = handler as TypeHandler<Any>
        return anyHandler.toBinary(parameter)
    }

    fun getOid(parameter: Any?): UInt {
        if (parameter == null) return 0u // Unspecified type

        if (parameter is PgContainer) {
            return when (parameter) {
                is PgComposite -> parameter.type.oid
                is PgArray -> {
                    val arrayType = typeRegistry.types.values.firstOrNull { 
                        it is PgType.Array && it.elementOid == parameter.elementOid
                    } ?: throw OctaviusTypeException(
                        TypeExceptionMessage.TYPE_NOT_FOUND,
                        details = "Nie znaleziono typu tablicowego dla elementOid = ${parameter.elementOid}"
                    )
                    arrayType.oid
                }
                is PgRange -> {
                    val rangeType = typeRegistry.types.values.firstOrNull { 
                        it is PgType.Range && it.subtypeOid == parameter.elementOid
                    } ?: throw OctaviusTypeException(
                        TypeExceptionMessage.TYPE_NOT_FOUND,
                        details = "Nie znaleziono typu range dla subtypeOid = ${parameter.elementOid}"
                    )
                    rangeType.oid
                }
                is PgMultirange -> {
                    0u //TODO
                }
                else -> 0u
            }
        }

        val handler = typeRegistry.getHandlerByClass(parameter::class)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_HANDLER,
                details = "Nie znaleziono handlera dla typu: ${parameter::class.qualifiedName}"
            )

        return handler.oid
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
