package io.github.octaviusframework.types

import io.github.octaviusframework.query.QueryExecutor
import java.nio.ByteBuffer

object TypeRegistryLoader {

    fun load(typeRegistry: TypeRegistry, queryExecutor: QueryExecutor) {
        // Krok 1: Pobranie podstawowych typów
        val typesSql = """
            SELECT t.oid, t.typname, t.typrelid, t.typelem, t.typarray, n.nspname 
            FROM pg_catalog.pg_type t
            JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace
        """.trimIndent()
        
        val typesResult = queryExecutor.executeExtendedQuery(typesSql)
        
        for (row in typesResult.rawRows) {
            if (row.columns.size < 6) continue
            val oidBytes = row.columns[0] ?: continue
            val nameBytes = row.columns[1] ?: continue
            val typrelidBytes = row.columns[2] ?: continue
            val typelemBytes = row.columns[3] ?: continue
            val typarrayBytes = row.columns[4] ?: continue
            val nspnameBytes = row.columns[5] ?: continue
            
            val oid = ByteBuffer.wrap(oidBytes).int
            val name = String(nameBytes, Charsets.UTF_8)
            val typrelid = ByteBuffer.wrap(typrelidBytes).int
            val typelem = ByteBuffer.wrap(typelemBytes).int
            val typarray = ByteBuffer.wrap(typarrayBytes).int
            val schema = String(nspnameBytes, Charsets.UTF_8)
            
            typeRegistry.types[oid] = PgType(oid, name, typrelid, typelem, typarray)
            
            // Mapujemy ten konkretny OID na handler zdefiniowany w rejestrze
            typeRegistry.bindOidToHandler(oid, schema, name)
        }

        // Krok 2: Pobranie struktury kompozytów z pg_attribute
        val attrSql = "SELECT attrelid, attnum, attname, atttypid FROM pg_catalog.pg_attribute WHERE attnum > 0 AND attisdropped = false ORDER BY attrelid, attnum"
        val attrResult = queryExecutor.executeExtendedQuery(attrSql)

        for (row in attrResult.rawRows) {
            if (row.columns.size < 4) continue
            val attrelidBytes = row.columns[0] ?: continue
            val attnumBytes = row.columns[1] ?: continue
            val attnameBytes = row.columns[2] ?: continue
            val atttypidBytes = row.columns[3] ?: continue

            val attrelid = ByteBuffer.wrap(attrelidBytes).int
            val attnum = ByteBuffer.wrap(attnumBytes).short.toInt()
            val attname = String(attnameBytes, Charsets.UTF_8)
            val atttypid = ByteBuffer.wrap(atttypidBytes).int

            val attr = PgAttribute(attrelid, attnum, attname, atttypid)
            typeRegistry.relationAttributes.getOrPut(attrelid) { mutableListOf() }.add(attr)
        }
    }
}
