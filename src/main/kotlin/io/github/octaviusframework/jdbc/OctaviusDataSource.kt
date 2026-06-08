package io.github.octaviusframework.jdbc

import java.io.PrintWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.SQLFeatureNotSupportedException
import java.util.Properties
import java.util.logging.Logger
import javax.sql.DataSource

class OctaviusDataSource : DataSource {
    var url: String? = null
    var user: String? = null
    var password: String? = null
    
    private var logWriter: PrintWriter? = null
    private var loginTimeout: Int = 0

    override fun getConnection(): Connection {
        return getConnection(user, password)
    }

    override fun getConnection(username: String?, pass: String?): Connection {
        val jdbcUrl = url ?: throw SQLException("URL must be set on OctaviusDataSource")
        val props = Properties()
        if (username != null) props.setProperty("user", username)
        if (pass != null) props.setProperty("password", pass)
        
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

    override fun getParentLogger(): Logger = throw SQLFeatureNotSupportedException()

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw SQLException("Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)
}

fun DataSource.unwrapToOctavius(): OctaviusConnection {
    return this.unwrap(OctaviusConnection::class.java)
}

inline fun <reified T> DataSource.unwrap(): T {
    return this.unwrap(T::class.java)
}

fun DataSource.getOctaviusConnection(): OctaviusConnection {
    return this.getConnection().unwrapToOctavius()
}

fun DataSource.getOctaviusConnection(username: String?, pass: String?): OctaviusConnection {
    return this.getConnection(username, pass).unwrapToOctavius()
}

