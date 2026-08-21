package io.github.octaviusframework.driver.row

import io.github.octaviusframework.driver.exception.MappingException
import io.github.octaviusframework.driver.exception.MappingExceptionReason
import io.github.octaviusframework.driver.message.backend.FieldDescription
import io.github.octaviusframework.driver.registry.TypeDictionary

/**
 * The shape of a result: one [ColumnMetadata] per column, in the order the server returned them.
 *
 * It is built once for a result, from the `RowDescription` that opens it, and every [Row] of that result shares
 * it. Resolving the columns there rather than on demand is what keeps a result internally consistent: a
 * `reloadTypes()` between two reads of the same result cannot leave two of its columns describing themselves
 * against different catalogs.
 *
 * A column whose type the dictionary does not describe fails at that point, with
 * [TypeException][io.github.octaviusframework.driver.exception.TypeException]. Such a column could never have
 * been decoded anyway - a type the catalog load did not see has no codec either - and failing on the description
 * rather than on the first row that happens to carry a value makes it fail the same way every time.
 */
class RowMetadata internal constructor(
    fields: List<FieldDescription>,
    dictionary: TypeDictionary
) {
    /**
     * The metadata of every column, in the order the server returned them.
     */
    val columns: List<ColumnMetadata> = fields.map { it.resolve(dictionary) }

    /**
     * The total number of columns in the row.
     */
    val size: Int get() = columns.size

    /**
     * A list of all column names in the order they were returned.
     */
    val columnNames: List<String> = columns.map { it.name }

    private val nameToIndexCache: Map<String, Int>

    init {
        val map = HashMap<String, Int>()
        columns.forEachIndexed { index, column ->
            map.putIfAbsent(column.name, index)
        }
        nameToIndexCache = map
    }

    /**
     * Finds the zero-based index of the column with the specified [columnName].
     *
     * If multiple columns share the same name, this returns the index of the first occurrence.
     *
     * @param columnName The name of the column.
     * @return The zero-based index of the column.
     * @throws MappingException if no column with the given name exists.
     */
    fun getColumnIndex(columnName: String): Int {
        return nameToIndexCache[columnName] ?: throw MappingException(MappingExceptionReason.COLUMN_NOT_FOUND, "Column not found: $columnName")
    }

    /**
     * Retrieves the metadata of the column at the specified [index].
     *
     * @param index The zero-based index of the column.
     * @return The column's metadata.
     * @throws MappingException if the index is out of bounds.
     */
    fun getColumn(index: Int): ColumnMetadata {
        if (index !in columns.indices) throw MappingException(MappingExceptionReason.COLUMN_NOT_FOUND, "Column index out of bounds: $index")
        return columns[index]
    }

    /**
     * Retrieves the metadata of the column with the specified [columnName].
     *
     * @param columnName The name of the column.
     * @return The column's metadata.
     * @throws MappingException if no column with the given name exists.
     */
    fun getColumn(columnName: String): ColumnMetadata = columns[getColumnIndex(columnName)]

    /**
     * Retrieves the PostgreSQL Object Identifier (OID) for the type of the column at the specified [index].
     *
     * @param index The zero-based index of the column.
     * @return The OID of the column's data type.
     * @throws MappingException if the index is out of bounds.
     */
    fun getOid(index: Int): Int = getColumn(index).oid
}

/**
 * Resolves a field of a `RowDescription` against [dictionary], turning its OIDs into the types and names a
 * caller can read.
 *
 * The type has to be there; the origin does not. A column the server tracked back to a relation it will not
 * name here - one of the catalogs the type load skips, or a table created since the last load - keeps its
 * [ColumnOrigin] with the OIDs the server gave and nothing filled in around them.
 */
private fun FieldDescription.resolve(dictionary: TypeDictionary): ColumnMetadata {
    val origin = if (tableOid == 0) null else {
        val relation = dictionary.findCompositeByRelation(tableOid)
        val attributeNumber = columnAttrNumber.toInt()
        ColumnOrigin(
            relationOid = tableOid,
            attributeNumber = attributeNumber,
            relationName = relation?.name,
            schema = relation?.schema,
            columnName = relation?.attributeNameByNumber(attributeNumber)
        )
    }
    return ColumnMetadata(name, dictionary.getPgType(dataTypeOid), typeModifier, origin)
}
