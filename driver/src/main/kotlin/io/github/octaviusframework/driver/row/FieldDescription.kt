package io.github.octaviusframework.driver.row

/**
 * Represents the description of a single field (column) in a row returned by a PostgreSQL query.
 *
 * @property name The name of the field.
 * @property tableOid The object ID of the table containing the field, or 0 if it's not a table column.
 * @property columnAttrNumber The attribute number of the column, or 0 if it's not a table column.
 * @property dataTypeOid The object ID of the field's data type.
 * @property dataTypeSize The data type size (in bytes). A negative value denotes a variable-width type.
 * @property typeModifier The type modifier of the data type.
 * @property formatCode The format code indicating how the field is represented (0 for text, 1 for binary).
 */
class FieldDescription(
    val name: String,
    val tableOid: Int,
    val columnAttrNumber: Short,
    val dataTypeOid: Int,
    val dataTypeSize: Short,
    val typeModifier: Int,
    val formatCode: Short
)