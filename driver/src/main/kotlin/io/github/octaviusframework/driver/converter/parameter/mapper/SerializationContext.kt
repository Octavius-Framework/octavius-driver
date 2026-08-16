package io.github.octaviusframework.driver.converter.parameter.mapper

import io.github.octaviusframework.driver.registry.TypeManager
import kotlin.reflect.KClass

/**
 * What a [ParameterConverter] is handed to convert the values nested inside the one it was given.
 *
 * Going through [convert] rather than converting a nested value by hand is what keeps the failure
 * legible: each call contributes a segment to the `path` of any `MappingException` raised below it,
 * so an error in one attribute of a composite arrives naming that attribute.
 */
interface SerializationContext {
    /** The session's type manager, for resolving OIDs and looking up type definitions. */
    val typeManager: TypeManager

    /**
     * Converts a Kotlin value to an object compatible with PostgreSQL using registered converters.
     *
     * @param source The value to convert.
     * @param expectedOid The expected PostgreSQL OID.
     * @param pathSegment Optional segment name (e.g., property name or array index) for debugging purposes. 
     * If a MappingException occurs during conversion, this segment is added to the exception's path 
     * to help trace the exact location of the error in nested structures.
     * @return A value a registered codec can encode.
     */
    fun convert(source: Any, expectedOid: Int, pathSegment: String? = null): Any?

    /**
     * Looks up the converter that would claim a value, without converting anything.
     *
     * @param source The value being sent.
     * @param expectedOid The expected PostgreSQL OID, or `0` when it is not known.
     * @return The converter that would be used, or `null` if none claims it.
     */
    fun findConverter(source: Any, expectedOid: Int): ParameterConverter<Any>?

    /**
     * Same as [findConverter], keyed on a class rather than an instance.
     *
     * @param sourceClass The class of the value being sent.
     * @param expectedOid The expected PostgreSQL OID, or `0` when it is not known.
     * @return The converter that would be used, or `null` if none claims it.
     */
    fun findConverterByClass(sourceClass: KClass<*>, expectedOid: Int): ParameterConverter<Any>?
}

