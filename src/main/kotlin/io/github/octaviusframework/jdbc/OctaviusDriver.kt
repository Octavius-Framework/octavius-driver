package io.github.octaviusframework.jdbc

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLFeatureNotSupportedException
import java.util.Properties
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
        
        val prefix = "jdbc:octavius://"
        if (!url.startsWith(prefix)) return null
        
        val withoutPrefix = url.substring(prefix.length)
        val slashIndex = withoutPrefix.indexOf('/')
        
        val hostPort = if (slashIndex != -1) withoutPrefix.substring(0, slashIndex) else withoutPrefix
        val database = if (slashIndex != -1) withoutPrefix.substring(slashIndex + 1).substringBefore('?') else "postgres"
        
        val colonIndex = hostPort.indexOf(':')
        val host = if (colonIndex != -1) hostPort.substring(0, colonIndex) else hostPort
        val port = if (colonIndex != -1) hostPort.substring(colonIndex + 1).toIntOrNull() ?: 5432 else 5432

        val user = info?.getProperty("user") ?: "postgres"
        val password = info?.getProperty("password")

        val stream = io.github.octaviusframework.network.PgStream(host, port)
        
        val startupParams = mapOf(
            "user" to user,
            "database" to database,
            "client_encoding" to "UTF8"
        )
        
        stream.sendMessage(io.github.octaviusframework.network.messages.StartupMessage(startupParams))
        stream.flush()
        
        val authenticator = io.github.octaviusframework.auth.Authenticator(stream)
        authenticator.authenticate(user, password)
        
        return OctaviusConnection(stream, url)
    }

    override fun acceptsURL(url: String): Boolean {
        return url.startsWith("jdbc:octavius:")
    }

    override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> {
        return emptyArray()
    }

    override fun getMajorVersion(): Int = 1
    override fun getMinorVersion(): Int = 0
    override fun jdbcCompliant(): Boolean = false
    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()
}
