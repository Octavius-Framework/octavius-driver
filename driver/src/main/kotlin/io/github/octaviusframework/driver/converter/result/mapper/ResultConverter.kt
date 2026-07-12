package io.github.octaviusframework.driver.converter.result.mapper

import io.github.octaviusframework.driver.type.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface ResultConverter<S : Any, T : Any> {
    val supportedSourceClass: KClass<S>
    fun canConvert(source: S, expectedType: KType, sourceType: PgType): Boolean
    fun convert(source: S, expectedType: KType, context: DeserializationContext, sourceType: PgType): T
}
