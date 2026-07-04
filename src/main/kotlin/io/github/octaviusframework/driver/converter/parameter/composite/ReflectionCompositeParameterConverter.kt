package io.github.octaviusframework.driver.converter.parameter.composite

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.container.ContainerField
import io.github.octaviusframework.driver.type.container.PgComposite
import io.github.octaviusframework.driver.type.container.PgContainer
import kotlin.reflect.KClass
import kotlin.reflect.jvm.isAccessible

class ReflectionCompositeParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: UInt?, typeManager: TypeManager): Boolean {
        if (!source::class.isData) return false
        val typeRegistry = typeManager.registry

        val registration = typeRegistry.registeredComposites[source::class]
        if (registration != null) return true

        if (expectedOid != null) {
            return typeRegistry.types[expectedOid] is PgType.Composite
        }

        return false
    }

    override fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeManager: TypeManager): Any? {
        val typeRegistry = typeManager.registry
        val registration = typeRegistry.registeredComposites[source::class]

        val type = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as PgType.Composite
        } else {
            if (registration == null) return null
            val qName = registration.qualifiedName
            typeRegistry.types.values.first {
                it is PgType.Composite && it.name == qName.name && (qName.schema.isEmpty() || it.schema == qName.schema)
            } as PgType.Composite
        }

        if (registration == null) {
            throw IllegalArgumentException("Composite not registered for ${source::class}")
        }

        @Suppress("UNCHECKED_CAST")
        val metadata = ReflectionCompositeCache.getOrCreateDataObjectMetadata(
            source::class as KClass<Any>,
            registration.pgConvention,
            registration.kotlinConvention
        )

        val propertiesByMapKey = metadata.constructorProperties.associateBy { it.keyName }

        val fields = type.attributes.map { (attrName, attributeOid) ->
            val meta = propertiesByMapKey[attrName]

            var value = if (meta != null) {
                meta.property.isAccessible = true
                meta.property.get(source)
            } else null

            if (value != null) {
                value = context.convert(value, attributeOid)
            }

            if (value is PgContainer) {
                ContainerField(rawValue = null, container = value, value = null)
            } else {
                ContainerField(rawValue = null, container = null, value = value)
            }
        }

        return PgComposite(type, fields, typeRegistry)
    }
}