package io.github.octaviusframework.driver.converter.result.row

import io.github.octaviusframework.driver.util.reflection.ReflectionCache
import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.identifier.CaseConvention
import io.github.octaviusframework.driver.row.Row
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import io.github.octaviusframework.driver.util.reflection.MissingToken
import io.github.octaviusframework.driver.util.reflection.instantiateDataObject

internal object ReflectionRowConverter : ResultConverter<Row, Any> {

    override val supportedSourceClass = Row::class

    override fun canConvert(sourceClass: KClass<*>, expectedType: KType, sourceType: PgType, context: DeserializationContext): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass.isData
    }

    override fun convert(source: Row, expectedType: KType, sourceType: PgType, context: DeserializationContext): Any {
        @Suppress("UNCHECKED_CAST")
        val kClass = expectedType.classifier as KClass<Any>

        val metadata = ReflectionCache.getOrCreateDataObjectMetadata(kClass)

        return instantiateDataObject(kClass, metadata) { meta ->
            val columnName = meta.keyName
            val index = source.columnNames.indexOf(columnName)

            if (index != -1) {
                val rawValue = source.getRaw(index)
                val oid = source.getOid(index)
                if (rawValue == null) {
                    null
                } else {
                    context.convert<Any>(rawValue, meta.type, oid, columnName)
                }
            } else {
                MissingToken
            }
        }
    }
}
