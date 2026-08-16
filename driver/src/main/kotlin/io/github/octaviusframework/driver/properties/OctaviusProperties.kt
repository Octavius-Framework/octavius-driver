package io.github.octaviusframework.driver.properties

import io.github.octaviusframework.driver.auth.ChannelBinding
import io.github.octaviusframework.driver.ssl.SslMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*

/**
 * Holds all configuration properties for establishing a connection to a PostgreSQL database
 * using the Octavius driver.
 *
 * It provides a strongly-typed representation of common connection properties (e.g., host,
 * port, user, password, ssl settings) and stores any unrecognised properties in a map.
 * This class also provides utility methods for parsing properties from a JDBC URL and 
 * converting the properties back into a valid JDBC URL.
 */
class OctaviusProperties {
    var user: String? = null
    var password: String? = null
    var serverName: String? = null
    var portNumber: Int? = null
    var databaseName: String? = null

    var loginTimeout: Int? = null
    var socketTimeout: Int? = null

    /**
     * Seconds allowed for a cancel request, covering both its connect and its reads.
     *
     * A cancel travels on a connection of its own, so it can get stuck on a server that the
     * session's own connection is not stuck on - which is why it has a budget separate from
     * [loginTimeout] and [socketTimeout]. Defaults to 10 seconds.
     */
    var cancelSignalTimeout: Int? = null


    var maxCachedRowSize: Int? = null
    var notificationBufferCapacity: Int? = null
    var noticeHandler: String? = null
    var maxParameterWriterCapacity: Int? = null
    var initialParameterWriterCapacity: Int? = null

    var ssl: Boolean? = null
    var sslmode: SslMode? = null
    var sslrootcert: String? = null
    var sslcert: String? = null
    var sslkey: String? = null
    var sslpassword: String? = null

    /**
     * How hard to insist that authentication be bound to the TLS channel. Defaults to
     * [ChannelBinding.PREFER]: bind when the connection is encrypted and the server offers it.
     */
    var channelBinding: ChannelBinding? = null

    val additionalProperties: MutableMap<String, String> = mutableMapOf()

    fun setProperty(key: String, value: String) {
        when (key.lowercase()) {
            "user" -> user = value
            "password" -> password = value
            "servername", "host" -> serverName = value
            "portnumber", "port" -> portNumber = value.toIntOrNull()
            "databasename", "database" -> databaseName = value
            "logintimeout" -> loginTimeout = value.toIntOrNull()
            "sockettimeout" -> socketTimeout = value.toIntOrNull()
            "cancelsignaltimeout" -> cancelSignalTimeout = value.toIntOrNull()
            "maxcachedrowsize" -> maxCachedRowSize = value.toIntOrNull()
            "notificationbuffercapacity" -> notificationBufferCapacity = value.toIntOrNull()
            "noticehandler" -> noticeHandler = value
            "maxparameterwritercapacity" -> maxParameterWriterCapacity = value.toIntOrNull()
            "initialparameterwritercapacity" -> initialParameterWriterCapacity = value.toIntOrNull()
            "ssl" -> ssl = value.toBoolean()
            "sslmode" -> sslmode = SslMode.of(value)
            "sslrootcert" -> sslrootcert = value
            "sslcert" -> sslcert = value
            "sslkey" -> sslkey = value
            "sslpassword" -> sslpassword = value
            "channelbinding", "channel_binding" -> channelBinding = ChannelBinding.of(value)
            else -> additionalProperties[key] = value
        }
    }

    fun merge(other: OctaviusProperties) {
        other.user?.let { user = it }
        other.password?.let { password = it }
        other.serverName?.let { serverName = it }
        other.portNumber?.let { portNumber = it }
        other.databaseName?.let { databaseName = it }
        other.loginTimeout?.let { loginTimeout = it }
        other.socketTimeout?.let { socketTimeout = it }
        other.cancelSignalTimeout?.let { cancelSignalTimeout = it }
        other.maxCachedRowSize?.let { maxCachedRowSize = it }
        other.notificationBufferCapacity?.let { notificationBufferCapacity = it }
        other.noticeHandler?.let { noticeHandler = it }
        other.maxParameterWriterCapacity?.let { maxParameterWriterCapacity = it }
        other.initialParameterWriterCapacity?.let { initialParameterWriterCapacity = it }
        other.ssl?.let { ssl = it }
        other.sslmode?.let { sslmode = it }
        other.sslrootcert?.let { sslrootcert = it }
        other.sslcert?.let { sslcert = it }
        other.sslkey?.let { sslkey = it }
        other.sslpassword?.let { sslpassword = it }
        other.channelBinding?.let { channelBinding = it }
        additionalProperties.putAll(other.additionalProperties)
    }

