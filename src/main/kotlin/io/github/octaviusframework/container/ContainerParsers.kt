package io.github.octaviusframework.container

import io.github.octaviusframework.io.ByteArrayWindow
import io.github.octaviusframework.io.get
import io.github.octaviusframework.io.getIntBE
import io.github.octaviusframework.io.getUIntBE
import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.types.TypeRegistry

object ContainerParsers {

    fun isContainerType(oid: UInt, typeRegistry: TypeRegistry): Boolean {
        val pgType = typeRegistry.types[oid] ?: return false
        return pgType is PgType.Array || pgType is PgType.Composite || pgType is PgType.Range || pgType is PgType.Multirange
    }

    fun parseContainer(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgContainer {
        val pgType = typeRegistry.types[oid]
        return when (pgType) {
            is PgType.Array -> parsePgArray(window, pgType.oid, typeRegistry)
            is PgType.Composite -> parsePgComposite(window, pgType.oid, typeRegistry)
            is PgType.Range -> parsePgRange(window, pgType.oid, typeRegistry)
            is PgType.Multirange -> parsePgMultirange(window, pgType.oid, typeRegistry)
            else -> throw IllegalStateException("Oczekiwano typu kontenerowego dla OID $oid")
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
        val count = if (ndims == 0) 0 else totalElements
        
        val isContainer = isContainerType(elementOid, typeRegistry)
        val windowsList = if (!isContainer) ArrayList<ByteArrayWindow?>(count) else null
        val eagerList = if (isContainer) ArrayList<PgContainer?>(count) else null
        
        for (i in 0 until count) {
            val len = window.getIntBE(offset); offset += 4
            if (len == -1) {
                windowsList?.add(null)
                eagerList?.add(null)
            } else {
                val elementWindow = window.slice(offset, len)
                if (isContainer) {
                    eagerList!!.add(parseContainer(elementWindow, elementOid, typeRegistry))
                } else {
                    windowsList!!.add(elementWindow)
                }
                offset += len
            }
        }
        
        return PgArray(elementOid, dimensions, hasNullsInt != 0, windowsList, eagerList, null, typeRegistry)
    }

    fun parsePgComposite(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgComposite {
        val pgType = typeRegistry.types[oid] as? PgType.Composite
            ?: throw IllegalStateException("Oczekiwano typu Composite dla OID $oid")
            
        var offset = 0
        val numFields = window.getIntBE(offset); offset += 4
        
        val fields = ArrayList<ContainerField>(numFields)
        for (i in 0 until numFields) {
            val fieldOid = window.getUIntBE(offset); offset += 4
            val len = window.getIntBE(offset); offset += 4
            if (len == -1) {
                fields.add(ContainerField(null, null))
            } else {
                val fieldWindow = window.slice(offset, len)
                if (isContainerType(fieldOid, typeRegistry)) {
                    fields.add(ContainerField(fieldWindow, parseContainer(fieldWindow, fieldOid, typeRegistry)))
                } else {
                    fields.add(ContainerField(fieldWindow, null))
                }
                offset += len
            }
        }
        
        return PgComposite(pgType, fields, typeRegistry)
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
        
        val isSubtypeContainer = isContainerType(pgType.subtypeOid, typeRegistry)
        
        var lowerField: ContainerField? = null
        if (!isEmpty && !isLowerInfinite && !isLowerNull) {
            val len = window.getIntBE(offset); offset += 4
            val boundWindow = window.slice(offset, len)
            lowerField = if (isSubtypeContainer) {
                ContainerField(boundWindow, parseContainer(boundWindow, pgType.subtypeOid, typeRegistry))
            } else {
                ContainerField(boundWindow, null)
            }
            offset += len
        }
        
        var upperField: ContainerField? = null
        if (!isEmpty && !isUpperInfinite && !isUpperNull) {
            val len = window.getIntBE(offset); offset += 4
            val boundWindow = window.slice(offset, len)
            upperField = if (isSubtypeContainer) {
                ContainerField(boundWindow, parseContainer(boundWindow, pgType.subtypeOid, typeRegistry))
            } else {
                ContainerField(boundWindow, null)
            }
            offset += len
        }
        
        return PgRange(pgType.subtypeOid, flags, lowerField, upperField, typeRegistry)
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
