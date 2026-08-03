package io.github.octaviusframework.driver.registry

import io.github.octaviusframework.driver.exception.TypeException
import io.github.octaviusframework.driver.exception.TypeExceptionMessage
import io.github.octaviusframework.driver.type.PgType

class TypeDictionary(
    val types: IntObjectMap<PgType>,
    val typesByName: Map<String, Map<String, Int>>,
    val arrayTypesByElementOid: IntObjectMap<PgType.Array>,
    val rangeTypesByElementOid: IntObjectMap<PgType.Range>,
    val multirangeTypesByRangeOid: IntObjectMap<PgType.Multirange>
) {
    companion object {
        val EMPTY = TypeDictionary(
            IntObjectMap(),
            emptyMap(),
            IntObjectMap(),
            IntObjectMap(),
            IntObjectMap()
        )
    }

    fun getPgType(oid: Int): PgType = types[oid]
        ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, oid = oid, details = "Type with OID $oid not found")

    fun getArrayType(elementOid: Int): PgType.Array = arrayTypesByElementOid[elementOid]
        ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, oid = elementOid, details = "Array type for element OID $elementOid not found")

    fun getRangeType(elementOid: Int): PgType.Range = rangeTypesByElementOid[elementOid]
        ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, oid = elementOid, details = "Range type for element OID $elementOid not found")

    fun getMultirangeType(rangeOid: Int): PgType.Multirange = multirangeTypesByRangeOid[rangeOid]
        ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, oid = rangeOid, details = "Multirange type for range OID $rangeOid not found")

    fun resolveOid(
        typeName: String,
        requestedSchema: String,
        isArray: Boolean = false,
        searchPath: List<String>
    ): Int {
        val schemasForName = typesByName[typeName]
            ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, typeName = typeName, details = "Type '$typeName' not found")

        var resolvedOid: Int? = null
        // 1. If schema is explicitly requested
        if (requestedSchema.isNotEmpty()) {
            resolvedOid = schemasForName[requestedSchema]
                ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, typeName = typeName, details = "Type '$typeName' not found in schema '$requestedSchema'")
        } else {
            // 2. If schema is empty, look in search_path (first match wins)
            for (i in 0 until searchPath.size) {
                val oid = schemasForName[searchPath[i]]
                if (oid != null) {
                    resolvedOid = oid
                    break
                }
            }

            // 3. If not in search_path, check for unambiguous match
            if (resolvedOid == null) {
                if (schemasForName.size == 1) {
                    resolvedOid = schemasForName.values.first()
                } else {
                    throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, typeName = typeName, details = "Ambiguous type '$typeName'. Schema must be specified.")
                }
            }
        }

        return if (isArray) {
            arrayTypesByElementOid[resolvedOid]?.oid
                ?: throw TypeException(TypeExceptionMessage.TYPE_NOT_FOUND, typeName = typeName, details = "Array type for '$typeName' not found")
        } else {
            resolvedOid
        }
    }
}
