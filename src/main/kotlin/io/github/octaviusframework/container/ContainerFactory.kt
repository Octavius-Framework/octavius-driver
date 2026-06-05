package io.github.octaviusframework.container

import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.jdbc.OctaviusConnection

/**
 * Fabryka pozwalająca na ręczne tworzenie pustych (lub pre-inicjalizowanych) kontenerów 
 * od zera, co pozwala na zamianę obiektów dziedzinowych w struktury Postgresa przed wysłaniem do bazy.
 */

/**
 * Tworzy całkowicie nowy, pusty kompozyt na podstawie jego nazwy typu (oraz opcjonalnie schematu).
 */
fun OctaviusConnection.createComposite(typeName: String, schema: String? = null): PgComposite {
    val typeRegistry = this.typeRegistry
    val pgType = typeRegistry.types.values.firstOrNull { 
        it is PgType.Composite && it.name == typeName && (schema == null || it.schema == schema) 
    } as? PgType.Composite
        ?: throw IllegalArgumentException("Typ kompozytowy '$typeName' (schemat: ${schema ?: "dowolny"}) nie istnieje w TypeRegistry")
    
    val fields = pgType.attributes.map { 
        ContainerField(rawValue = null, container = null, value = null) 
    }
    return PgComposite(pgType, fields, typeRegistry)
}

/**
 * Tworzy całkowicie nowy, pusty kompozyt na podstawie jego OID.
 */
fun OctaviusConnection.createComposite(oid: UInt): PgComposite {
    val typeRegistry = this.typeRegistry
    val pgType = typeRegistry.types[oid] as? PgType.Composite
        ?: throw IllegalArgumentException("Typ o OID $oid nie jest kompozytem lub nie istnieje w TypeRegistry")
    
    val fields = pgType.attributes.map { 
        ContainerField(rawValue = null, container = null, value = null) 
    }
    return PgComposite(pgType, fields, typeRegistry)
}

/**
 * Tworzy nową tablicę o podanych rozmiarach wielowymiarowych (wymiary w postaci kolejnych intów, min. 1).
 */
fun OctaviusConnection.createArray(elementOid: UInt, vararg dimensionSizes: Int): PgArray {
    require(dimensionSizes.isNotEmpty()) { "Tablica musi mieć co najmniej 1 wymiar" }
    
    val typeRegistry = this.typeRegistry
    val dimensions = dimensionSizes.map { ArrayDimension(it, 1) }
    
    val totalSize = dimensionSizes.fold(1) { acc, size -> acc * size }
    val values = MutableList<Any?>(totalSize) { null }
    
    return PgArray(elementOid, dimensions, true, null, null, values, typeRegistry)
}

/**
 * Tworzy nową jednowymiarową tablicę z dostarczonymi elementami.
 */
fun OctaviusConnection.createArrayWithElements(elementOid: UInt, vararg elements: Any?): PgArray {
    val array = createArray(elementOid, elements.size)
    for (i in elements.indices) {
        array[i] = elements[i]
    }
    return array
}

/**
 * Tworzy nowy zakres (Range) dla zadanych granic.
 */
fun OctaviusConnection.createRange(elementOid: UInt, lower: Any?, upper: Any?, flags: Byte): PgRange {
    val typeRegistry = this.typeRegistry
    val lowerField = lower?.let { ContainerField(null, if (it is PgContainer) it else null, if (it !is PgContainer) it else null) }
    val upperField = upper?.let { ContainerField(null, if (it is PgContainer) it else null, if (it !is PgContainer) it else null) }
    return PgRange(elementOid, flags, lowerField, upperField, typeRegistry)
}

/**
 * Tworzy nowy zbiór zakresów (Multirange).
 */
fun OctaviusConnection.createMultirange(vararg ranges: PgRange): PgMultirange {
    return PgMultirange(ranges.toList())
}
