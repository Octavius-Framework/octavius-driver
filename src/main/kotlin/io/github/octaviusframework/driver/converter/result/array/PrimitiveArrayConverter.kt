package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class PrimitiveArrayConverter : ResultConverter<PgArray, Any> {
    override val supportedSourceClass = PgArray::class

    private val intType = typeOf<Int>()
    private val doubleType = typeOf<Double>()
    private val floatType = typeOf<Float>()
    private val longType = typeOf<Long>()
    private val shortType = typeOf<Short>()
    private val byteType = typeOf<Byte>()
    private val booleanType = typeOf<Boolean>()
    private val charType = typeOf<Char>()

    override fun canConvert(source: PgArray, expectedType: KType, sourceType: PgType): Boolean {
        val classifier = expectedType.classifier
        return classifier == IntArray::class ||
               classifier == DoubleArray::class ||
               classifier == FloatArray::class ||
               classifier == LongArray::class ||
               classifier == ShortArray::class ||
               classifier == ByteArray::class ||
               classifier == BooleanArray::class ||
               classifier == CharArray::class
    }

    override fun convert(
        source: PgArray,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): Any {
        val pgElementType = source.typeRegistry.types[source.elementOid]
            ?: throw IllegalStateException("Type not found for element OID: ${source.elementOid}")

        val elements = source.elements
        val size = elements.size

        return when (expectedType.classifier) {
            IntArray::class -> {
                val result = IntArray(size)
                for (i in 0 until size) {
                    val value = elements[i] ?: throw buildNullException(i, "IntArray", "Int?")
                    result[i] = context.convert(value, intType, pgElementType)
                }
                result
            }
            DoubleArray::class -> {
                val result = DoubleArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "DoubleArray", "Double?")
                    result[i] = context.convert(value, doubleType, pgElementType)
                }
                result
            }
            FloatArray::class -> {
                val result = FloatArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "FloatArray", "Float?")
                    result[i] = context.convert(value, floatType, pgElementType)
                }
                result
            }
            LongArray::class -> {
                val result = LongArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "LongArray", "Long?")
                    result[i] = context.convert(value, longType, pgElementType)
                }
                result
            }
            ShortArray::class -> {
                val result = ShortArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "ShortArray", "Short?")
                    result[i] = context.convert(value, shortType, pgElementType)
                }
                result
            }
            ByteArray::class -> {
                val result = ByteArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "ByteArray", "Byte?")
                    result[i] = context.convert(value, byteType, pgElementType)
                }
                result
            }
            BooleanArray::class -> {
                val result = BooleanArray(size)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "BooleanArray", "Boolean?")
                    result[i] = context.convert(value, booleanType, pgElementType)
                }
                result
            }
            CharArray::class -> {
                val result = CharArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = elements[i] ?: throw buildNullException(i, "CharArray", "Char?")
                    result[i] = context.convert(value, charType, pgElementType)
                }
                result
            }
            else -> throw IllegalArgumentException("Unsupported primitive array type")
        }
    }

    private fun buildNullException(index: Int, arrayType: String, nullableType: String): OctaviusTypeException {
        return OctaviusTypeException(
            TypeExceptionMessage.CASTING_ERROR,
            typeName = arrayType,
            details = "Wykryto SQL NULL pod indeksem $index. Tablica prymitywna $arrayType nie może przechowywać nulli. Użyj Array<$nullableType> lub List<$nullableType>."
        )
    }
}
