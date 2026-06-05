package io.github.octaviusframework.containter

import io.github.octaviusframework.io.getIntBE
import io.github.octaviusframework.io.getUIntBE
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry

object ContainerParsers {

    fun parsePgArray(bytes: ByteArray, oid: UInt, typeRegistry: TypeRegistry): PgArray {
        var offset = 0
        if (bytes.size < 12) throw IllegalStateException("Zbyt mało danych dla PgArray")
        
        val ndims = bytes.getIntBE(offset); offset += 4
        val hasNullsInt = bytes.getIntBE(offset); offset += 4
        val elementOid = bytes.getUIntBE(offset); offset += 4
        
        val dimensions = mutableListOf<ArrayDimension>()
        for (i in 0 until ndims) {
            val size = bytes.getIntBE(offset); offset += 4
            val lowerBound = bytes.getIntBE(offset); offset += 4
            dimensions.add(ArrayDimension(size, lowerBound))
        }
        
        val totalElements = dimensions.fold(1) { acc, dim -> acc * dim.size }
        val rawElements = ArrayList<ByteArray?>(if (ndims == 0) 0 else totalElements)
        
        for (i in 0 until (if (ndims == 0) 0 else totalElements)) {
            val len = bytes.getIntBE(offset); offset += 4
            if (len == -1) {
                rawElements.add(null)
            } else {
                val elementBytes = bytes.sliceArray(offset until offset + len)
                rawElements.add(elementBytes)
                offset += len
            }
        }
        
        return PgArray(elementOid, dimensions, hasNullsInt != 0, rawElements, typeRegistry)
    }

    fun parsePgComposite(bytes: ByteArray, oid: UInt, typeRegistry: TypeRegistry): PgComposite {
        val pgType = typeRegistry.types[oid] as? PgType.Composite
            ?: throw IllegalStateException("Oczekiwano typu Composite dla OID $oid")
            
        var offset = 0
        val numFields = bytes.getIntBE(offset); offset += 4
        
        val rawAttributes = ArrayList<ByteArray?>(numFields)
        for (i in 0 until numFields) {
            val fieldOid = bytes.getUIntBE(offset); offset += 4
            val len = bytes.getIntBE(offset); offset += 4
            if (len == -1) {
                rawAttributes.add(null)
            } else {
                val fieldBytes = bytes.sliceArray(offset until offset + len)
                rawAttributes.add(fieldBytes)
                offset += len
            }
        }
        
        return PgComposite(pgType, rawAttributes, typeRegistry)
    }

    fun parsePgRange(bytes: ByteArray, oid: UInt, typeRegistry: TypeRegistry): PgRange {
        val pgType = typeRegistry.types[oid] as? PgType.Range
            ?: throw IllegalStateException("Oczekiwano typu Range dla OID $oid")
            
        var offset = 0
        val flags = bytes[offset]; offset += 1
        
        val isEmpty = (flags.toInt() and 0x01) != 0
        val isLowerInfinite = (flags.toInt() and 0x08) != 0
        val isLowerNull = (flags.toInt() and 0x20) != 0
        val isUpperInfinite = (flags.toInt() and 0x10) != 0
        val isUpperNull = (flags.toInt() and 0x40) != 0
        
        var rawLowerBound: ByteArray? = null
        if (!isEmpty && !isLowerInfinite && !isLowerNull) {
            val len = bytes.getIntBE(offset); offset += 4
            rawLowerBound = bytes.sliceArray(offset until offset + len)
            offset += len
        }
        
        var rawUpperBound: ByteArray? = null
        if (!isEmpty && !isUpperInfinite && !isUpperNull) {
            val len = bytes.getIntBE(offset); offset += 4
            rawUpperBound = bytes.sliceArray(offset until offset + len)
            offset += len
        }
        
        return PgRange(pgType.subtypeOid, flags, rawLowerBound, rawUpperBound, typeRegistry)
    }

    fun parsePgMultirange(bytes: ByteArray, oid: UInt, typeRegistry: TypeRegistry): PgMultirange {
        val pgType = typeRegistry.types[oid] as? PgType.Multirange
            ?: throw IllegalStateException("Oczekiwano typu Multirange dla OID $oid")
            
        var offset = 0
        val numRanges = bytes.getIntBE(offset); offset += 4
        
        val ranges = mutableListOf<PgRange>()
        for (i in 0 until numRanges) {
            val len = bytes.getIntBE(offset); offset += 4
            val rangeBytes = bytes.sliceArray(offset until offset + len)
            ranges.add(parsePgRange(rangeBytes, pgType.rangeOid, typeRegistry))
            offset += len
        }
        
        return PgMultirange(ranges)
    }
}
