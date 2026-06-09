package io.github.octaviusframework.converter.row

import io.github.octaviusframework.deserialization.DeserializationContext
import io.github.octaviusframework.deserialization.PgConverter
import io.github.octaviusframework.query.Row
import io.github.octaviusframework.types.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MapRowConverter : PgConverter<Map<String, Any?>> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType?): Boolean {
        if (source !is Row) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType?): Map<String, Any?> {
        source as Row
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()

        val result = mutableMapOf<String, Any?>()
        for ((index, columnName) in source.columnNames.withIndex()) {
            val rawValue = source.getRaw(index)
            val oid = source.fields.getOrNull(index)?.descriptor?.dataTypeOid
            val type = if (oid != null) source.typeRegistry.types[oid] else null
            result[columnName] = if (rawValue == null) null else context.convert<Any>(rawValue, valueType, type)
        }
        return result
    }
}