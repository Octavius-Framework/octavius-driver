package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.containter.PgArray
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class CollectionArrayConverter : ResultConverter<Collection<*>> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == List::class || kClass == Collection::class || kClass == Iterable::class || kClass == Set::class
    }

    override fun convert(source: Any, expectedType: KType, context: DeserializationContext, sourceType: PgType): Collection<*> {
        source as PgArray
        return buildMultiDimensionalCollection(source, context, expectedType, 0, 0, sourceType)
    }

    private fun buildMultiDimensionalCollection(
        source: PgArray,
        context: DeserializationContext,
        expectedType: KType,
        dimensionIndex: Int,
        flatIndexOffset: Int,
        sourceType: PgType
    ): Collection<*> {
        if (source.dimensions.isEmpty()) {
            val elementType = expectedType.arguments.firstOrNull()?.type ?: typeOf<Any?>()
            val kClass = expectedType.classifier as? KClass<*> ?: List::class
            val mappedElements = (0 until source.totalElements).map { i ->
                val value = source.get<Any?>(i)
                val type = source.typeRegistry.types[source.elementOid]!!
                if (value == null) null else context.convert<Any>(value, elementType, type)
            }
            return if (kClass == Set::class) mappedElements.toSet() else mappedElements
        }

        val currentDimSize = source.dimensions[dimensionIndex].size
        val elementType = expectedType.arguments.firstOrNull()?.type ?: typeOf<Any?>()
        val kClass = expectedType.classifier as? KClass<*> ?: List::class

        val mappedElements = if (dimensionIndex == source.dimensions.size - 1) {
            (0 until currentDimSize).map { i ->
                val flatIndex = flatIndexOffset + i
                val value = source.get<Any?>(flatIndex)
                val type = source.typeRegistry.types[source.elementOid]!!
                if (value == null) null else context.convert<Any>(value, elementType, type)
            }
        } else {
            var multiplier = 1
            for (j in dimensionIndex + 1 until source.dimensions.size) {
                multiplier *= source.dimensions[j].size
            }
            (0 until currentDimSize).map { i ->
                buildMultiDimensionalCollection(
                    source,
                    context,
                    elementType,
                    dimensionIndex + 1,
                    flatIndexOffset + i * multiplier,
                    sourceType
                )
            }
        }

        return if (kClass == Set::class) {
            mappedElements.toSet()
        } else {
            mappedElements
        }
    }
}