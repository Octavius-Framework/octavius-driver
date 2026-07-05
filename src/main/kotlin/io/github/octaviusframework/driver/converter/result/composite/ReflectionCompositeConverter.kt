package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.container.PgComposite
import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionCompositeConverter : ResultConverter<Any> {
    override val supportedSourceClass = PgComposite::class

    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgComposite) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (!kClass.isData) return false
        return source.typeRegistry.registeredComposites.containsKey(kClass)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        val composite = source as PgComposite
        @Suppress("UNCHECKED_CAST")
        val kClass = expectedType.classifier as KClass<Any>
        val registration = composite.typeRegistry.registeredComposites[kClass]
            ?: throw IllegalArgumentException("Composite not registered for $kClass")

        val metadata = ReflectionCompositeCache.getOrCreateDataObjectMetadata(
            kClass,
            registration.pgConvention,
            registration.kotlinConvention
        )

        val constructorArgs = mutableMapOf<KParameter, Any?>()

        for (meta in metadata.constructorProperties) {
            val param = meta.parameter
            val columnName = meta.keyName
            val index = composite.type.attributes.keys.indexOf(columnName)

            if (index != -1) {
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

        return metadata.constructor.callBy(constructorArgs)
    }
}