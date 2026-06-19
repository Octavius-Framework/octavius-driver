package io.github.octaviusframework.driver.mapping.parameter

import io.github.octaviusframework.driver.type.TypeRegistry

class ParameterConverterRegistry {
    private val converters = mutableListOf<ParameterConverter<*>>()

    fun addConverter(converter: ParameterConverter<*>) {
        converters.add(0, converter)
    }

    fun convert(source: Any, expectedOid: UInt?, typeRegistry: TypeRegistry): Any? {
        val converter = converters.firstOrNull { it.canConvert(source, expectedOid, typeRegistry) }
        return converter?.convert(source, expectedOid, typeRegistry) ?: source
    }
}
