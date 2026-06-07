package io.github.octaviusframework.container

import io.github.octaviusframework.types.PgType
import io.github.octaviusframework.jdbc.OctaviusConnection
import io.github.octaviusframework.exceptions.OctaviusTypeException
import io.github.octaviusframework.exceptions.TypeExceptionMessage

/**
 * Fabryka pozwalająca na ręczne tworzenie pustych (lub pre-inicjalizowanych) kontenerów 
 * od zera, co pozwala na zamianę obiektów dziedzinowych w struktury Postgresa przed wysłaniem do bazy.
 */

/**
 * Tworzy całkowicie nowy, pusty kompozyt na podstawie jego nazwy typu (oraz opcjonalnie schematu).
 */
fun OctaviusConnection.createComposite(typeName: String, schema: String = ""): PgComposite {
    val typeRegistry = this.typeRegistry
    val searchPath = this.getSearchPath()
    
    val (resolvedOid, _) = typeRegistry.resolveOid(typeName, schema, searchPath)
    
    val pgType = typeRegistry.types[resolvedOid] as? PgType.Composite
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = resolvedOid.toInt(), details = "Typ $typeName nie jest kompozytem")
    
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
        ?: throw OctaviusTypeException(TypeExceptionMessage.NOT_A_CONTAINER, oid = oid.toInt(), details = "Typ nie jest kompozytem lub nie istnieje w TypeRegistry")
    
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
    
    val elementType = typeRegistry.types[elementOid]
        ?: throw OctaviusTypeException(TypeExceptionMessage.TYPE_NOT_FOUND, details = "Nie znaleziono elementOid = $elementOid w rejestrze")
        
    val arrayOid = elementType.arrayOid 
    if (arrayOid == 0u) {
        throw OctaviusTypeException(TypeExceptionMessage.TYPE_NOT_FOUND, details = "Typ $elementOid nie ma zarejestrowanej tablicy (arrayOid=0)")
    }
    
    return PgArray(arrayOid, elementOid, dimensions, true, null, null, values, typeRegistry)
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
    
    val rangeType = typeRegistry.types.values.firstOrNull { 
        it is PgType.Range && it.subtypeOid == elementOid
    } ?: throw OctaviusTypeException(TypeExceptionMessage.TYPE_NOT_FOUND, details = "Nie znaleziono typu range dla subtypeOid = $elementOid")
    
    return PgRange(rangeType.oid, elementOid, flags, lowerField, upperField, typeRegistry)
}

/**
 * Tworzy nowy zbiór zakresów (Multirange).
 */
fun OctaviusConnection.createMultirange(multirangeOid: UInt, rangeOid: UInt, vararg ranges: PgRange): PgMultirange {
    return PgMultirange(multirangeOid, rangeOid, ranges.toList())
}
