package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.exception.MappingExceptionMessage
import io.github.octaviusframework.driver.exception.MappingException

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.exception.OctaviusInternalException
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionCompositeConverter : ResultConverter<PgComposite, Any> {

    override val supportedSourceClass = PgComposite::class

    override fun canConvert(
        source: PgComposite,
        expectedType: KType,
        sourceType: PgType,
        context: DeserializationContext
    ): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (!kClass.isData) return false
        return context.typeManager.registry.registeredComposites.containsKey(kClass)
    }

    override fun convert(source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext): Any {
        @Suppress("UNCHECKED_CAST")
        val kClass = expectedType.classifier as KClass<Any>
        val registration = context.typeManager.registry.registeredComposites[kClass]
            ?: throw OctaviusInternalException()

        val metadata = ReflectionCompositeCache.getOrCreateDataObjectMetadata(
            kClass,
            registration.pgConvention,
            registration.kotlinConvention
        )

        val constructorArgs = mutableMapOf<KParameter, Any?>()

        for (meta in metadata.constructorProperties) {
            val param = meta.parameter
            val columnName = meta.keyName
            val index = source.type.nameToIndex[columnName] ?: -1

            if (index != -1) {
                val rawValue = source.get<Any?>(index)
                val type = source.getAttributeType(index)

                if (rawValue == null) {
                    if (!meta.type.isMarkedNullable && !param.isOptional) {
                        throw MappingException(MappingExceptionMessage.NULL_FOR_NON_NULLABLE_ATTRIBUTE, "Null value for non-nullable attribute '$columnName' for class $kClass")
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
                    throw MappingException(MappingExceptionMessage.MISSING_ATTRIBUTE, "Missing non-nullable attribute '$columnName' in composite for class $kClass")
                }
                if (!param.isOptional) {
                    constructorArgs[param] = null
                }
            }
        }

        return metadata.constructor.callBy(constructorArgs)
    }
}