package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.ssl.SslMode
import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.logging.Logger
import javax.sql.DataSource

class OctaviusDataSource : DataSource {
    var url: String? = null
    var user: String? = null
    var password: String? = null
    
    var ssl: String? = null
    var sslmode: SslMode? = null
    var sslrootcert: String? = null
    var sslcert: String? = null
    var sslkey: String? = null
    var sslpassword: String? = null
    
    private var logWriter: PrintWriter? = null
    private var loginTimeout: Int = 0

    override fun getConnection(): Connection {
        return getConnection(user, password)
    }

    override fun getConnection(username: String?, pass: String?): Connection {
        val jdbcUrl = url ?: throw OctaviusJdbcException(JdbcExceptionMessage.INVALID_URL, "URL must be set on OctaviusDataSource")
        val props = Properties()
        if (username != null) props.setProperty("user", username)
        if (pass != null) props.setProperty("password", pass)
        
        if (loginTimeout > 0) {
            props.setProperty("loginTimeout", loginTimeout.toString())
        }
        
        ssl?.let { props.setProperty("ssl", it) }
        sslmode?.let { props.setProperty("sslmode", it.value) }
        sslrootcert?.let { props.setProperty("sslrootcert", it) }
        sslcert?.let { props.setProperty("sslcert", it) }
        sslkey?.let { props.setProperty("sslkey", it) }
        sslpassword?.let { props.setProperty("sslpassword", it) }
        
        return DriverManager.getConnection(jdbcUrl, props)
    }

    override fun getLogWriter(): PrintWriter? = logWriter
    override fun setLogWriter(out: PrintWriter?) {
        logWriter = out
    }
    
    override fun setLoginTimeout(seconds: Int) { // required by Hikari
        loginTimeout = seconds
    }
    
    override fun getLoginTimeout(): Int = loginTimeout

    override fun getParentLogger(): Logger = throw OctaviusJdbcException(JdbcExceptionMessage.FEATURE_NOT_SUPPORTED)

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw OctaviusJdbcException(JdbcExceptionMessage.UNWRAP_ERROR, "Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)
}

