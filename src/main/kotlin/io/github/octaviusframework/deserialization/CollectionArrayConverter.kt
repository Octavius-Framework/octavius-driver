package io.github.octaviusframework.deserialization

import io.github.octaviusframework.container.PgArray
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class CollectionArrayConverter : PgConverter<Collection<*>> {
    override fun canConvert(source: Any, expectedType: KType): Boolean {
        if (source !is PgArray) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == List::class || kClass == Collection::class || kClass == Iterable::class || kClass == Set::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext): Collection<*> {
        source as PgArray
        val elementType = expectedType.arguments.firstOrNull()?.type ?: typeOf<Any?>()
        val elements = source.toList<Any>()
        val mappedElements = elements.map { 
            if (it == null) null else context.convert<Any>(it, elementType) 
        }
        
        val kClass = expectedType.classifier as KClass<*>
        return if (kClass == Set::class) {
            mappedElements.toSet()
        } else {
            mappedElements
        }
    }
}
