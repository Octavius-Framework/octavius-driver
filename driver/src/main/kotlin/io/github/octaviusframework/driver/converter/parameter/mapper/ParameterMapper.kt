package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.KClass

class ParameterMapper(
    private val registry: ParameterConverterRegistry,
    private val typeManager: TypeManager
) {
    fun convert(source: Any?, expectedOid: Int): Any? {
        if (source == null) return null
        val context = DefaultSerializationContext(registry, typeManager)
        return context.convert(source, expectedOid)
    }
}

internal class DefaultSerializationContext(
    private val registry: ParameterConverterRegistry,
    override val typeManager: TypeManager
) : SerializationContext {
    override fun convert(source: Any, expectedOid: Int): Any? {
        return registry.convert(source, expectedOid, this)
    }

    override fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>? {
        return registry.findConverter(source, expectedOid, this)
    }

    override fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>? {
        return registry.findConverterByClass(sourceClass, expectedOid, this)
    }
}

