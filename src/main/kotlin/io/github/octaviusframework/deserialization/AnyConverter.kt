package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class AnyConverter : PgConverter<Any> {
    override fun canConvert(source: Any, expectedType: KType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Any::class && (source is PgComposite || source is PgArray || source is io.github.octaviusframework.query.Row)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext): Any {
        return when (source) {
            is PgComposite -> {
                val mapType = typeOf<Map<String, Any?>>()
                context.convert<Map<String, Any?>>(source, mapType) ?: emptyMap<String, Any?>()
            }
            is io.github.octaviusframework.query.Row -> {
                val mapType = typeOf<Map<String, Any?>>()
                context.convert<Map<String, Any?>>(source, mapType) ?: emptyMap<String, Any?>()
            }
            is PgArray -> {
                val listType = typeOf<List<Any?>>()
                context.convert<List<Any?>>(source, listType) ?: emptyList<Any?>()
            }
            else -> source
        }
    }
}
