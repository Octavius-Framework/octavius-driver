package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.KClass

interface SerializationContext {
    val typeManager: TypeManager
    /**
     * Converts a Kotlin value to an object compatible with PostgreSQL using registered converters.
     *
     * @param source The value to convert.
     * @param expectedOid The expected PostgreSQL OID.
     * @param pathSegment Optional segment name (e.g., property name or array index) for debugging purposes. 
     * If a MappingException occurs during conversion, this segment is added to the exception's path 
     * to help trace the exact location of the error in nested structures.
     */
    fun convert(source: Any, expectedOid: Int, pathSegment: String? = null): Any?
    fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>?
    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>?
}

