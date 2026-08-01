package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.UNRESOLVED_OID
import kotlin.reflect.KClass

interface ParameterConverter<T : Any> {
    val supportedClass: KClass<T>

    fun canConvert(sourceClass: KClass<*>, expectedOid: Int, typeManager: TypeManager): Boolean {
        return supportedClass == sourceClass || supportedClass.java.isAssignableFrom(sourceClass.java)
    }

    fun convert(source: T, expectedOid: Int, context: SerializationContext, typeManager: TypeManager): Any

    fun getDefaultOid(typeManager: TypeManager): Int = UNRESOLVED_OID
}

