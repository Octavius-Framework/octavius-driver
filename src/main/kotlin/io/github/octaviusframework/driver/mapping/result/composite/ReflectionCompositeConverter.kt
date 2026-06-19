package io.github.octaviusframework.driver.mapping.result.composite

import io.github.octaviusframework.driver.mapping.result.DeserializationContext
import io.github.octaviusframework.driver.mapping.result.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.containter.PgComposite
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

class ReflectionCompositeConverter : ResultConverter<Any> {
    private val constructorCache = ConcurrentHashMap<KClass<*>, KFunction<Any>>()

    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgComposite) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (!kClass.isData) return false
        return source.typeRegistry.registeredComposites.containsKey(kClass)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        val composite = source as PgComposite
        val kClass = expectedType.classifier as KClass<*>

        val constructor = constructorCache.getOrPut(kClass) {
            kClass.primaryConstructor
        } ?: throw IllegalArgumentException("Class $kClass does not have a primary constructor (is it a data class?)")

        val constructorArgs = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val columnName = param.name ?: continue
            val index = composite.type.attributes.keys.indexOf(columnName)

            if (index != -1) {
                // Pobieramy wartość wprost (bez rzutowania na tym etapie)
                val rawValue = composite.get<Any?>(index)
                val oid = composite.type.attributes.values.toList()[index]
                val type = composite.typeRegistry.types[oid]!!

                if (rawValue == null) {
                    if (!param.type.isMarkedNullable && !param.isOptional) {
                        throw IllegalArgumentException("Null value for non-nullable attribute '$columnName' for class $kClass")
                    }
                    if (!param.isOptional) {
                        constructorArgs[param] = null
                    }
                } else {
                    val convertedValue = context.convert<Any>(rawValue, param.type, type)
                    constructorArgs[param] = convertedValue
                }
            } else {
                if (!param.isOptional && !param.type.isMarkedNullable) {
                    throw IllegalArgumentException("Missing non-nullable attribute '$columnName' in composite for class $kClass")
                }
                if (!param.isOptional) {
                    constructorArgs[param] = null
                }
            }
        }

        return constructor.callBy(constructorArgs)
    }
}