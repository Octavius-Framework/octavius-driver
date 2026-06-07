package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

interface PgConverter<T : Any> {
    fun canConvert(source: Any, expectedType: KType): Boolean
    fun convert(source: Any, expectedType: KType, context: DeserializationContext): T?
}
