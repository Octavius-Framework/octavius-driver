package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import kotlin.reflect.KClass

interface ParameterConverter<T : Any> {
    val supportedClass: KClass<T>

    fun canConvert(sourceClass: KClass<*>, expectedOid: Int, context: SerializationContext): Boolean {
        return supportedClass == sourceClass || supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    fun convert(source: T, expectedOid: Int, context: SerializationContext): Any

    fun getDefaultOid(context: SerializationContext): Int = UNRESOLVED_OID
}

