package io.github.octaviusframework.driver.converter.parameter.mapper

import kotlin.reflect.KClass

interface SerializationContext {
    fun convert(source: Any, expectedOid: Int): Any?
    fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>?
    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>?
}

