package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgArray
import io.github.octaviusframework.container.PgComposite
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class AnyConverter : PgConverter<Any> {
    override fun canConvert(source: Any, expectedType: KType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Any::class && (source is PgComposite || source is PgArray)
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext): Any {
        return when (source) {
            is PgComposite -> {
                // Delegate as if we were expecting Map<String, Any?>
                val mapType = typeOf<Map<String, Any?>>()
                context.convert<Map<String, Any?>>(source, mapType) ?: emptyMap<String, Any?>()
            }
            is PgArray -> {
                // Delegate as if we were expecting List<Any?>
                val listType = typeOf<List<Any?>>()
                context.convert<List<Any?>>(source, listType) ?: emptyList<Any?>()
            }
            else -> source
        }
    }
}
