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
    fun findConverter(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): ResultConverter<Any, *>? {
        val specificConverters: List<ResultConverter<*,*>>? = converters[sourceClass]
        if (specificConverters != null) {
            for (i in 0 until specificConverters.size) {
                @Suppress("UNCHECKED_CAST")
                val converter = specificConverters[i] as ResultConverter<Any, *>
                if (converter.canConvert(sourceClass, expectedType, sourceType, context)) {
                    return converter
                }
            }
        }

        val anyConverters: List<ResultConverter<*,*>>? = converters[Any::class]
        if (anyConverters != null) {
            for (i in 0 until anyConverters.size) {
                @Suppress("UNCHECKED_CAST")
                val converter = anyConverters[i] as ResultConverter<Any, *>
                if (converter.canConvert(sourceClass, expectedType, sourceType, context)) {
                    return converter
                }
            }
        }

        return parent?.findConverter(sourceClass, expectedType, sourceType, context)
    }
}