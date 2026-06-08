package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

import io.github.octaviusframework.types.PgType

interface PgConverter<T : Any> {
    fun canConvert(source: Any, expectedType: KType, sourceType: PgType? = null): Boolean
    fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType? = null): T
}
