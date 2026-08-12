package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeManager
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface DeserializationContext {
    val typeManager: TypeManager
    /**
     * Converts a raw value to the expected type using registered converters.
     *
     * @param source The raw value to convert.
     * @param expectedType The Kotlin type expected as the result.
     * @param sourceType The PostgreSQL type of the raw value.
     * @param pathSegment Optional segment name (e.g., property name or array index) for debugging purposes. 
     * If a MappingException occurs during conversion, this segment is added to the exception's path
     * to help trace the exact location of the error in nested structures.
     */
    fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType, pathSegment: String? = null): T
    fun findConverter(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>?

    /**
     * Converts a raw value to the expected type using registered converters, resolving the PostgreSQL type from its OID.
     *
     * @param source The raw value to convert.
     * @param expectedType The Kotlin type expected as the result.
     * @param sourceOid The PostgreSQL OID of the raw value.
     * @param pathSegment Optional segment name (e.g., property name or array index) for debugging purposes.
     * If a MappingException occurs during conversion, this segment is added to the exception's path
     * to help trace the exact location of the error in nested structures.
     */
    fun <T> convert(source: Any?, expectedType: KType, sourceOid: Int, pathSegment: String? = null): T {
        return convert(source, expectedType, typeManager.typeDictionary.getPgType(sourceOid), pathSegment)
    }
}
