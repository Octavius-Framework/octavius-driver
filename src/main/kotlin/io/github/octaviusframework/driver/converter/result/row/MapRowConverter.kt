package io.github.octaviusframework.driver.converter.result.row

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.query.OctaviusRow
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MapRowConverter : ResultConverter<OctaviusRow, Map<String, Any?>> {
    override val supportedSourceClass = OctaviusRow::class

    override fun canConvert(source: OctaviusRow, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class
    }

    override fun convert(source: OctaviusRow, expectedType: KType, context: DeserializationContext, sourceType: PgType): Map<String, Any?> {
        val valueType = expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()

        val result = mutableMapOf<String, Any?>()
        for ((index, columnName) in source.columnNames.withIndex()) {
            val rawValue = source.getRaw(index)
            val oid = source.getOid(index)
            val type = source.typeRegistry.types[oid]!!
            result[columnName] = if (rawValue == null) null else context.convert<Any>(rawValue, valueType, type)
        }
        return result
    }
}