package io.github.octaviusframework.driver.message.backend

class FieldDescription(
    val name: String,
    val tableOid: Int,
    val columnAttrNumber: Short,
    val dataTypeOid: Int,
    val dataTypeSize: Short,
    val typeModifier: Int,
    val formatCode: Short
)