package io.github.octaviusframework.driver.message.backend

/**
 * The description of a single field (column) as it arrives inside a `RowDescription` message.
 *
 * This is the wire form, where every reference is an OID and every modifier is raw. It is resolved into
 * [io.github.octaviusframework.driver.row.ColumnMetadata] once per result, and never reaches a caller as it is.
 *
 * @property name The name of the field - the alias, where the query gave the column one.
 * @property tableOid The OID of the relation the column was written against, or 0 if it is not a plain column reference.
 * @property columnAttrNumber The attribute number of the column within that relation, or 0 if it is not a plain column reference.
 * @property dataTypeOid The object ID of the field's data type.
 * @property dataTypeSize The data type size (in bytes). A negative value denotes a variable-width type.
 * @property typeModifier The type modifier of the data type.
 * @property formatCode The format code indicating how the field is represented (0 for text, 1 for binary).
 */
internal class FieldDescription(
    val name: String,
    val tableOid: Int,
    val columnAttrNumber: Short,
    val dataTypeOid: Int,
    val dataTypeSize: Short,
    val typeModifier: Int,
    val formatCode: Short
)
