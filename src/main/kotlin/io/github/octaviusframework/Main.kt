package io.github.octaviusframework

import java.sql.DriverManager

fun main() {
    println("Zaczynamy test!")
    val connection = DriverManager.getConnection("jdbc:octavius://localhost:5432/postgres")
    println("Sukces, Driver nawiązał testowe połączenie: $connection")
}
