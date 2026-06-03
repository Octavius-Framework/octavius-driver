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
        
        println("Próba połączenia z $url ...")
        val stream = io.github.octaviusframework.network.PgStream("localhost", 5432)
        
        val startupParams = mapOf(
            "user" to "postgres",
            "database" to "postgres",
            "client_encoding" to "UTF8"
        )
        
        println("Wysyłam StartupMessage...")
        stream.sendMessage(io.github.octaviusframework.network.messages.StartupMessage(startupParams))
        
        val authenticator = io.github.octaviusframework.auth.Authenticator(stream)
        authenticator.authenticate("postgres", "1234") // hasło podane przez usera w czacie (docelowo z Properties)
        
        return OctaviusConnection(stream)
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
