package io.github.octaviusframework.driver.jdbc

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslMode
import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

/**
 * Implementation of the standard JDBC [DataSource] interface for the Octavius driver.
 *
 * This class serves as a factory for obtaining connections to a PostgreSQL database.
 * It encapsulates the configuration properties required to establish a connection,
 * such as the server URL, credentials, and SSL settings.
 *
 * Configuration properties can be set either via the [url] property or individually
 * using the respective accessor properties.
 */
class OctaviusDataSource : DataSource {
    private val octaviusProperties = OctaviusProperties()

    /**
     * The JDBC URL used to connect to the database.
     * Setting this property will parse the URL and merge the properties.
     */
    var url: String
        get() = octaviusProperties.toUrl()
        set(value) {
            val parsed = OctaviusProperties.parse(value)
            octaviusProperties.merge(parsed)
        }

    /**
     * The hostname or IP address of the PostgreSQL server.
     */
    var serverName: String?
        get() = octaviusProperties.serverName
        set(value) { octaviusProperties.serverName = value }

    /**
     * The port number on which the PostgreSQL server is listening. Defaults to 5432.
     */
    var portNumber: Int
        get() = octaviusProperties.portNumber ?: 5432
        set(value) { octaviusProperties.portNumber = value }

    /**
     * The name of the database to connect to.
     */
    var databaseName: String?
        get() = octaviusProperties.databaseName
        set(value) { octaviusProperties.databaseName = value }

    /**
     * The username for authenticating with the database.
     */
    var user: String?
        get() = octaviusProperties.user
        set(value) { octaviusProperties.user = value }

    /**
     * The password for authenticating with the database.
     */
    var password: String?
        get() = octaviusProperties.password
        set(value) { octaviusProperties.password = value }

    /**
     * Controls whether SSL is required ("true" or "false").
     */
    var ssl: String?
        get() = octaviusProperties.ssl?.toString()
        set(value) { octaviusProperties.ssl = value?.toBoolean() }

    /**
     * The SSL mode to use (e.g., disable, require, verify-ca, verify-full).
     */
    var sslmode: SslMode?
        get() = octaviusProperties.sslmode
        set(value) { octaviusProperties.sslmode = value }

    /**
     * The path to the root certificate file for verifying the server's certificate.
     */
    var sslrootcert: String?
        get() = octaviusProperties.sslrootcert
        set(value) { octaviusProperties.sslrootcert = value }

    /**
     * The path to the client certificate file for SSL authentication.
     */
    var sslcert: String?
        get() = octaviusProperties.sslcert
        set(value) { octaviusProperties.sslcert = value }

    /**
     * The path to the client private key file for SSL authentication.
     */
    var sslkey: String?
        get() = octaviusProperties.sslkey
        set(value) { octaviusProperties.sslkey = value }

    /**
     * The password for the client private key file, if it is encrypted.
     */
    var sslpassword: String?
        get() = octaviusProperties.sslpassword
        set(value) { octaviusProperties.sslpassword = value }

    private var logWriter: PrintWriter? = null

    override fun getConnection(): Connection {
        return getConnection(user, password)
    }

    override fun getConnection(username: String?, pass: String?): Connection {

        val props = octaviusProperties.copy()
        
        if (username != null) props.user = username
        if (pass != null) props.password = pass
        
        return OctaviusConnectionFactory.createConnection(props)
    }

    override fun getLogWriter(): PrintWriter? = logWriter
    override fun setLogWriter(out: PrintWriter?) {
        logWriter = out
    }

    override fun setLoginTimeout(seconds: Int) { // required by Hikari
        octaviusProperties.loginTimeout = seconds
    }

    override fun getLoginTimeout(): Int = octaviusProperties.loginTimeout ?: 0

    override fun getParentLogger(): Logger = throw InvalidOperationException(InvalidOperationExceptionReason.FEATURE_NOT_SUPPORTED)

    @Suppress("UNCHECKED_CAST")
    override fun <T> unwrap(iface: Class<T>): T {
        if (iface.isInstance(this)) {
            return this as T
        }
        throw InvalidOperationException(InvalidOperationExceptionReason.UNWRAP_ERROR, "Cannot unwrap to ${iface.name}")
    }

    override fun isWrapperFor(iface: Class<*>): Boolean = iface.isInstance(this)
}

