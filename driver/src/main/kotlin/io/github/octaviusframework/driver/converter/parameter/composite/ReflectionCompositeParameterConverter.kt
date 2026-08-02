package io.github.octaviusframework.driver.converter.parameter.composite

import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import io.github.octaviusframework.driver.type.isKnownOid

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.parameter.mapper.ParameterConverter
import io.github.octaviusframework.driver.converter.parameter.mapper.SerializationContext
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.exception.OctaviusInternalException
import kotlin.reflect.KClass
import kotlin.reflect.jvm.isAccessible

class ReflectionCompositeParameterConverter : ParameterConverter<Any> {
    override val supportedClass: KClass<Any> = Any::class

    override fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        if (!sourceClass.isData) return false
        val typeRegistry = typeManager.registry

        val registration = typeRegistry.registeredComposites[sourceClass]
        if (registration != null) return true

        if (expectedOid.isKnownOid) {
            return typeRegistry.types[expectedOid] is PgType.Composite
        }

        return false
    }

    override fun convert(source: Any, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any {
        val typeRegistry = typeManager.registry
        val registration = typeRegistry.registeredComposites[source::class] ?: throw OctaviusInternalException()

        val type = if (expectedOid.isKnownOid) {
            typeRegistry.types[expectedOid] as PgType.Composite
        } else {
            val qName = registration.qualifiedName
            typeRegistry.types[typeManager.resolveOid(qName.name, qName.schema)] as PgType.Composite
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

            value
        }.toTypedArray()

        return PgComposite(type, fields, typeRegistry)
    }
}

