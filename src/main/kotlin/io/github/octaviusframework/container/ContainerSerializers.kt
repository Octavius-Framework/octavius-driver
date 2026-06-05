package io.github.octaviusframework.container

import io.github.octaviusframework.io.PgByteWriter
import io.github.octaviusframework.types.TypeRegistry

object ContainerSerializers {

    fun serializeContainer(container: PgContainer, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        when (container) {
            is PgArray -> serializePgArray(container, writer, typeRegistry)
            is PgComposite -> serializePgComposite(container, writer, typeRegistry)
            is PgRange -> serializePgRange(container, writer, typeRegistry)
            is PgMultirange -> serializePgMultirange(container, writer, typeRegistry)
            else -> throw IllegalArgumentException("Nieznany typ kontenera: ${container::class.simpleName}")
        }
    }

    private fun writeField(field: ContainerField?, expectedOid: UInt, writer: PgByteWriter, typeRegistry: TypeRegistry) {
        if (field == null) {
            writer.writeInt(-1)
            return
        }

        if (field.value != null) {
            val handler = typeRegistry.getHandlerByOid<Any>(expectedOid)
                ?: throw IllegalStateException("Brak handlera dla OID $expectedOid przy serializacji wartości: ${field.value}")
            val bytes = handler.toBinary(field.value!!)
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
        writer.writeInt(array.dimensions.size)
        writer.writeInt(if (array.hasNulls) 1 else 0)
        writer.writeUInt(array.elementOid)
        
        for (dim in array.dimensions) {
            writer.writeInt(dim.size)
            writer.writeInt(dim.lowerBound)
        }
        
        val count = array.totalElements
        val handler = typeRegistry.getHandlerByOid<Any>(array.elementOid)
        
        for (i in 0 until count) {
            val value = array.values?.getOrNull(i)
            if (value != null) {
                if (handler == null) throw IllegalStateException("Brak handlera dla elementów tablicy o OID: ${array.elementOid}")
                val bytes = handler.toBinary(value)
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
