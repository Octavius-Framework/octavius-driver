package io.github.octaviusframework.driver.converter.result.composite

import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.converter.ReflectionCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.identifier.QualifiedName
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionCompositeConverter : ResultConverter<PgComposite, Any> {

    override val supportedSourceClass = PgComposite::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        if (kClass == Any::class) {
            return context.typeManager.converterRegistry.compositeClassByName.containsKey(QualifiedName(sourceType.schema, sourceType.name)) ||
                    context.typeManager.converterRegistry.compositeClassByName.containsKey(QualifiedName("", sourceType.name))
        }
        if (!kClass.isData) return false
        return context.typeManager.converterRegistry.registeredComposites.containsKey(kClass)
    }

    override fun convert(source: PgComposite, expectedType: KType, sourceType: PgType, context: DeserializationContext): Any {
        val expectedClass = expectedType.classifier as? KClass<*> ?: Any::class
        
        @Suppress("UNCHECKED_CAST")
        val kClass = if (expectedClass == Any::class) {
            context.typeManager.converterRegistry.compositeClassByName[QualifiedName(sourceType.schema, sourceType.name)]
                ?: context.typeManager.converterRegistry.compositeClassByName[QualifiedName("", sourceType.name)]
                ?: error("Missing composite registration for type")
        } else {
            expectedClass
        } as KClass<Any>

        val registration = context.typeManager.converterRegistry.registeredComposites[kClass]
            ?: error("Missing composite registration for class")

        val metadata = ReflectionCache.getOrCreateDataObjectMetadata(
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
                if (rawValue == null) {
                    if (!meta.type.isMarkedNullable && !param.isOptional) {
                        throw MappingException(MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING, "Null value for non-nullable attribute '$columnName' for class $kClass", path = mutableListOf(columnName))
                    }
                    if (!param.isOptional) {
                        constructorArgs[param] = null
                    }
                } else {
                    val convertedValue = context.convert<Any>(rawValue, meta.type, source.getAttributeOid(index), columnName)
                    constructorArgs[param] = convertedValue
                }
            } else {
                if (!param.isOptional && !meta.type.isMarkedNullable) {
                    throw MappingException(MappingExceptionReason.REQUIRED_ATTRIBUTE_MISSING, "Missing non-nullable attribute '$columnName' in composite for class $kClass", path = mutableListOf(columnName))
                }
                if (!param.isOptional) {
                    constructorArgs[param] = null
                }
            }
        }

        return metadata.constructor.callBy(constructorArgs)
    }
}