package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.identifier.QualifiedName
import kotlin.reflect.KClass

interface ParameterConverter<T : Any> {
    val supportedClass: KClass<T>

    fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return supportedClass == sourceClass || supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    fun convert(source: T, expectedOid: Int, context: SerializationContext): Any

    fun getDefaultTypeName(sourceClass: KClass<*>, context: SerializationContext): QualifiedName? = null
}

