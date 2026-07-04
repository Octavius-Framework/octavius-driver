package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager

class ParameterConverterRegistry(
    private val parent: ParameterConverterRegistry? = null
) {
    private val converters = mutableListOf<ParameterConverter<*>>()

    fun addConverter(converter: ParameterConverter<*>) {
        converters.add(0, converter)
    }

    fun convert(source: Any, expectedOid: UInt?, context: SerializationContext, typeManager: TypeManager): Any? {
        val converter = converters.firstOrNull { it.canConvert(source, expectedOid, typeManager) }
        val result = converter?.convert(source, expectedOid, context, typeManager)
        if (result != null) return result

        return parent?.convert(source, expectedOid, context, typeManager) ?: source
    }
}