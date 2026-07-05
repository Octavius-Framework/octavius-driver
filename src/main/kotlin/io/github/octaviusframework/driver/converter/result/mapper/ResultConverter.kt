package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface ResultConverter<T : Any> {
    val supportedSourceClass: KClass<*>
    fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean
    fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): T
}
