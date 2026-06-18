package io.github.octaviusframework.converter

import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import io.github.octaviusframework.deserialization.DeserializationContext
import io.github.octaviusframework.deserialization.PgConverter
import io.github.octaviusframework.types.PgType
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class AnyConverter : PgConverter<Any> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Any::class && (source is PgComposite || source is PgArray)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Any {
        return source
    }
}