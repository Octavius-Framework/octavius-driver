package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgArray
import java.lang.reflect.Array as JavaArray
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class ObjectArrayConverter : ResultConverter<PgArray, Array<*>> {
    override val supportedSourceClass = PgArray::class

    override fun canConvert(source: PgArray, expectedType: KType, sourceType: PgType): Boolean {
        val kClass = expectedType.classifier as? KClass<*> ?: return false
        return kClass == Array::class
    }

    override fun convert(source: PgArray, expectedType: KType, context: DeserializationContext, sourceType: PgType): Array<*> {
        val pgElementType = source.typeRegistry.types[source.elementOid]
            ?: throw IllegalStateException("Type not found for element OID: ${source.elementOid}")

        return buildMultiDimensionalArray(source, context, expectedType, 0, 0, pgElementType)
    }

    private fun getJavaClassForType(kType: KType): Class<*> {
        val classifier = kType.classifier as? KClass<*> ?: return Any::class.java
        if (classifier == Array::class) {
            val elementType = kType.arguments.firstOrNull()?.type ?: typeOf<Any?>()
            val elementJavaClass = getJavaClassForType(elementType)
            return JavaArray.newInstance(elementJavaClass, 0).javaClass
        }
        return classifier.javaObjectType
    }

    private fun buildMultiDimensionalArray(
        source: PgArray,
        context: DeserializationContext,
        expectedType: KType,
        dimensionIndex: Int,
        flatIndexOffset: Int,
        pgElementType: PgType
    ): Array<*> {
        val ktElementType = expectedType.arguments.firstOrNull()?.type ?: typeOf<Any?>()
        val elements = source.elements

        val jClass = getJavaClassForType(ktElementType)

        if (source.dimensions.isEmpty()) {
            val mappedElements = JavaArray.newInstance(jClass, elements.size) as Array<Any?>
            for (i in elements.indices) {
                val value = elements[i]
                mappedElements[i] = if (value == null) null else context.convert<Any>(value, ktElementType, pgElementType)
            }
            return mappedElements
        }

        val currentDimSize = source.dimensions[dimensionIndex].size
        val mappedElements = JavaArray.newInstance(jClass, currentDimSize) as Array<Any?>

        if (dimensionIndex == source.dimensions.size - 1) {
            for (i in 0 until currentDimSize) {
                val flatIndex = flatIndexOffset + i
                val value = elements[flatIndex]
                mappedElements[i] = if (value == null) null else context.convert<Any>(value, ktElementType, pgElementType)
            }
        } else {
            var multiplier = 1
            for (j in dimensionIndex + 1 until source.dimensions.size) {
                multiplier *= source.dimensions[j].size
            }

            for (i in 0 until currentDimSize) {
                mappedElements[i] = buildMultiDimensionalArray(
                    source,
                    context,
                    ktElementType, // Array<Array<Int>> -> Array<Int> etc.
                    dimensionIndex + 1,
                    flatIndexOffset + i * multiplier,
                    pgElementType
                )
            }
        }

        return mappedElements
    }
}
