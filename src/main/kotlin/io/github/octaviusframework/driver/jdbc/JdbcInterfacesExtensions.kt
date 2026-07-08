package io.github.octaviusframework.driver.jdbc

import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

// DriverManager
fun getOctaviusConnection(
    url: String,
    info: Properties
): OctaviusConnection {
    return DriverManager.getConnection(url, info).unwrapToOctavius()
}

fun getOctaviusConnection(
    url: String,
    user: String, password: String
): OctaviusConnection {
    return DriverManager.getConnection(url, user, password).unwrapToOctavius()
}

// DataSource
fun DataSource.unwrapToOctavius(): OctaviusDataSource {
    return this.unwrap(OctaviusDataSource::class.java)
}

inline fun <reified T> DataSource.unwrap(): T {
    return this.unwrap(T::class.java)
}

fun DataSource.getOctaviusConnection(): OctaviusConnection {
    return this.getConnection().unwrapToOctavius()
}

fun DataSource.getOctaviusConnection(username: String?, pass: String?): OctaviusConnection {
    return this.getConnection(username, pass).unwrapToOctavius()
}
// Connection
inline fun <reified T : Any> Connection.unwrap(): T {
    return this.unwrap(T::class.java)
}

fun Connection.unwrapToOctavius(): OctaviusConnection {
    return this.unwrap(OctaviusConnection::class.java)
}