package io.github.octaviusframework.driver.mapping.result

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KType

class ResultConverterRegistry(
    private val parent: ResultConverterRegistry? = null
) {
    private val converters = mutableListOf<ResultConverter<*>>()

    fun addConverter(converter: ResultConverter<*>) {
        // Adding to the beginning so that newer converters have higher priority
        converters.add(0, converter)
    }

    fun findConverter(source: Any, expectedType: KType, sourceType: PgType): ResultConverter<*>? {
        val converter = converters.find { it.canConvert(source, expectedType, sourceType) }
        return converter ?: parent?.findConverter(source, expectedType, sourceType)
    }
}