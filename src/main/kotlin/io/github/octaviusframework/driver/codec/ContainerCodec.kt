package io.github.octaviusframework.driver.codec

import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.ByteArrayWindow
import io.github.octaviusframework.driver.io.get
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.io.getUIntBE
import io.github.octaviusframework.driver.type.PgType
import io.github.octaviusframework.driver.type.TypeRegistry
import io.github.octaviusframework.driver.type.containter.ArrayDimension
import io.github.octaviusframework.driver.type.containter.ContainerField
import io.github.octaviusframework.driver.type.containter.PgArray
import io.github.octaviusframework.driver.type.containter.PgComposite
import io.github.octaviusframework.driver.type.containter.PgContainer
import io.github.octaviusframework.driver.type.containter.PgMultirange
import io.github.octaviusframework.driver.type.containter.PgRange

object ContainerCodec {

    // PARSERS

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
            else -> throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Oczekiwano typu kontenerowego"
            )
        }
    }

    fun parsePgArray(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgArray {
        var offset = 0
        if (window.length < 12) throw OctaviusTypeException(
            TypeExceptionMessage.NOT_ENOUGH_DATA,
            details = "Zbyt mało danych dla PgArray (min. 12)"
        )

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

        return PgArray(oid, elementOid, dimensions, windowsList, eagerList, null, typeRegistry)
    }

    fun parsePgComposite(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgComposite {
        val pgType = typeRegistry.types[oid] as? PgType.Composite
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Oczekiwano typu Composite"
            )

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
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Oczekiwano typu Range"
            )

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

        return PgRange(oid, pgType.subtypeOid, flags, lowerField, upperField, typeRegistry)
    }

    fun parsePgMultirange(window: ByteArrayWindow, oid: UInt, typeRegistry: TypeRegistry): PgMultirange {
        val pgType = typeRegistry.types[oid] as? PgType.Multirange
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Oczekiwano typu Multirange"
            )

        var offset = 0
        val numRanges = window.getIntBE(offset); offset += 4

        val ranges = mutableListOf<PgRange>()
        for (i in 0 until numRanges) {
            val len = window.getIntBE(offset); offset += 4
            val rangeWindow = window.slice(offset, len)
            ranges.add(parsePgRange(rangeWindow, pgType.rangeOid, typeRegistry))
            offset += len
        }

        return PgMultirange(pgType.oid, pgType.rangeOid, ranges)
    }

    // SERIALIZERS

    fun serializeContainer(container: PgContainer, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        when (container) {
            is PgArray -> serializePgArray(container, writer, typeRegistry)
            is PgComposite -> serializePgComposite(container, writer, typeRegistry)
            is PgRange -> serializePgRange(container, writer, typeRegistry)
            is PgMultirange -> serializePgMultirange(container, writer, typeRegistry)
            else -> throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                typeName = container::class.simpleName,
                details = "Nieznany typ kontenera"
            )
        }
    }

    private fun writeField(
        field: ContainerField?,
        expectedOid: UInt,
        writer: PgByteWriter,
        typeRegistry: TypeRegistry
    ) {
        if (field == null) {
            writer.writeInt(-1)
            return
        }

        if (field.value != null) {
            val codec = typeRegistry.getCodecByOid<Any>(expectedOid)
                ?: throw OctaviusTypeException(
                    TypeExceptionMessage.MISSING_SERIALIZER,
                    oid = expectedOid,
                    details = "Serializacja wartości: ${field.value}"
                )
            val bytes = codec.toBinary(field.value!!)
            writer.writeInt(bytes.size)
            writer.writeBytes(bytes)
        } else if (field.container != null) {
            val marker = writer.reserveLengthInt()
            serializeContainer(field.container!!, writer, typeRegistry)
            writer.fillLengthInt(marker)
        } else if (field.rawValue != null) {
            writer.writeInt(field.rawValue!!.length)
            writer.writeBytes(field.rawValue!!)
        } else {
            writer.writeInt(-1)
        }
    }

    fun serializePgArray(array: PgArray, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        val count = array.totalElements
        var hasNulls = false
        for (i in 0 until count) {
            if (array.values?.getOrNull(i) == null &&
                array.containers?.getOrNull(i) == null &&
                array.windows?.getOrNull(i) == null
            ) {
                hasNulls = true
                break
            }
        }

        writer.writeInt(array.dimensions.size)
        writer.writeInt(if (hasNulls) 1 else 0)
        writer.writeUInt(array.elementOid)

        for (dim in array.dimensions) {
            writer.writeInt(dim.size)
            writer.writeInt(dim.lowerBound)
        }

        val codec = typeRegistry.getCodecByOid<Any>(array.elementOid)

        for (i in 0 until count) {
            val value = array.values?.getOrNull(i)
            if (value != null) {
                if (codec == null) throw OctaviusTypeException(
                    TypeExceptionMessage.MISSING_SERIALIZER,
                    oid = array.elementOid,
                    details = "Element tablicy"
                )
                val bytes = codec.toBinary(value)
                writer.writeInt(bytes.size)
                writer.writeBytes(bytes)
                continue
            }

            val container = array.containers?.getOrNull(i)
            if (container != null) {
                val marker = writer.reserveLengthInt()
                serializeContainer(container, writer, typeRegistry)
                writer.fillLengthInt(marker)
                continue
            }

            val window = array.windows?.getOrNull(i)
            if (window != null) {
                writer.writeInt(window.length)
                writer.writeBytes(window)
                continue
            }

            writer.writeInt(-1)
        }
    }

    fun serializePgComposite(composite: PgComposite, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeInt(composite.fields.size)
        val attributeOids = composite.type.attributes.values.toList()
        for (i in composite.fields.indices) {
            writer.writeUInt(attributeOids[i])
            writeField(composite.fields[i], attributeOids[i], writer, typeRegistry)
        }
    }

    fun serializePgRange(range: PgRange, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeByte(range.flags)

        if (!range.isEmpty) {
            if (!range.isLowerInfinite && !range.isLowerNull) {
                writeField(range.lowerBoundField, range.elementOid, writer, typeRegistry)
            }
            if (!range.isUpperInfinite && !range.isUpperNull) {
                writeField(range.upperBoundField, range.elementOid, writer, typeRegistry)
            }
        }
    }

    fun serializePgMultirange(multirange: PgMultirange, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeInt(multirange.ranges.size)
        for (range in multirange.ranges) {
            val marker = writer.reserveLengthInt()
            serializePgRange(range, writer, typeRegistry)
            writer.fillLengthInt(marker)
        }
    }
}
