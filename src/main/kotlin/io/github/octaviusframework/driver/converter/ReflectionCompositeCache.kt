package io.github.octaviusframework.driver.converter

import io.github.octaviusframework.driver.annotation.MapKey
import io.github.octaviusframework.driver.type.CaseConvention
import io.github.octaviusframework.driver.type.CaseConverter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

data class ConstructorParamMetadata<T : Any>(
    val parameter: KParameter,
    val property: KProperty1<T, Any?>,
    val type: KType,
    val keyName: String
)

data class DataObjectClassMetadata<T : Any>(
    val constructor: KFunction<T>,
    val constructorProperties: List<ConstructorParamMetadata<T>>
)

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

                val keyName = property.findAnnotation<MapKey>()?.name
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
