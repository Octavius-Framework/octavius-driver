package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager

class ParameterMapper(
    private val registry: ParameterConverterRegistry,
    private val typeManager: TypeManager
) {
    fun convert(source: Any?, expectedOid: UInt? = null): Any? {
        if (source == null) return null
        val context = DefaultSerializationContext(registry, typeManager)
        return context.convert(source, expectedOid)
    }
}

internal class DefaultSerializationContext(
    private val registry: ParameterConverterRegistry,
    private val typeManager: TypeManager
) : SerializationContext {
    override fun convert(source: Any, expectedOid: UInt?): Any? {
        return registry.convert(source, expectedOid, this, typeManager)
    }
}
