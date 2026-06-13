package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

import io.github.octaviusframework.types.PgType

interface DeserializationContext {
    fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType): T
}
