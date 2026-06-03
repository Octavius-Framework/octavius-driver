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
        
        // TODO: Parsonowanie URL jdbc:octavius://host:port/database i nawiązywanie fizycznego połączenia TCP
        return OctaviusConnection()
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
