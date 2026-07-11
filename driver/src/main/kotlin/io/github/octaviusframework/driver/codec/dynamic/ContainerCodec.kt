package io.github.octaviusframework.driver.codec.dynamic

import io.github.octaviusframework.driver.codec.PgByteWriter
import io.github.octaviusframework.driver.container.ArrayDimension
import io.github.octaviusframework.driver.container.PgArray
import io.github.octaviusframework.driver.container.PgComposite
import io.github.octaviusframework.driver.container.PgContainer
import io.github.octaviusframework.driver.container.PgMultirange
import io.github.octaviusframework.driver.container.PgRange
import io.github.octaviusframework.driver.container.PgRecord
import io.github.octaviusframework.driver.exception.OctaviusTypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.registry.TypeRegistry
import io.github.octaviusframework.driver.type.PgType


/**
 * Utility object for parsing and serializing PostgreSQL container types.
 * Supports arrays, composites, records, ranges, and multiranges.
 */
internal object ContainerCodec {

    // ------------------------------------------PARSERS----------------------------------------------------------------

    /**
     * Parses a generic field, which can be either a container or a primitive type.
     */
    private fun parseField(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): Any {
        if (isContainerType(oid, typeRegistry)) {
            return parseContainer(data, offset, length, oid, typeRegistry)
        }
        val codec = typeRegistry.getCodecByOid<Any>(oid)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                oid = oid,
                details = "Parsing field of oid $oid"
            )
        return codec.fromBinary(data, offset, length)
    }

    /**
     * Checks if the given OID corresponds to a container type (Array, Composite, Range, Multirange, Record).
     */
    fun isContainerType(oid: Int, typeRegistry: TypeRegistry): Boolean {
        val pgType = typeRegistry.types[oid] ?: return false
        return pgType is PgType.Array || pgType is PgType.Composite || pgType is PgType.Range || pgType is PgType.Multirange || pgType is PgType.Record
    }

    /**
     * Parses a byte array into a [PgContainer] based on the OID.
     */
    fun parseContainer(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgContainer {
        return when (val pgType = typeRegistry.types[oid]) {
            is PgType.Array -> parsePgArray(data, offset, length, pgType.oid, typeRegistry)
            is PgType.Composite -> parsePgComposite(data, offset, length, pgType.oid, typeRegistry)
            is PgType.Range -> parsePgRange(data, offset, length, pgType.oid, typeRegistry)
            is PgType.Multirange -> parsePgMultirange(data, offset, length, pgType.oid, typeRegistry)
            is PgType.Record -> parsePgRecord(data, offset, length, pgType.oid, typeRegistry)
            else -> throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Expected container type"
            )
        }
    }

    /**
     * Parses a PostgreSQL array from its binary format.
     *
     * @param data The byte array containing the payload.
     * @param offset The starting position in the byte array.
     * @param length The total length of the array payload.
     * @param oid The OID of the array type.
     * @param typeRegistry Registry to look up types and codecs.
     * @return The parsed [PgArray].
     */
    fun parsePgArray(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgArray {
        var localOffset = offset
        if (length < 12) throw OctaviusTypeException(
            TypeExceptionMessage.NOT_ENOUGH_DATA,
            details = "Not enough data for PgArray (min. 12)"
        )

        val ndims = data.getIntBE(localOffset); localOffset += 4
        localOffset += 4 // hasNullsInt ignored
        val elementOid = data.getIntBE(localOffset); localOffset += 4

        val dimensions = mutableListOf<ArrayDimension>()
        for (i in 0 until ndims) {
            val size = data.getIntBE(localOffset); localOffset += 4
            val lowerBound = data.getIntBE(localOffset); localOffset += 4
            dimensions.add(ArrayDimension(size, lowerBound))
        }

        val totalElements = dimensions.fold(1) { acc, dim -> acc * dim.size }
        val count = if (ndims == 0) 0 else totalElements

        val elements = ArrayList<Any?>(count)

        for (i in 0 until count) {
            val len = data.getIntBE(localOffset); localOffset += 4
            if (len == -1) {
                elements.add(null)
            } else {
                elements.add(parseField(data, localOffset, len, elementOid, typeRegistry))
                localOffset += len
            }
        }

        return PgArray(oid, elementOid, dimensions, elements, typeRegistry)
    }

    /**
     * Parses a PostgreSQL composite type (row) from its binary format.
     *
     * @param data The byte array containing the payload.
     * @param offset The starting position in the byte array.
     * @param length The total length of the composite payload.
     * @param oid The OID of the composite type.
     * @param typeRegistry Registry to look up types and codecs.
     * @return The parsed [PgComposite].
     */
    fun parsePgComposite(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgComposite {
        val pgType = typeRegistry.types[oid] as? PgType.Composite
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Expected Composite type"
            )

        var localOffset = offset
        val numFields = data.getIntBE(localOffset); localOffset += 4

        val fields = arrayOfNulls<Any?>(numFields)
        for (i in 0 until numFields) {
            val fieldOid = data.getIntBE(localOffset); localOffset += 4
            val len = data.getIntBE(localOffset); localOffset += 4
            if (len != -1) {
                fields[i] = parseField(data, localOffset, len, fieldOid, typeRegistry)
                localOffset += len
            }
        }

        return PgComposite(pgType, fields, typeRegistry)
    }

    /**
     * Parses an anonymous PostgreSQL record type from its binary format.
     *
     * @param data The byte array containing the payload.
     * @param offset The starting position in the byte array.
     * @param length The total length of the record payload.
     * @param oid The OID of the record type.
     * @param typeRegistry Registry to look up types and codecs.
     * @return The parsed [PgRecord].
     */
    fun parsePgRecord(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgRecord {
        val pgType = typeRegistry.types[oid] as? PgType.Record
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Expected Record type"
            )

        var localOffset = offset
        val numFields = data.getIntBE(localOffset); localOffset += 4

        val fields = Array<Any?>(numFields) { null }
        val fieldOids = IntArray(numFields) { 0 }

        for (i in 0 until numFields) {
            val fieldOid = data.getIntBE(localOffset); localOffset += 4
            val len = data.getIntBE(localOffset); localOffset += 4
            fieldOids[i] = fieldOid
            if (len == -1) {
                fields[i] = null
            } else {
                fields[i] = parseField(data, localOffset, len, fieldOid, typeRegistry)
                localOffset += len
            }
        }

        return PgRecord(pgType, fieldOids, fields, typeRegistry)
    }

    /**
     * Parses a PostgreSQL range type from its binary format.
     *
     * @param data The byte array containing the payload.
     * @param offset The starting position in the byte array.
     * @param length The total length of the range payload.
     * @param oid The OID of the range type.
     * @param typeRegistry Registry to look up types and codecs.
     * @return The parsed [PgRange].
     */
    fun parsePgRange(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgRange {
        val pgType = typeRegistry.types[oid] as? PgType.Range
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Expected Range type"
            )

        var localOffset = offset
        val flags = data[localOffset]; localOffset += 1

        val isEmpty = (flags.toInt() and 0x01) != 0
        val isLowerInfinite = (flags.toInt() and 0x08) != 0
        val isLowerNull = (flags.toInt() and 0x20) != 0
        val isUpperInfinite = (flags.toInt() and 0x10) != 0
        val isUpperNull = (flags.toInt() and 0x40) != 0

        var lowerBound: Any? = null
        if (!isEmpty && !isLowerInfinite && !isLowerNull) {
            val len = data.getIntBE(localOffset); localOffset += 4
            lowerBound = parseField(data, localOffset, len, pgType.subtypeOid, typeRegistry)
            localOffset += len
        }

        var upperBound: Any? = null
        if (!isEmpty && !isUpperInfinite && !isUpperNull) {
            val len = data.getIntBE(localOffset); localOffset += 4
            upperBound = parseField(data, localOffset, len, pgType.subtypeOid, typeRegistry)
            localOffset += len
        }

        return PgRange(oid, pgType.subtypeOid, flags, lowerBound, upperBound, typeRegistry)
    }

    /**
     * Parses a PostgreSQL multirange type from its binary format.
     *
     * @param data The byte array containing the payload.
     * @param offset The starting position in the byte array.
     * @param length The total length of the multirange payload.
     * @param oid The OID of the multirange type.
     * @param typeRegistry Registry to look up types and codecs.
     * @return The parsed [PgMultirange].
     */
    fun parsePgMultirange(data: ByteArray, offset: Int, length: Int, oid: Int, typeRegistry: TypeRegistry): PgMultirange {
        val pgType = typeRegistry.types[oid] as? PgType.Multirange
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                oid = oid,
                details = "Expected Multirange type"
            )

        var localOffset = offset
        val numRanges = data.getIntBE(localOffset); localOffset += 4

        val ranges = mutableListOf<PgRange>()
        for (i in 0 until numRanges) {
            val len = data.getIntBE(localOffset); localOffset += 4
            ranges.add(parsePgRange(data, localOffset, len, pgType.rangeOid, typeRegistry))
            localOffset += len
        }

        return PgMultirange(pgType.oid, pgType.rangeOid, ranges)
    }

    // ----------------------------------------------SERIALIZERS--------------------------------------------------------

    /**
     * Serializes a [PgContainer] into the provided [PgByteWriter].
     */
    fun serializeContainer(container: PgContainer, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        when (container) {
            is PgArray -> serializePgArray(container, writer, typeRegistry)
            is PgComposite -> serializePgComposite(container, writer, typeRegistry)
            is PgRange -> serializePgRange(container, writer, typeRegistry)
            is PgMultirange -> serializePgMultirange(container, writer, typeRegistry)
            is PgRecord -> serializePgRecord()
            else -> throw OctaviusTypeException(
                TypeExceptionMessage.NOT_A_CONTAINER,
                typeName = container::class.simpleName,
                details = "Unknown container type"
            )
        }
    }

    /**
     * Writes a single field (primitive or container) to the provided [PgByteWriter].
     * Uses length prefix framing as expected by the PostgreSQL binary protocol.
     */
    private fun writeField(
        value: Any?,
        expectedOid: Int,
        writer: PgByteWriter,
        typeRegistry: TypeRegistry
    ) {
        if (value == null) {
            writer.writeInt(-1)
            return
        }

        if (isContainerType(expectedOid, typeRegistry)) {
            if (value !is PgContainer) {
                throw OctaviusTypeException(
                    TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                    oid = expectedOid,
                    details = "Expected PgContainer for container type, got ${value::class.simpleName}"
                )
            }
            val marker = writer.reserveLengthInt()
            serializeContainer(value, writer, typeRegistry)
            writer.fillLengthInt(marker)
            return
        }

        val codec = typeRegistry.getCodecByOid<Any>(expectedOid)
            ?: throw OctaviusTypeException(
                TypeExceptionMessage.MISSING_CODEC,
                oid = expectedOid,
                details = "Serializing value: $value"
            )
        if (!codec.kotlinClass.isInstance(value)) {
            throw OctaviusTypeException(
                TypeExceptionMessage.INVALID_PARAMETER_TYPE,
                oid = expectedOid,
                details = "Type mismatch. Expected ${codec.kotlinClass.qualifiedName}, got ${value::class.qualifiedName}"
            )
        }
        val marker = writer.reserveLengthInt()
        codec.toBinary(value, writer)
        writer.fillLengthInt(marker)
    }

    /**
     * Serializes a [PgArray] into the provided [PgByteWriter].
     *
     * @param array The array container to serialize.
     * @param writer The binary packet writer.
     * @param typeRegistry Registry to look up types and codecs.
     */
    fun serializePgArray(array: PgArray, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        val count = array.totalElements
        val hasNulls = array.elements.any { it == null }

        writer.writeInt(array.dimensions.size)
        writer.writeInt(if (hasNulls) 1 else 0)
        writer.writeInt(array.elementOid)

        for (dim in array.dimensions) {
            writer.writeInt(dim.size)
            writer.writeInt(dim.lowerBound)
        }

        for (i in 0 until count) {
            writeField(array.elements[i], array.elementOid, writer, typeRegistry)
        }
    }

    /**
     * Serializes a [PgComposite] into the provided [PgByteWriter].
     *
     * @param composite The composite container to serialize.
     * @param writer The binary packet writer.
     * @param typeRegistry Registry to look up types and codecs.
     */
    fun serializePgComposite(composite: PgComposite, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeInt(composite.fields.size)
        val attributeOids = composite.type.attributeOids
        for (i in composite.fields.indices) {
            writer.writeInt(attributeOids[i])
            writeField(composite.fields[i], attributeOids[i], writer, typeRegistry)
        }
    }

    /**
     * Attempting to serialize an anonymous record will throw an exception,
     * since PostgreSQL does not accept anonymous records as bound parameters.
     */
    fun serializePgRecord() {
        throw OctaviusTypeException(
            TypeExceptionMessage.ANONYMOUS_RECORD_NOT_SUPPORTED,
            oid = 2249,
            details = "Postgres cannot accept 'record' type directly as a bound parameter. Use a registered composite type instead."
        )
    }

    /**
     * Serializes a [PgRange] into the provided [PgByteWriter].
     *
     * @param range The range container to serialize.
     * @param writer The binary packet writer.
     * @param typeRegistry Registry to look up types and codecs.
     */
    fun serializePgRange(range: PgRange, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeByte(range.flags)

        if (!range.isEmpty) {
            if (!range.isLowerInfinite && !range.isLowerNull) {
                writeField(range.lowerBound, range.elementOid, writer, typeRegistry)
            }
            if (!range.isUpperInfinite && !range.isUpperNull) {
                writeField(range.upperBound, range.elementOid, writer, typeRegistry)
            }
        }
    }

    /**
     * Serializes a [PgMultirange] into the provided [PgByteWriter].
     *
     * @param multirange The multirange container to serialize.
     * @param writer The binary packet writer.
     * @param typeRegistry Registry to look up types and codecs.
     */
    fun serializePgMultirange(multirange: PgMultirange, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        writer.writeInt(multirange.ranges.size)
        for (range in multirange.ranges) {
            val marker = writer.reserveLengthInt()
            serializePgRange(range, writer, typeRegistry)
            writer.fillLengthInt(marker)
        }
    }
}


