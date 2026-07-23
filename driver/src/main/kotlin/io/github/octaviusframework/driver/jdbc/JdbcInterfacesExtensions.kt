package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.session.OctaviusSession
import io.github.octaviusframework.driver.session.OctaviusSessionImpl
import java.sql.Connection
import java.sql.DriverManager
import javax.sql.DataSource
import io.github.octaviusframework.driver.properties.OctaviusProperties

// DriverManager
/**
 * Establishes a new [OctaviusSession] using the specified [url] and [properties] properties.
 *
 * @param url A database url of the form `jdbc:subprotocol:subname`.
 * @param properties A list of arbitrary string tag/value pairs as connection arguments.
 * @return An [OctaviusSession] instance.
 */
fun getOctaviusSession(
    url: String,
    properties: OctaviusProperties
): OctaviusSession {
    val conn = DriverManager.getConnection(url, properties.info)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

/**
 * Establishes a new [OctaviusSession] using the specified [properties].
 *
 * @param properties Connection arguments encapsulated in [OctaviusProperties].
 * @return An [OctaviusSession] instance.
 */
fun getOctaviusSession(
    properties: OctaviusProperties
): OctaviusSession {
    val conn = DriverManager.getConnection(properties.toUrl(), properties.info)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

/**
 * Establishes a new [OctaviusSession] using the specified [url], [user], and [password].
 *
 * @param url A database url of the form `jdbc:subprotocol:subname`.
 * @param user The database user on whose behalf the connection is being made.
 * @param password The user's password.
 * @return An [OctaviusSession] instance.
 */
fun getOctaviusSession(
    url: String,
    user: String, password: String
): OctaviusSession {
    val conn = DriverManager.getConnection(url, user, password)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

// DataSource
/**
 * Unwraps this [DataSource] to its underlying [OctaviusDataSource] instance.
 *
 * @return The underlying [OctaviusDataSource].
 */
fun DataSource.unwrapToOctavius(): OctaviusDataSource {
    return this.unwrap(OctaviusDataSource::class.java)
}

/**
 * Unwraps this [DataSource] to an instance of the specified type [T].
 *
 * @return The underlying object of type [T].
 */
inline fun <reified T> DataSource.unwrap(): T {
    return this.unwrap(T::class.java)
}

/**
 * Retrieves a new [OctaviusSession] from this [DataSource].
 *
 * @return An [OctaviusSession] instance.
 */
fun DataSource.getOctaviusSession(): OctaviusSession {
    val conn = this.getConnection()
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

/**
 * Retrieves a new [OctaviusSession] from this [DataSource] using the specified credentials.
 *
 * @param username The database user on whose behalf the connection is being made.
 * @param pass The user's password.
 * @return An [OctaviusSession] instance.
 */
fun DataSource.getOctaviusSession(username: String, pass: String): OctaviusSession {
    val conn = this.getConnection(username, pass)
    return OctaviusSessionImpl(conn, conn.unwrapToOctavius())
}

// Connection
/**
 * Unwraps this [Connection] to an instance of the specified type [T].
 *
 * @return The underlying object of type [T].
 */
inline fun <reified T : Any> Connection.unwrap(): T {
    return this.unwrap(T::class.java)
}

/**
 * Unwraps this [Connection] to its underlying [OctaviusConnection] instance.
 *
 * @return The underlying [OctaviusConnection].
 */
internal fun Connection.unwrapToOctavius(): OctaviusConnection {
    return this.unwrap(OctaviusConnection::class.java)
}
