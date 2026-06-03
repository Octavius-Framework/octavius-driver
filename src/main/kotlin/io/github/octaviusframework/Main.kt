package io.github.octaviusframework

import java.sql.DriverManager
import java.util.Properties
import io.github.octaviusframework.jdbc.OctaviusConnection

fun main() {
    println("Zaczynamy test!")
    
    val props = Properties()
    props.setProperty("user", "postgres")
    props.setProperty("password", "1234")
    
    val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres", props)
    val octaviusConn = connection.unwrap(OctaviusConnection::class.java)
    
    println("Sukces! Załadowano ${octaviusConn.typeRegistry.types.size} typów z pg_catalog.pg_type do rejestru.")
    
    println("Sukces, testujemy Extended Query (SELECT 12345)...")
    val result = octaviusConn.queryExecutor.executeExtendedQuery("SELECT 12345 AS my_number, 'hello' AS my_string")
    
    println("Odczytano wierszy: ${result.rows.size}")
    for (row in result.rows) {
        val col1Size = row.columns[0]?.size ?: 0
        val col2Size = row.columns[1]?.size ?: 0
        println("Wiersz -> kolumna 1 [binarnie, $col1Size bajtów], kolumna 2 [binarnie, $col2Size bajtów]")
    }
}
