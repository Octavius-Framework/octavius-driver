package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

class ResultConverterRegistry(
    private val parent: ResultConverterRegistry? = null
) {
    private val converters = mutableMapOf<KClass<*>, MutableList<ResultConverter<*, *>>>()

    fun addConverter(converter: ResultConverter<*, *>) {
        val list = converters.getOrPut(converter.supportedSourceClass) { mutableListOf() }
        // Adding to the beginning so that newer converters have higher priority
        list.add(0, converter)
    }

    @Suppress("UNCHECKED_CAST")
    fun findConverter(source: Any, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>? {
        val sourceClass = source::class
        
        val specificConverters = converters[sourceClass]
        if (specificConverters != null) {
            val converter = specificConverters.find { (it as ResultConverter<Any, *>).canConvert(source, expectedType, sourceType) }
            if (converter != null) return converter as ResultConverter<Any, *>
        }

        val anyConverters = converters[Any::class]
        if (anyConverters != null) {
            val converter = anyConverters.find { (it as ResultConverter<Any, *>).canConvert(source, expectedType, sourceType) }
            if (converter != null) return converter as ResultConverter<Any, *>
        }

        return parent?.findConverter(source, expectedType, sourceType)
    }
}