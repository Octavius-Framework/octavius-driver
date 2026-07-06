package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.container.PgArray
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class PrimitiveArrayConverter : ResultConverter<PgArray, Any> {
    override val supportedSourceClass = PgArray::class

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
        
        return when (expectedType.classifier) {
            IntArray::class -> {
                val result = IntArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Int>(), type)
                }
                result
            }
            DoubleArray::class -> {
                val result = DoubleArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Double>(), type)
                }
                result
            }
            FloatArray::class -> {
                val result = FloatArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Float>(), type)
                }
                result
            }
            LongArray::class -> {
                val result = LongArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Long>(), type)
                }
                result
            }
            ShortArray::class -> {
                val result = ShortArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Short>(), type)
                }
                result
            }
            ByteArray::class -> {
                val result = ByteArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Byte>(), type)
                }
                result
            }
            BooleanArray::class -> {
                val result = BooleanArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Boolean>(), type)
                }
                result
            }
            CharArray::class -> {
                val result = CharArray(source.totalElements)
                for (i in 0 until source.totalElements) {
                    val value = source.get<Any>(i)
                    val type = source.typeRegistry.types[source.elementOid]!!
                    result[i] = context.convert(value, typeOf<Char>(), type)
                }
                result
            }
            else -> throw IllegalArgumentException("Unsupported primitive array type")
        }
    }
}
