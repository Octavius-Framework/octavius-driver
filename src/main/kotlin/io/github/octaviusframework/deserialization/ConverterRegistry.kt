package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

import io.github.octaviusframework.types.PgType

class ConverterRegistry(
    private val parent: ConverterRegistry? = null
) {
    private val converters = mutableListOf<PgConverter<*>>()

    fun addConverter(converter: PgConverter<*>) {
        // Dodawanie na początek, aby nowsze konwertery miały wyższy priorytet
        converters.add(0, converter)
    }

    fun findConverter(source: Any, expectedType: KType, sourceType: PgType? = null): PgConverter<*>? {
        val converter = converters.find { it.canConvert(source, expectedType, sourceType) }
        return converter ?: parent?.findConverter(source, expectedType, sourceType)
    }
}
