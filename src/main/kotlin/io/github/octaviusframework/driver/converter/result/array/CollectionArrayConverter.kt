package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgArray
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class CollectionArrayConverter : ResultConverter<PgArray, Collection<*>> {
    override val supportedSourceClass = PgArray::class

    override fun canConvert(source: PgArray, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == List::class || kClass == Collection::class || kClass == Iterable::class || kClass == Set::class
    }

    override fun convert(source: PgArray, expectedType: KType, context: DeserializationContext, sourceType: PgType): Collection<*> {
        val pgElementType = source.typeRegistry.types[source.elementOid]
            ?: throw IllegalStateException("Type not found for element OID: ${source.elementOid}")

        return buildMultiDimensionalCollection(source, context, expectedType, 0, 0, pgElementType)
    }

    private fun buildMultiDimensionalCollection(
        source: PgArray,
        context: DeserializationContext,
        expectedType: KType,
        dimensionIndex: Int,
        flatIndexOffset: Int,
        pgElementType: PgType
    ): Collection<*> {
        val kClass = expectedType.classifier as? KClass<*> ?: List::class
        val ktElementType = expectedType.arguments.firstOrNull()?.type ?: typeOf<Any?>()

        val elements = source.elements

        if (source.dimensions.isEmpty()) {
            val mappedElements = List(elements.size) { i ->
                val value = elements[i]
                if (value == null) null else context.convert<Any>(value, ktElementType, pgElementType)
            }
            return if (kClass == Set::class) mappedElements.toSet() else mappedElements
        }

        val currentDimSize = source.dimensions[dimensionIndex].size

        var elementConverter: ResultConverter<Any, *>? = null
        var isFallbackCast = false
        var converterSearched = false
        val kClassForCast = ktElementType.classifier as? KClass<*>

        val mappedElements = if (dimensionIndex == source.dimensions.size - 1) {
            List(currentDimSize) { i ->
                val flatIndex = flatIndexOffset + i
                val value = elements[flatIndex]
                if (value == null) {
                    null
                } else {
                    if (!converterSearched) {
                        elementConverter = context.findConverter(value, ktElementType, pgElementType)
                        if (elementConverter == null) isFallbackCast = true
                        converterSearched = true
                    }
                    if (isFallbackCast) {
                        if (kClassForCast != null && kClassForCast.isInstance(value)) {
                            value
                        } else {
                            throw IllegalArgumentException("No converter found for source ${value::class} and expected type $ktElementType")
                        }
                    } else {
                        elementConverter!!.convert(value, ktElementType, context, pgElementType)
                    }
                }
            }
        } else {
            var multiplier = 1
            for (j in dimensionIndex + 1 until source.dimensions.size) {
                multiplier *= source.dimensions[j].size
            }

            List(currentDimSize) { i ->
                buildMultiDimensionalCollection(
                    source,
                    context,
                    ktElementType,
                    dimensionIndex + 1,
                    flatIndexOffset + i * multiplier,
                    pgElementType
                )
            }
        }

        return if (kClass == Set::class) mappedElements.toSet() else mappedElements
    }
}