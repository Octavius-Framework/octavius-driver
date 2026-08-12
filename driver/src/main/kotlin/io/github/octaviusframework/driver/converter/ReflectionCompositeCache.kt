package io.github.octaviusframework.driver.converter

import io.github.octaviusframework.driver.annotation.PgName
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.identifier.CaseConverter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.*
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Metadata for a single constructor parameter and its corresponding property in a data class.
 *
 * @param T The type of the data class.
 * @property parameter The reflection [KParameter] object from the constructor.
 * @property property The reflection [KProperty1] object corresponding to the parameter.
 * @property type The Kotlin [KType] of the parameter.
 * @property keyName The mapped name used to resolve this property against a PostgreSQL record/composite type.
 */
data class ConstructorParamMetadata<T : Any>(
    val parameter: KParameter,
    val property: KProperty1<T, Any?>,
    val type: KType,
    val keyName: String
)

/**
 * Pre-computed reflection metadata for a data class.
 *
 * @param T The type of the data class.
 * @property constructor The primary constructor of the data class.
 * @property constructorProperties A list of metadata for each constructor parameter.
 */
data class DataObjectClassMetadata<T : Any>(
    val constructor: KFunction<T>,
    val constructorProperties: List<ConstructorParamMetadata<T>>
)

/**
 * A global cache for Kotlin reflection metadata of data classes.
 *
 * This prevents the expensive overhead of inspecting class properties and annotations
 * repeatedly during the serialization or deserialization of PostgreSQL composite types.
 */
object ReflectionCompositeCache {
    private val dataObjectCache = ConcurrentHashMap<KClass<*>, DataObjectClassMetadata<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrCreateDataObjectMetadata(
        kClass: KClass<T>,
        pgConvention: CaseConvention,
        kotlinConvention: CaseConvention
    ): DataObjectClassMetadata<T> {
        return dataObjectCache.getOrPut(kClass) {
            val constructor = kClass.primaryConstructor
                ?: throw IllegalArgumentException("Class ${kClass.simpleName} must have a primary constructor.")

            val propertiesByName = kClass.memberProperties.associateBy { it.name }

            val constructorProperties = constructor.parameters.map { param ->
                val property = propertiesByName[param.name]!!

                val keyName = property.findAnnotation<PgName>()?.name
                    ?: CaseConverter.convert(param.name!!, kotlinConvention, pgConvention)

                ConstructorParamMetadata(
                    parameter = param,
                    property = property,
                    type = param.type,
                    keyName = keyName
                )
            }
            DataObjectClassMetadata(constructor, constructorProperties)
        } as DataObjectClassMetadata<T>
    }
}
