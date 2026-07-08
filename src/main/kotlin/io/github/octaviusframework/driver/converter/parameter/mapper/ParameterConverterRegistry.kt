package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager

class ParameterConverterRegistry(
    private val parent: ParameterConverterRegistry? = null
) {
    private val converters = mutableListOf<ParameterConverter<*>>()

    fun addConverter(converter: ParameterConverter<*>) {
        converters.add(0, converter)
    }

    fun convert(source: Any, expectedOid: Int?, context: SerializationContext, typeManager: TypeManager): Any? {
        for (i in 0 until converters.size) {
            val converter = converters[i]
            if (converter.canConvert(source, expectedOid, typeManager)) {
                val result = converter.convert(source, expectedOid, context, typeManager)
                if (result != null) return result
            }
        }

        return parent?.convert(source, expectedOid, context, typeManager) ?: source
    }
}