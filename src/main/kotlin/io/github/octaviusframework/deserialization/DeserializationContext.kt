package io.github.octaviusframework.deserialization

import kotlin.reflect.KType

interface DeserializationContext {
    fun <T> convert(source: Any?, expectedType: KType): T?
}
