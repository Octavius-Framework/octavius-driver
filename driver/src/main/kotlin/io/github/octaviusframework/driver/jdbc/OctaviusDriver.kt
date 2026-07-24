package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.exception.UnsupportedFeatureException
import io.github.octaviusframework.driver.exception.UnsupportedFeatureExceptionMessage
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
        
        return OctaviusConnectionFactory.createConnection(url, info)
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

