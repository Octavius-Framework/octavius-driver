package io.github.octaviusframework.driver.converter.result.row

import io.github.octaviusframework.driver.converter.ReflectionCompositeCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.query.Row
import io.github.octaviusframework.driver.type.CaseConvention
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType

class ReflectionRowConverter : ResultConverter<Any> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is Row) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass.isData
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        val row = source as Row
        @Suppress("UNCHECKED_CAST")
        val kClass = expectedType.classifier as KClass<Any>

        val registration = row.typeRegistry.registeredComposites[kClass]
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
            
            val index = try {
                row.getColumnIndex(columnName)
            } catch (e: Exception) {
                -1
            }

            if (index != -1) {
                val rawValue = row.getRaw(index)
                val oid = row.fields[index].descriptor.dataTypeOid
                val type = row.typeRegistry.types[oid]!!

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
                    throw IllegalArgumentException("Missing non-nullable attribute '$columnName' in row for class $kClass")
                }
                if (!param.isOptional) {
                    constructorArgs[param] = null
                }
            }
        }

        return metadata.constructor.callBy(constructorArgs)
    }
}