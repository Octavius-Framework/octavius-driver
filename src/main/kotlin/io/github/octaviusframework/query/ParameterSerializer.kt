package io.github.octaviusframework.query

import io.github.octaviusframework.container.ContainerSerializers
import io.github.octaviusframework.container.PgContainer
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage
import io.github.octaviusframework.io.PgByteWriter
import io.github.octaviusframework.types.TypeHandler
import io.github.octaviusframework.types.TypeRegistry

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
}
