package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.auth.Authenticator
import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.frontend.StartupMessage
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslNegotiator
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties

object OctaviusConnectionFactory {
    fun createConnection(url: String, info: Properties? = null): Connection {
        val properties = OctaviusProperties.parse(url, info)
        return createConnection(url, properties)
    }

    fun createConnection(url: String, properties: OctaviusProperties): Connection {
        val serverName = properties.serverName ?: "localhost"
        val portNumber = properties.portNumber ?: 5432
        val databaseName = properties.databaseName ?: "postgres"
        
        val user = properties.user ?: "postgres"
        val password = properties.password
        val loginTimeout = properties.loginTimeout ?: DriverManager.getLoginTimeout()

        val stream = PgStream(serverName, portNumber, loginTimeout)
        
        val sslNegotiator = SslNegotiator(stream)
        sslNegotiator.negotiate(serverName, portNumber, properties.info)
        
        val startupParams = mapOf(
            "user" to user,
            "database" to databaseName,
            "client_encoding" to "UTF8"
        )
        
        stream.sendMessage(StartupMessage(startupParams))
        stream.flush()
        
        val authenticator = Authenticator(stream)
        authenticator.authenticate(user, password)
        
        stream.networkTimeout = properties.socketTimeout?.let { it * 1000 } ?: 0
        
        val serverVersion = stream.parameters["server_version"]
        if (serverVersion != null) {
            val majorVersion = serverVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            if (majorVersion < 18) {
                stream.close()
                throw OctaviusJdbcException(JdbcExceptionMessage.UNSUPPORTED_SERVER_VERSION, "Octavius JDBC requires PostgreSQL database version 18 or higher. Received version: $serverVersion")
            }
        }
        
        return OctaviusConnection(stream, url)
    }
}
