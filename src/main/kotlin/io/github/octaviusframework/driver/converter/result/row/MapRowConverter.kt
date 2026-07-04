package io.github.octaviusframework.driver.converter.result.row

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.query.Row
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MapRowConverter : ResultConverter<Map<String, Any?>> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is Row) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Map<String, Any?> {
        source as Row
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()

        val result = mutableMapOf<String, Any?>()
        for ((index, columnName) in source.columnNames.withIndex()) {
            val rawValue = source.getRaw(index)
            val oid = source.fields[index].descriptor.dataTypeOid
            val type = source.typeRegistry.types[oid]!!
            result[columnName] = if (rawValue == null) null else context.convert<Any>(rawValue, valueType, type)
        }
        return result
    }
}