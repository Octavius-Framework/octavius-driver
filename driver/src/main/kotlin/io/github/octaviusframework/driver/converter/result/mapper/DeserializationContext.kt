package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.TypeManager
import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KType

interface DeserializationContext {
    val typeManager: TypeManager
    fun <T> convert(source: Any?, expectedType: KType, sourceType: PgType): T
    fun findConverter(source: Any, expectedType: KType, sourceType: PgType): ResultConverter<Any, *>?
}