package io.github.octaviusframework.driver.mapping.parameter

import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.type.containter.ContainerField
import io.github.octaviusframework.driver.type.containter.PgComposite
import io.github.octaviusframework.driver.type.containter.PgContainer
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class ReflectionCompositeParameterConverter : ParameterConverter<Any> {
    override fun canConvert(source: Any, expectedOid: UInt?, typeRegistry: TypeRegistry): Boolean {
        if (!source::class.isData) return false
        
        val qName = typeRegistry.registeredComposites[source::class]
        if (qName != null) return true

        if (expectedOid != null) {
            return typeRegistry.types[expectedOid] is PgType.Composite
        }

        return false
    }

    override fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeRegistry: TypeRegistry): Any? {
        val type = if (expectedOid != null) {
            typeRegistry.types[expectedOid] as PgType.Composite
        } else {
            val qName = typeRegistry.registeredComposites[source::class] ?: return null
            typeRegistry.types.values.first { 
                it is PgType.Composite && it.name == qName.name && (qName.schema.isEmpty() || it.schema == qName.schema)
            } as PgType.Composite
        }

        val properties = source::class.memberProperties.associateBy { it.name.lowercase() }
        
        val fields = type.attributes.map { (attrName, attributeOid) ->
            val prop = properties[attrName.lowercase()]
                ?: properties[attrName.replace("_", "").lowercase()]
            
            var value = if (prop != null) {
                prop.isAccessible = true
                prop.call(source)
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
