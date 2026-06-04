package io.github.octaviusframework.types

import java.nio.ByteBuffer

interface PgDecoder<T> {
    fun decodeBinary(bytes: ByteArray): T
}

object IntDecoder : PgDecoder<Int> {
    override fun decodeBinary(bytes: ByteArray): Int {
        require(bytes.size == 4) { "Int4 must be exactly 4 bytes" }
        return ByteBuffer.wrap(bytes).int
    }
}

object StringDecoder : PgDecoder<String> {
    override fun decodeBinary(bytes: ByteArray): String {
        return String(bytes, Charsets.UTF_8)
    }
}

class CompositeDecoder(
    private val registry: TypeRegistry,
    private val compositeOid: Int
) : PgDecoder<Map<String, Any?>> {
    
    @Suppress("UNCHECKED_CAST")
    override fun decodeBinary(bytes: ByteArray): Map<String, Any?> {
        val buffer = ByteBuffer.wrap(bytes)
        val numFields = buffer.int
        
        val pgType = registry.types[compositeOid]
        val attributes = pgType?.relationId?.let { registry.relationAttributes[it] }
        
        val result = mutableMapOf<String, Any?>()
        
        for (i in 0 until numFields) {
            val typeOid = buffer.int
            val length = buffer.int
            
            // Mapowanie nazw pól, domyślnie field_X jeśli nie ma w słowniku
            val fieldName = attributes?.getOrNull(i)?.name ?: "field_$i"
            
            if (length == -1) {
                result[fieldName] = null
            } else {
                val fieldBytes = ByteArray(length)
                buffer.get(fieldBytes)
                
                val decoder = registry.getDecoder(typeOid)
                if (decoder != null) {
                    val decodedValue = (decoder as PgDecoder<Any>).decodeBinary(fieldBytes)
                    result[fieldName] = decodedValue
                } else {
                    // Fallback jeśli nie mamy dekodera dla zagnieżdżonego typu
                    result[fieldName] = fieldBytes
                }
            }
        }
        
        return result
    }
}
