package io.github.octaviusframework.jdbc

import io.github.octaviusframework.auth.Authenticator
import io.github.octaviusframework.network.PgStream
import io.github.octaviusframework.network.messages.SSLRequestMessage
import io.github.octaviusframework.network.messages.StartupMessage

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
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
        val dbPart = if (slashIndex != -1) withoutPrefix.substring(slashIndex + 1) else "postgres"
        val database = dbPart.substringBefore('?')
        
        val mergedInfo = Properties()
        info?.let { mergedInfo.putAll(it) }
        
        val query = if (dbPart.contains('?')) dbPart.substringAfter('?') else ""
        if (query.isNotEmpty()) {
            query.split("&").forEach {
                val parts = it.split("=")
                if (parts.size == 2) {
                    mergedInfo.setProperty(parts[0], parts[1])
                }
            }
        }
        
        val colonIndex = hostPort.indexOf(':')
        val host = if (colonIndex != -1) hostPort.substring(0, colonIndex) else hostPort
        val port = if (colonIndex != -1) hostPort.substring(colonIndex + 1).toIntOrNull() ?: 5432 else 5432

        val user = mergedInfo.getProperty("user") ?: "postgres"
        val password = mergedInfo.getProperty("password")
        val ssl = mergedInfo.getProperty("ssl")?.toBoolean() ?: false

        val stream = PgStream(host, port)
        
        if (ssl) {
            stream.sendMessage(SSLRequestMessage())
            stream.flush()
            val response = stream.inputStream.readByte().toInt().toChar()
            if (response == 'S') {
                stream.upgradeToSSL(host, port)
            } else if (response == 'N') {
                throw SQLException("Server does not support SSL, but ssl=true was specified.")
            } else {
                throw SQLException("Unexpected SSL negotiation response: $response")
            }
        }
        
        val startupParams = mapOf(
            "user" to user,
            "database" to database,
            "client_encoding" to "UTF8"
        )
        
        stream.sendMessage(StartupMessage(startupParams))
        stream.flush()
        
        val authenticator = Authenticator(stream)
        authenticator.authenticate(user, password)
        
        val serverVersion = stream.parameters["server_version"]
        if (serverVersion != null) {
            val majorVersion = serverVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0
            if (majorVersion < 18) {
                stream.close()
                throw SQLException("Octavius JDBC wymaga bazy PostgreSQL w wersji co najmniej 18. Otrzymano wersję: $serverVersion")
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

    override fun getMajorVersion(): Int = 1
    override fun getMinorVersion(): Int = 0
    override fun jdbcCompliant(): Boolean = false
    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()
}
