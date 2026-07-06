package io.github.octaviusframework.driver.converter.result.record

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.container.PgRecord
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class MapRecordConverter : ResultConverter<PgRecord, Map<String, Any?>> {
    override val supportedSourceClass = PgRecord::class

    override fun canConvert(source: PgRecord, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Map::class || expectedType.classifier == Any::class
    }

    override fun convert(source: PgRecord, expectedType: KType, context: DeserializationContext, sourceType: PgType): Map<String, Any?> {
        val valueType = if (expectedType.classifier == Map::class) {
            expectedType.arguments.getOrNull(1)?.type ?: typeOf<Any?>()
        } else {
            typeOf<Any?>()
        }

        if (source.fields.size % 2 != 0) {
            throw IllegalArgumentException("Record fields must be in key-value pairs (even number of fields expected)")
        }

        val result = mutableMapOf<String, Any?>()
        for (i in source.fields.indices step 2) {
            val keyRaw = source.get<Any?>(i)
            val key = keyRaw.toString()
            
            val valRaw = source.get<Any?>(i + 1)
            val valOid = source.getAttributeOid(i + 1)
            val valType = source.typeRegistry.types[valOid]!!
            
            result[key] = if (valRaw == null) null else context.convert<Any?>(valRaw, valueType, valType)
        }
        return result
    }
}
