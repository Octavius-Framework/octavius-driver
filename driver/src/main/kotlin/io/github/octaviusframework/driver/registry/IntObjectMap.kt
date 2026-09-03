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
internal class IntObjectMap<V> {
    private var keys: IntArray
    private var _values: Array<Any?>
    private var _size = 0
    private var threshold: Int

    constructor(capacity: Int = 128) {
        // Rounds up to a power of two, which collapses to zero for a capacity of one or less - a table with
        // no slots at all, where the mask becomes -1 and the first lookup indexes out of bounds.
        keys = IntArray((Integer.highestOneBit(capacity - 1) shl 1).coerceAtLeast(2))
        _values = arrayOfNulls(keys.size)
        threshold = (keys.size * 0.75).toInt()
    }
    
    constructor(other: IntObjectMap<V>) {
        keys = other.keys.copyOf()
        _values = other._values.copyOf()
        _size = other._size
        threshold = other.threshold
    }

    /**
     * Stores [value] under [key], growing and rehashing the table when it passes its load factor.
     *
     * @param key The key, which may not be `0` - that value marks an empty slot.
     * @param value The value to store.
     * @throws IllegalArgumentException if [key] is `0`.
     */
    fun put(key: Int, value: V) {
        require(key != 0) { "Key 0 is reserved" }
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
    /**
     * The value stored under [key], or `null` if there is none. A lookup for `0` is `null` rather than an
     * error, so the reserved key reads as absent on the way out even though it is refused on the way in.
     *
     * @param key The key to look up.
     * @return The value, or `null`.
     */
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

    /**
     * Whether [key] has a value. Since the map holds no nulls, this is `get(key) != null`.
     *
     * @param key The key to look for.
     * @return `true` if something is stored under it.
     */
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
        
    /** Whether the map holds nothing. */
    fun isEmpty() = _size == 0
    /** Whether the map holds anything. */
    fun isNotEmpty() = _size > 0
    
    /**
     * [put], written as `map[key] = value`.
     *
     * @param key The key, which may not be `0`.
     * @param value The value to store.
     */
    operator fun set(key: Int, value: V) {
        put(key, value)
    }

    /**
     * Runs [action] for every entry, walking the table directly so that nothing is allocated to iterate -
     * no entry objects, no boxed keys.
     *
     * Order is the table's, which is neither insertion order nor key order and changes when the map grows.
     *
     * @param action Called with each key and its value.
     */
    fun forEach(action: (key: Int, value: V) -> Unit) {
        val ks = keys
        val vs = _values
        for (i in ks.indices) {
            val k = ks[i]
            if (k != 0) {
                @Suppress("UNCHECKED_CAST")
                action(k, vs[i] as V)
            }
        }
    }
}
