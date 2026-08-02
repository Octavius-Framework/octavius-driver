package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgTyped
import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.isKnownOid
import kotlin.reflect.KClass

class ParameterConverterRegistry(
    private val parent: ParameterConverterRegistry? = null
) {
    private val converters = mutableListOf<ParameterConverter<*>>()

    fun addConverter(converter: ParameterConverter<*>) {
        converters.add(0, converter)
    }

    fun convert(source: Any, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any? {
        for (i in 0 until converters.size) {
            val converter = converters[i]
            if (converter.canConvert(source::class, expectedOid, typeManager)) {
                @Suppress("UNCHECKED_CAST")
                var result = (converter as ParameterConverter<Any>).convert(source, expectedOid, context, typeManager)
                if (result !is PgTyped && !expectedOid.isKnownOid) {
                    val defaultOid = converter.getDefaultOid(typeManager)
                    if (defaultOid.isKnownOid) {
                        val type = typeManager.registry.types[defaultOid]
                        if (type != null) {
                            result = PgTyped(result, QualifiedName(type.schema, type.name, false))
                        }
                    }
                }
                return result
            }
        }

        return parent?.convert(source, expectedOid, context, typeManager) ?: source
    }

    fun findConverter(source: Any, expectedOid: Int, typeManager: TypeManager): ParameterConverter<Any>? {
        return findConverterByClass(source::class, expectedOid, typeManager)
    }

    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): ParameterConverter<Any>? {
        for (i in 0 until converters.size) {
            val converter = converters[i]
            if (converter.canConvert(sourceClass, expectedOid, typeManager)) {
                @Suppress("UNCHECKED_CAST")
                return converter as ParameterConverter<Any>
            }
        }
        return parent?.findConverterByClass(sourceClass, expectedOid, typeManager)
    }
}