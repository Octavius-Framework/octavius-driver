package io.github.octaviusframework.driver.converter.result.row

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.identifier.CaseConvention
import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionRowConverter : ResultConverter<Row, Any> {
    override val supportedSourceClass = Row::class

    override fun canConvert(source: Row, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass.isData
    }

    override fun convert(source: Row, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        @Suppress("UNCHECKED_CAST")
        val kClass = expectedType.classifier as KClass<Any>

        val registration = source.typeRegistry.registeredComposites[kClass]
        val pgConvention = registration?.pgConvention ?: CaseConvention.SNAKE_CASE_LOWER
        val kotlinConvention = registration?.kotlinConvention ?: CaseConvention.CAMEL_CASE

        val metadata = ReflectionCompositeCache.getOrCreateDataObjectMetadata(
            kClass,
            pgConvention,
            kotlinConvention
        )

        val constructorArgs = mutableMapOf<KParameter, Any?>()

        for (meta in metadata.constructorProperties) {
            val param = meta.parameter
            val columnName = meta.keyName
            
            val index = source.columnNames.indexOf(columnName)

            if (index != -1) {
                val rawValue = source.getRaw(index)
                val oid = source.getOid(index)
                val type = source.typeRegistry.types[oid]!!

                if (rawValue == null) {
                    if (!meta.type.isMarkedNullable && !param.isOptional) {
                        throw IllegalArgumentException("Null value for non-nullable attribute '$columnName' for class $kClass") //TODO proper exception
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
                    throw IllegalArgumentException("Missing non-nullable attribute '$columnName' in row for class $kClass") //TODO proper exception
                }
                if (!param.isOptional) {
                    constructorArgs[param] = null
                }
            }
        }

        return metadata.constructor.callBy(constructorArgs)
    }
}
