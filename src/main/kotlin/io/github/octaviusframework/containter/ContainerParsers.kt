package io.github.octaviusframework.containter

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.io.get
import io.github.octaviusframework.io.getIntBE
import io.github.octaviusframework.io.getUIntBE
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry

object ContainerParsers {

    fun parseEagerContainer(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): Any {
        val pgType = typeRegistry.types[oid]
        return when (pgType) {
            is PgType.Array -> parsePgArray(window, pgType.oid, typeRegistry)
            is PgType.Composite -> parsePgComposite(window, pgType.oid, typeRegistry)
            is PgType.Range -> parsePgRange(window, pgType.oid, typeRegistry)
            is PgType.Multirange -> parsePgMultirange(window, pgType.oid, typeRegistry)
            else -> window
        }
    }

    fun parsePgArray(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgArray {
        var offset = 0
        if (window.length < 12) throw IllegalStateException("Zbyt mało danych dla PgArray")
        
        val ndims = window.getIntBE(offset); offset += 4
        val hasNullsInt = window.getIntBE(offset); offset += 4
        val elementOid = window.getUIntBE(offset); offset += 4
        
        val dimensions = mutableListOf<ArrayDimension>()
        for (i in 0 until ndims) {
            val size = window.getIntBE(offset); offset += 4
            val lowerBound = window.getIntBE(offset); offset += 4
            dimensions.add(ArrayDimension(size, lowerBound))
        }
        
        val totalElements = dimensions.fold(1) { acc, dim -> acc * dim.size }
        val rawElements = ArrayList<Any?>(if (ndims == 0) 0 else totalElements)
        
        for (i in 0 until (if (ndims == 0) 0 else totalElements)) {
            val len = window.getIntBE(offset); offset += 4
            if (len == -1) {
                rawElements.add(null)
            } else {
                val elementWindow = window.slice(offset, len)
                rawElements.add(parseEagerContainer(elementWindow, elementOid, typeRegistry))
                offset += len
            }
        }
        
        return PgArray(elementOid, dimensions, hasNullsInt != 0, rawElements, typeRegistry)
    }

    fun parsePgComposite(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgComposite {
        val pgType = typeRegistry.types[oid] as? PgType.Composite
            ?: throw IllegalStateException("Oczekiwano typu Composite dla OID $oid")
            
        var offset = 0
        val numFields = window.getIntBE(offset); offset += 4
        
        val rawAttributes = ArrayList<Any?>(numFields)
        for (i in 0 until numFields) {
            val fieldOid = window.getUIntBE(offset); offset += 4
            val len = window.getIntBE(offset); offset += 4
            if (len == -1) {
                rawAttributes.add(null)
            } else {
                val fieldWindow = window.slice(offset, len)
                rawAttributes.add(parseEagerContainer(fieldWindow, fieldOid, typeRegistry))
                offset += len
            }
        }
        
        return PgComposite(pgType, rawAttributes, typeRegistry)
    }

    fun parsePgRange(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgRange {
        val pgType = typeRegistry.types[oid] as? PgType.Range
            ?: throw IllegalStateException("Oczekiwano typu Range dla OID $oid")
            
        var offset = 0
        val flags = window[offset]; offset += 1
        
        val isEmpty = (flags.toInt() and 0x01) != 0
        val isLowerInfinite = (flags.toInt() and 0x08) != 0
        val isLowerNull = (flags.toInt() and 0x20) != 0
        val isUpperInfinite = (flags.toInt() and 0x10) != 0
        val isUpperNull = (flags.toInt() and 0x40) != 0
        
        var rawLowerBound: Any? = null
        if (!isEmpty && !isLowerInfinite && !isLowerNull) {
            val len = window.getIntBE(offset); offset += 4
            val boundWindow = window.slice(offset, len)
            rawLowerBound = parseEagerContainer(boundWindow, pgType.subtypeOid, typeRegistry)
            offset += len
        }
        
        var rawUpperBound: Any? = null
        if (!isEmpty && !isUpperInfinite && !isUpperNull) {
            val len = window.getIntBE(offset); offset += 4
            val boundWindow = window.slice(offset, len)
            rawUpperBound = parseEagerContainer(boundWindow, pgType.subtypeOid, typeRegistry)
            offset += len
        }
        
        return PgRange(pgType.subtypeOid, flags, rawLowerBound, rawUpperBound, typeRegistry)
    }

    fun parsePgMultirange(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgMultirange {
        val pgType = typeRegistry.types[oid] as? PgType.Multirange
            ?: throw IllegalStateException("Oczekiwano typu Multirange dla OID $oid")
            
        var offset = 0
        val numRanges = window.getIntBE(offset); offset += 4
        
        val ranges = mutableListOf<PgRange>()
        for (i in 0 until numRanges) {
            val len = window.getIntBE(offset); offset += 4
            val rangeWindow = window.slice(offset, len)
            ranges.add(parsePgRange(rangeWindow, pgType.rangeOid, typeRegistry))
            offset += len
        }
        
        return PgMultirange(ranges)
    }
}
