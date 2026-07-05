package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

class ResultConverterRegistry(
    private val parent: ResultConverterRegistry? = null
) {
    private val converters = mutableMapOf<KClass<*>, MutableList<ResultConverter<*>>>()

    fun addConverter(converter: ResultConverter<*>) {
        val list = converters.getOrPut(converter.supportedSourceClass) { mutableListOf() }
        // Adding to the beginning so that newer converters have higher priority
        list.add(0, converter)
    }

    fun findConverter(source: Any, expectedType: KType, sourceType: PgType): ResultConverter<*>? {
        val sourceClass = source::class
        
        val specificConverters = converters[sourceClass]
        if (specificConverters != null) {
            val converter = specificConverters.find { it.canConvert(source, expectedType, sourceType) }
            if (converter != null) return converter
        }

        val anyConverters = converters[Any::class]
        if (anyConverters != null) {
            val converter = anyConverters.find { it.canConvert(source, expectedType, sourceType) }
            if (converter != null) return converter
        }

        return parent?.findConverter(source, expectedType, sourceType)
    }
}