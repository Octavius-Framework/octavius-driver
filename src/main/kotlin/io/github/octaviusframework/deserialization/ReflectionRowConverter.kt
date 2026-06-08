package io.github.octaviusframework.deserialization

import io.github.octaviusframework.query.Row
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import io.github.octaviusframework.types.PgType

class ReflectionRowConverter : PgConverter<Any> {
    private val constructorCache = ConcurrentHashMap<KClass<*>, KFunction<Any>?>()

    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType?): Boolean {
        if (source !is Row) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass.isData
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType?): Any {
        val row = source as Row
        val kClass = expectedType.classifier as KClass<*>

        val constructor = constructorCache.getOrPut(kClass) {
            kClass.primaryConstructor
        } ?: throw IllegalArgumentException("Class $kClass does not have a primary constructor (is it a data class?)")

        val constructorArgs = mutableMapOf<kotlin.reflect.KParameter, Any?>()

        for (param in constructor.parameters) {
            val columnName = param.name ?: continue
            val index = try { row.getColumnIndex(columnName) } catch (e: Exception) { -1 }
            
            if (index != -1) {
                val rawValue = row.getRaw(index)
                val oid = try { row.fields.getOrNull(index)?.descriptor?.dataTypeOid } catch (e: Exception) { null }
                val type = if (oid != null) row.typeRegistry.types[oid] else null
                
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

        return constructor.callBy(constructorArgs)
    }
}
