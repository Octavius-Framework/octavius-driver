package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

class ConverterRegistry(
    private val parent: ConverterRegistry? = null
) {
    private val converters = mutableListOf<PgConverter<*>>()

    fun addConverter(converter: PgConverter<*>) {
        // Dodawanie na początek, aby nowsze konwertery miały wyższy priorytet
        converters.add(0, converter)
    }

    fun findConverter(source: Any, expectedType: KType): PgConverter<*>? {
        val converter = converters.find { it.canConvert(source, expectedType) }
        return converter ?: parent?.findConverter(source, expectedType)
    }
}
