package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionCompositeConverter : ResultConverter<PgComposite, Any> {
    override val supportedSourceClass = PgComposite::class

    override fun canConvert(source: PgComposite, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (!kClass.isData) return false
        return source.typeRegistry.registeredComposites.containsKey(kClass)
    }

    override fun convert(source: PgComposite, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        val composite = source
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
                    if (!meta.type.isMarkedNullable && !param.isOptional) {
                        throw IllegalArgumentException("Null value for non-nullable attribute '$columnName' for class $kClass")
                    }
                    if (!param.isOptional) {
                        constructorArgs[param] = null
                    }
                } else {
                    val convertedValue = context.convert<Any>(rawValue, meta.type, type)
                    constructorArgs[param] = convertedValue
                }
            } else {
                if (!param.isOptional && !meta.type.isMarkedNullable) {
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