package io.github.octaviusframework.driver.registry

/**
 * A highly optimized, primitive-key map implementation that maps primitive [Int] keys to object values.
 *
 * This implementation avoids the memory and performance overhead of autoboxing integers into [java.lang.Integer]
 * objects, which occurs when using standard collections like `HashMap<Int, V>`.
 * It utilizes open addressing with linear probing for fast lookups and insertions.
 * 
 * Note: The key `0` is reserved internally as an empty slot indicator and cannot be used.
 *
 * @param V the type of values maintained by this map
 */
class IntObjectMap<V> {
    private var keys: IntArray
    private var _values: Array<Any?>
    private var _size = 0
    private var threshold: Int

    constructor(capacity: Int = 128) {
        keys = IntArray(Integer.highestOneBit(capacity - 1) shl 1)
        _values = arrayOfNulls(keys.size)
        threshold = (keys.size * 0.75).toInt()
    }
    
    constructor(other: IntObjectMap<V>) {
        keys = other.keys.copyOf()
        _values = other._values.copyOf()
        _size = other._size
        threshold = other.threshold
    }

    fun put(key: Int, value: V) {
        if (key == 0) throw IllegalArgumentException("Key 0 is reserved")
        if (_size >= threshold) rehash()

        val mask = keys.size - 1
        var idx = hash(key) and mask
        while (true) {
            val k = keys[idx]
            if (k == 0) {
                keys[idx] = key
                this._values[idx] = value
                _size++
                return
            }
            if (k == key) {
                this._values[idx] = value
                return
            }
            idx = (idx + 1) and mask
        }
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(key: Int): V? {
        if (key == 0) return null
        val mask = keys.size - 1
        var idx = hash(key) and mask
        while (true) {
            val k = keys[idx]
            if (k == 0) return null
            if (k == key) return _values[idx] as V
            idx = (idx + 1) and mask
        }
    }

    fun containsKey(key: Int): Boolean {
        return get(key) != null
    }

    private fun hash(key: Int): Int {
        var h = key
        h = h xor (h ushr 16)
        h = h * -2048144789
        h = h xor (h ushr 13)
        h = h * -1028477387
        h = h xor (h ushr 16)
        return h
    }

    private fun rehash() {
        val oldKeys = keys
        val oldValues = _values
        val newCapacity = oldKeys.size * 2
        keys = IntArray(newCapacity)
        _values = arrayOfNulls(newCapacity)
        _size = 0
        threshold = (newCapacity * 0.75).toInt()

        for (i in oldKeys.indices) {
            val k = oldKeys[i]
            if (k != 0) {
                @Suppress("UNCHECKED_CAST")
                put(k, oldValues[i] as V)
            }
        }
    }
    
    val size: Int get() = _size
    
    val entries: List<Map.Entry<Int, V>>
        get() {
            val list = mutableListOf<Map.Entry<Int, V>>()
            for (i in keys.indices) {
                val k = keys[i]
                if (k != 0) {
                    @Suppress("UNCHECKED_CAST")
                    list.add(java.util.AbstractMap.SimpleEntry(k, _values[i] as V))
                }
            }
            return list
        }
        
    val values: List<V>
        get() {
            val list = mutableListOf<V>()
            for (i in keys.indices) {
                if (keys[i] != 0) {
                    @Suppress("UNCHECKED_CAST")
                    list.add(this._values[i] as V)
                }
            }
            return list
        }
        
    fun isEmpty() = _size == 0
    fun isNotEmpty() = _size > 0
    
    operator fun set(key: Int, value: V) {
        put(key, value)
    }
}
