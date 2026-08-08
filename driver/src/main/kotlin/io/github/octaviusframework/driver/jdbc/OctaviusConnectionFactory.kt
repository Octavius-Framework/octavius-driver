package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.auth.Authenticator
import io.github.octaviusframework.driver.exception.InitializationException
import io.github.octaviusframework.driver.exception.InitializationExceptionReason
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.frontend.StartupMessage
import io.github.octaviusframework.driver.notice.NoticeHandler
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslNegotiator
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

/**
 * Factory object responsible for establishing physical connections to a PostgreSQL database.
 *
 * It handles URL parsing, socket creation, SSL negotiation, and the PostgreSQL startup 
 * and authentication sequences. It ensures that the connected server meets the minimum 
 * version requirement (PostgreSQL 18+).
 */
object OctaviusConnectionFactory {
    /**
     * Creates a new database connection using the provided JDBC URL and optional properties.
     *
     * @param url The JDBC URL (e.g., `jdbc:octavius://localhost:5432/db`).
     * @param info Additional connection properties.
     * @return A newly established [Connection] (specifically, an [OctaviusConnection]).
     * @throws InitializationException if the connection cannot be established or the server version is unsupported.
     */
    fun createConnection(url: String, info: Properties? = null): Connection {
        val properties = OctaviusProperties.parse(url, info)
        return createConnection(url, properties)
    }

    /**
     * Creates a new database connection using the provided JDBC URL and pre-parsed [OctaviusProperties].
     *
     * @param url The JDBC URL.
     * @param properties The parsed configuration properties.
     * @return A newly established [Connection].
     * @throws InitializationException if the connection cannot be established or the server version is unsupported.
     */
    fun createConnection(url: String, properties: OctaviusProperties): Connection {
        val serverName = properties.serverName ?: "localhost"
        val portNumber = properties.portNumber ?: 5432
        val databaseName = properties.databaseName ?: "postgres"

        val user = properties.user ?: "postgres"
        val password = properties.password
        val loginTimeout = properties.loginTimeout ?: DriverManager.getLoginTimeout()
        val notificationBufferCapacity = properties.notificationBufferCapacity ?: 256

        val stream = try {
            val handler = properties.noticeHandler?.let { className ->
                val clazz = try {
                    Thread.currentThread().contextClassLoader.loadClass(className)
                } catch (_: ClassNotFoundException) {
                    Class.forName(className)
                }
                
                val kClass = clazz.kotlin
                kClass.objectInstance as? NoticeHandler
                    ?: clazz.getDeclaredConstructor().newInstance() as NoticeHandler
            }
            PgStream(serverName, portNumber, loginTimeout, notificationBufferCapacity, handler)
        } catch (e: Exception) {
            throw InitializationException(InitializationExceptionReason.CONNECTION_ERROR, e.message, e)
        }

        val sslNegotiator = SslNegotiator(stream)
        sslNegotiator.negotiate(serverName, portNumber, properties)

        val startupParams = properties.additionalProperties
        startupParams["client_encoding"] = "UTF8"
        startupParams["user"] = user
        startupParams["database"] = databaseName

        stream.sendMessage(StartupMessage(startupParams))
        stream.flush()

        val authenticator = Authenticator(stream)
        authenticator.authenticate(password)

        stream.networkTimeout = properties.socketTimeout?.let { it * 1000 } ?: 0
        properties.maxCachedRowSize?.let { stream.maxCachedRowSize = it }

        val serverVersion = stream.parameters["server_version"]
        if (serverVersion != null) {
            val majorVersion = serverVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            if (majorVersion < 18) {
                stream.close()
                throw InitializationException(
                    InitializationExceptionReason.UNSUPPORTED_SERVER_VERSION,
                    "Octavius Driver requires PostgreSQL database version 18 or higher. Received version: $serverVersion"
                )
            }
        }

        return OctaviusConnection(
            stream, 
            url, 
            properties.maxParameterWriterCapacity, 
            properties.initialParameterWriterCapacity
        )
    }
}
