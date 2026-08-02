package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.KClass

interface SerializationContext {
    val typeManager: TypeManager
    fun convert(source: Any, expectedOid: Int): Any?
    fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>?
    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>?
}

