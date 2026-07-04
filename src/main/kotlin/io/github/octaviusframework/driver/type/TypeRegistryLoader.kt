package io.github.octaviusframework.driver.type

import io.github.octaviusframework.driver.io.getUIntBE
import io.github.octaviusframework.driver.converter.result.mapper.ResultMapper
import io.github.octaviusframework.driver.query.QueryExecutor

object TypeRegistryLoader {

    fun load(typeRegistry: TypeRegistry, queryExecutor: QueryExecutor, searchPath: List<String>) {
        // typtype is b for a base type, c for a composite type (e.g., a table's row type), d for a domain, e for an enum type, p for a pseudo-type, r for a range type, or m for a multirange type.
        val typesSql = """
            SELECT 
                t.oid, t.typname, t.typelem, t.typarray, t.typtype, t.typbasetype, n.nspname,
                e.enumlabel,
                r.rngsubtype,
                a.attname, a.atttypid,
                r.rngmultitypid
            FROM pg_catalog.pg_type t
            JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
            LEFT JOIN pg_catalog.pg_enum e ON t.oid = e.enumtypid
            LEFT JOIN pg_catalog.pg_range r ON t.oid = r.rngtypid
            LEFT JOIN pg_catalog.pg_attribute a ON t.typrelid = a.attrelid AND a.attnum > 0 AND a.attisdropped = false
            WHERE NOT (t.typtype = 'c' AND n.nspname IN ('pg_catalog', 'information_schema'))
            AND NOT (t.typtype = 'p' AND t.typname NOT IN ('void', 'record'))
            ORDER BY t.oid, e.enumsortorder, a.attnum
        """.trimIndent()

        val resultMapper = ResultMapper(typeRegistry.converterRegistry)
        val result = queryExecutor.query(typesSql, mapper = resultMapper)

        val enumMap = mutableMapOf<UInt, MutableList<String>>()
        val attrMap = mutableMapOf<UInt, LinkedHashMap<String, UInt>>()
        val rangeMap = mutableMapOf<UInt, UInt>()
        val multirangeMap = mutableMapOf<UInt, UInt>()

        class BaseTypeInfo(
            val name: String, val typelem: UInt, val typarray: UInt,
            val typtype: Char, val typbasetype: UInt, val schema: String
        )

        val parsedTypes = mutableMapOf<UInt, BaseTypeInfo>()

        for (row in result) {
            val oidBytes = row.fields[0].rawValue!!
            val oid = oidBytes.getUIntBE()

            // We collect the main type information only the first time for a given OID
            if (oid !in parsedTypes) {
                val f1 = row.fields[1].rawValue!!
                val name = String(f1.data, f1.offset, f1.length, Charsets.UTF_8)
                val typelem = row.fields[2].rawValue!!.getUIntBE()
                val typarray = row.fields[3].rawValue!!.getUIntBE()
                val f4 = row.fields[4].rawValue!!
                val typtype = String(f4.data, f4.offset, f4.length, Charsets.UTF_8).first()
                val typbasetype = row.fields[5].rawValue!!.getUIntBE()
                val f6 = row.fields[6].rawValue!!
                val schema = String(f6.data, f6.offset, f6.length, Charsets.UTF_8)

                parsedTypes[oid] = BaseTypeInfo(name, typelem, typarray, typtype, typbasetype, schema)
            }

            val enumLabelBytes = row.fields[7].rawValue
            if (enumLabelBytes != null) {
                val label = String(enumLabelBytes.data, enumLabelBytes.offset, enumLabelBytes.length, Charsets.UTF_8)
                val enumList = enumMap.getOrPut(oid) { mutableListOf() }
                if (!enumList.contains(label)) {
                    enumList.add(label)
                }
            }

            // Range
            val rngSubtypeBytes = row.fields[8].rawValue
            if (rngSubtypeBytes != null) {
                val rngSubtype = rngSubtypeBytes.getUIntBE()
                rangeMap[oid] = rngSubtype

                val multirangeOidBytes = row.fields[11].rawValue
                if (multirangeOidBytes != null) {
                    val multirangeOid = multirangeOidBytes.getUIntBE()
                    if (multirangeOid != 0u) {
                        multirangeMap[multirangeOid] = oid
                    }
                }
            }

            // Composite
            val attNameBytes = row.fields[9].rawValue
            val attTypidBytes = row.fields[10].rawValue

            if (attNameBytes != null && attTypidBytes != null) {
                val attName = String(attNameBytes.data, attNameBytes.offset, attNameBytes.length, Charsets.UTF_8)
                val attTypid = attTypidBytes.getUIntBE()

                val attrList = attrMap.getOrPut(oid) { LinkedHashMap() }
                if (!attrList.containsKey(attName)) {
                    attrList[attName] = attTypid
                }
            }
        }

        val newTypes = mutableMapOf<UInt, PgType>()

        // Final construction of correct instance objects for each detected type
        for ((oid, info) in parsedTypes) {
            val pgType = when {
                info.typtype == 'e' -> PgType.Enum(oid, info.name, info.schema, enumMap[oid] ?: emptyList())
                info.typtype == 'd' -> PgType.Domain(oid, info.name, info.schema, info.typbasetype)
                info.typtype == 'r' -> PgType.Range(oid, info.name, info.schema, rangeMap[oid]!!)
                info.typtype == 'm' -> PgType.Multirange(oid, info.name, info.schema, multirangeMap[oid]!!)
                info.typtype == 'c' -> {
                    val attrs = attrMap[oid] ?: LinkedHashMap()
                    PgType.Composite(oid, info.name, info.schema, attrs)
                }

                info.typtype == 'p' -> {
                    when (info.name) {
                        "record" -> PgType.Record(oid, info.name, info.schema)
                        "void" -> PgType.Void(oid, info.name, info.schema)
                        else -> error("Unreachable code: unexpected pseudo-type ${info.name}")
                    }
                }

                info.typelem != 0u && info.typarray == 0u -> PgType.Array(oid, info.name, info.schema, info.typelem)
                else -> PgType.Base(oid, info.name, info.schema)
            }

            newTypes[oid] = pgType
        }

        typeRegistry.updateTypes(newTypes, searchPath)
    }
}
