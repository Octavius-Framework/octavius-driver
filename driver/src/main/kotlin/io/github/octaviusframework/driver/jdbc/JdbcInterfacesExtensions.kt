package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionImpl
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import javax.sql.DataSource

// DriverManager
fun getOctaviusSession(
    url: String,
    info: Properties
): OctaviusSession {
    val conn = DriverManager.getConnection(url, info)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

fun getOctaviusSession(
    url: String,
    user: String, password: String
): OctaviusSession {
    val conn = DriverManager.getConnection(url, user, password)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

// DataSource
fun DataSource.unwrapToOctavius(): OctaviusDataSource {
    return this.unwrap(OctaviusDataSource::class.java)
}

inline fun <reified T> DataSource.unwrap(): T {
    return this.unwrap(T::class.java)
}

fun DataSource.getOctaviusSession(): OctaviusSession {
    val conn = this.getConnection()
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

fun DataSource.getOctaviusSession(username: String, pass: String): OctaviusSession {
    val conn = this.getConnection(username, pass)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

// Connection
inline fun <reified T : Any> Connection.unwrap(): T {
    return this.unwrap(T::class.java)
}

internal fun Connection.unwrapToOctavius(): OctaviusConnection {
    return this.unwrap(OctaviusConnection::class.java)
}
