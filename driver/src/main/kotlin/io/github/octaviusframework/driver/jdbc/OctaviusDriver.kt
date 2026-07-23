package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.auth.Authenticator
import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.UnsupportedFeatureExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.exception.UnsupportedFeatureException
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.frontend.StartupMessage
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslNegotiator
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.util.*
import java.util.logging.Logger

class OctaviusDriver : Driver {
    companion object {
        init {
            try {
                DriverManager.registerDriver(OctaviusDriver())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun connect(url: String, info: Properties?): Connection? {
        if (!acceptsURL(url)) return null
        
        val properties = OctaviusProperties.parse(url, info)
        
        val host = properties.host ?: "localhost"
        val port = properties.port ?: 5432
        val database = properties.database ?: "postgres"
        
        val user = properties.user ?: "postgres"
        val password = properties.password
        val loginTimeout = properties.loginTimeout ?: DriverManager.getLoginTimeout()

        val stream = PgStream(host, port, loginTimeout)
        
        val sslNegotiator = SslNegotiator(stream)
        sslNegotiator.negotiate(host, port, properties.info)
        
        val startupParams = mapOf(
            "user" to user,
            "database" to database,
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

    override fun acceptsURL(url: String): Boolean {
        return url.startsWith("jdbc:octavius:")
    }

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> {
        return emptyArray()
    }

    override fun getMajorVersion(): Int = 0
    override fun getMinorVersion(): Int = 5
    override fun jdbcCompliant(): Boolean = false
    override fun getParentLogger(): Logger = throw UnsupportedFeatureException(UnsupportedFeatureExceptionMessage.FEATURE_NOT_SUPPORTED)
}

