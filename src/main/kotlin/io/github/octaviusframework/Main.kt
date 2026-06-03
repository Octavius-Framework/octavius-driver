package io.github.octaviusframework

import java.sql.DriverManager
import io.github.octaviusframework.jdbc.OctaviusConnection

fun main() {
    println("Zaczynamy test!")
    val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres")
    val octaviusConn = connection.unwrap(OctaviusConnection::class.java)
    
    println("Sukces, testujemy Extended Query (SELECT 12345)...")
    val rows = octaviusConn.executeExtendedQuery("SELECT 12345 AS my_number, 'hello' AS my_string")
    
    println("Odczytano wierszy: ${rows.size}")
    for (row in rows) {
        val col1Size = row.columns[0]?.size ?: 0
        val col2Size = row.columns[1]?.size ?: 0
        println("Wiersz -> kolumna 1 [binarnie, $col1Size bajtów], kolumna 2 [binarnie, $col2Size bajtów]")
    }
}
