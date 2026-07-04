package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KType

interface DeserializationContext {
    fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType): T
}