    fun copy(): OctaviusProperties {
        val newProps = OctaviusProperties()
        newProps.merge(this)
        return newProps
    }

    companion object {
        fun parse(url: String, info: Properties? = null): OctaviusProperties {
            val octaviusProperties = OctaviusProperties()

            info?.forEach { (k, v) ->
                octaviusProperties.setProperty(k.toString(), v.toString())
            }

            val prefix = "jdbc:octavius://"
            if (url.startsWith(prefix)) {
                val withoutPrefix = url.substring(prefix.length)
                val slashIndex = withoutPrefix.indexOf('/')

                val hostPort = if (slashIndex != -1) withoutPrefix.substring(0, slashIndex) else withoutPrefix
                val dbPart = if (slashIndex != -1) withoutPrefix.substring(slashIndex + 1) else "postgres"

                val dbNameRaw = dbPart.substringBefore('?')
                octaviusProperties.databaseName = URLDecoder.decode(dbNameRaw, "UTF-8")

                val query = if (dbPart.contains('?')) dbPart.substringAfter('?') else ""
                if (query.isNotEmpty()) {
                    query.split("&").forEach {
                        // Only the first '=' separates: a value is free to contain more of them,
                        // as a password or an `options=-c key=value` string routinely does.
                        val separator = it.indexOf('=')
                        if (separator > 0) {
                            val key = URLDecoder.decode(it.substring(0, separator), "UTF-8")
                            val value = URLDecoder.decode(it.substring(separator + 1), "UTF-8")
                            octaviusProperties.setProperty(key, value)
                        }
                    }
                }

                val colonIndex = hostPort.indexOf(':')
                if (octaviusProperties.serverName == null) {
                    octaviusProperties.serverName =
                        if (colonIndex != -1) hostPort.substring(0, colonIndex) else hostPort
                }
                if (octaviusProperties.portNumber == null) {
                    octaviusProperties.portNumber =
                        if (colonIndex != -1) hostPort.substring(colonIndex + 1).toIntOrNull() else 5432
                }
            }
            return octaviusProperties
        }
    }

    /**
     * Renders these properties as a JDBC URL.
     *
     * The password is deliberately left out: the result is a string that tends to end up in
     * logs and diagnostics, and nothing in the driver reconstructs a connection from it.
     * Use [copy] when you need a complete, lossless duplicate of the configuration.
     */
    fun toUrl(): String {
        val h = serverName ?: "localhost"
        val p = portNumber ?: 5432
        val db = databaseName ?: "postgres"

        val urlBuilder = StringBuilder("jdbc:octavius://$h:$p/$db")

        val queryParams = mutableMapOf<String, String>()
        user?.let { queryParams["user"] = it }
        loginTimeout?.let { queryParams["loginTimeout"] = it.toString() }
        socketTimeout?.let { queryParams["socketTimeout"] = it.toString() }
        cancelSignalTimeout?.let { queryParams["cancelSignalTimeout"] = it.toString() }
        maxCachedRowSize?.let { queryParams["maxCachedRowSize"] = it.toString() }
        notificationBufferCapacity?.let { queryParams["notificationBufferCapacity"] = it.toString() }
        noticeHandler?.let { queryParams["noticeHandler"] = it }
        maxParameterWriterCapacity?.let { queryParams["maxParameterWriterCapacity"] = it.toString() }
        initialParameterWriterCapacity?.let { queryParams["initialParameterWriterCapacity"] = it.toString() }
        ssl?.let { queryParams["ssl"] = it.toString() }
        sslmode?.let { queryParams["sslmode"] = it.value }
        sslrootcert?.let { queryParams["sslrootcert"] = it }
        sslcert?.let { queryParams["sslcert"] = it }
        sslkey?.let { queryParams["sslkey"] = it }
        sslpassword?.let { queryParams["sslpassword"] = it }
        channelBinding?.let { queryParams["channelBinding"] = it.value }

        queryParams.putAll(additionalProperties)

        if (queryParams.isNotEmpty()) {
            urlBuilder.append("?")
            val queryString = queryParams.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            urlBuilder.append(queryString)
        }

        return urlBuilder.toString()
    }
}
