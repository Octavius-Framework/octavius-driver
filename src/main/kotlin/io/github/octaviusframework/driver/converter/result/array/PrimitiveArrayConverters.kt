package io.github.octaviusframework.driver.converter.result.array

import io.github.octaviusframework.driver.converter.result.mapper.DeserializationContext
import io.github.octaviusframework.driver.converter.result.mapper.ResultConverter
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.containter.PgArray
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class IntArrayConverter : ResultConverter<IntArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == IntArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): IntArray {
        source as PgArray
        val result = IntArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Int>(), type)
        }
        return result
    }
}

class DoubleArrayConverter : ResultConverter<DoubleArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == DoubleArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): DoubleArray {
        source as PgArray
        val result = DoubleArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Double>(), type)
        }
        return result
    }
}

class FloatArrayConverter : ResultConverter<FloatArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == FloatArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): FloatArray {
        source as PgArray
        val result = FloatArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Float>(), type)
        }
        return result
    }
}

class LongArrayConverter : ResultConverter<LongArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == LongArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): LongArray {
        source as PgArray
        val result = LongArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Long>(), type)
        }
        return result
    }
}

class ShortArrayConverter : ResultConverter<ShortArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == ShortArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): ShortArray {
        source as PgArray
        val result = ShortArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Short>(), type)
        }
        return result
    }
}

class ByteArrayConverter : ResultConverter<ByteArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == ByteArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): ByteArray {
        source as PgArray
        val result = ByteArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Byte>(), type)
        }
        return result
    }
}

class BooleanArrayConverter : ResultConverter<BooleanArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == BooleanArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): BooleanArray {
        source as PgArray
        val result = BooleanArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Boolean>(), type)
        }
        return result
    }
}

class CharArrayConverter : ResultConverter<CharArray> {
    override fun canConvert(source: Any, expectedType: KType, sourceType: PgType): Boolean {
        if (source !is PgArray) return false
        return expectedType.classifier == CharArray::class
    }

    override fun convert(
        source: Any,
        expectedType: KType,
        context: DeserializationContext,
        sourceType: PgType
    ): CharArray {
        source as PgArray
        val result = CharArray(source.totalElements)
        for (i in 0 until source.totalElements) {
            val value = source.get<Any>(i)
            val type = source.typeRegistry.types[source.elementOid]!!
            result[i] = context.convert(value, typeOf<Char>(), type)
        }
        return result
    }
}
