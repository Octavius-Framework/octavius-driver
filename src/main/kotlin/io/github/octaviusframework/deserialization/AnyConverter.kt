package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import io.github.octaviusframework.query.Row
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf
import io.github.octaviusframework.types.PgType

class AnyConverter : PgConverter<Any> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType?): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Any::class && (source is PgComposite || source is PgArray)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType?): Any {
        return when (source) {
            is PgComposite -> {
                val mapType = typeOf<Map<String, Any?>>()
                context.convert<Map<String, Any?>>(source, mapType, sourceType)
            }
            is PgArray -> {
                val listType = typeOf<List<Any?>>()
                context.convert<List<Any?>>(source, listType, sourceType)
            }
            else -> source
        }
    }
}
