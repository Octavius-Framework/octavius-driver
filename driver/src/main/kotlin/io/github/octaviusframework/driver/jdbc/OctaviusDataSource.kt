package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.exception.UnsupportedFeatureException
import io.github.octaviusframework.driver.exception.UnsupportedFeatureExceptionMessage
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslMode
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.util.logging.Logger
import javax.sql.DataSource

class OctaviusDataSource : DataSource {
    private val octaviusProperties = OctaviusProperties()

    var url: String?
        get() = octaviusProperties.toUrl()
        set(value) {
            if (value != null) {
                val parsed = OctaviusProperties.parse(value)
                octaviusProperties.info.putAll(parsed.info)
            }
        }

    var user: String?
        get() = octaviusProperties.user
        set(value) { octaviusProperties.user = value }

    var password: String?
        get() = octaviusProperties.password
        set(value) { octaviusProperties.password = value }

    var ssl: String?
        get() = octaviusProperties.ssl?.toString()
        set(value) { octaviusProperties.ssl = value?.toBoolean() }

    var sslmode: SslMode?
        get() = octaviusProperties.sslmode?.let { SslMode.of(it) }
        set(value) { octaviusProperties.sslmode = value?.value }

    var sslrootcert: String?
        get() = octaviusProperties.sslrootcert
        set(value) { octaviusProperties.sslrootcert = value }

    var sslcert: String?
        get() = octaviusProperties.sslcert
        set(value) { octaviusProperties.sslcert = value }

    var sslkey: String?
        get() = octaviusProperties.sslkey
        set(value) { octaviusProperties.sslkey = value }

    var sslpassword: String?
        get() = octaviusProperties.sslpassword
        set(value) { octaviusProperties.sslpassword = value }

    private var logWriter: PrintWriter? = null

    override fun getConnection(): Connection {
        return getConnection(user, password)
    }

    override fun getConnection(username: String?, pass: String?): Connection {
        val jdbcUrl = url ?: throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_URL, "URL must be set on OctaviusDataSource")
        
        val props = OctaviusProperties()
        props.info.putAll(octaviusProperties.info)
        
        if (username != null) props.user = username
        if (pass != null) props.password = pass
        
        return DriverManager.getConnection(jdbcUrl, props.info)
    }

    override fun getLogWriter(): PrintWriter? = logWriter
    override fun setLogWriter(out: PrintWriter?) {
        logWriter = out
    }
    
    override fun setLoginTimeout(seconds: Int) { // required by Hikari
        octaviusProperties.loginTimeout = seconds
    }
    
    override fun getLoginTimeout(): Int = octaviusProperties.loginTimeout ?: 0

    override fun getParentLogger(): Logger = throw UnsupportedFeatureException(UnsupportedFeatureExceptionMessage.FEATURE_NOT_SUPPORTED)

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw UnsupportedFeatureException(UnsupportedFeatureExceptionMessage.UNWRAP_ERROR, "Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)
}

