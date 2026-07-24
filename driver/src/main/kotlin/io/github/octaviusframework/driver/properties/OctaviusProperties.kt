package io.github.octaviusframework.driver.properties

import io.github.octaviusframework.driver.ssl.SslMode
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Properties

class OctaviusProperties {
    var user: String? = null
    var password: String? = null
    var serverName: String? = null
    var portNumber: Int? = null
    var databaseName: String? = null

    var loginTimeout: Int? = null
    var socketTimeout: Int? = null

    var ssl: Boolean? = null
    var sslmode: SslMode? = null
    var sslrootcert: String? = null
    var sslcert: String? = null
    var sslkey: String? = null
    var sslpassword: String? = null

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
            "ssl" -> ssl = value.toBoolean()
            "sslmode" -> sslmode = SslMode.of(value)
            "sslrootcert" -> sslrootcert = value
            "sslcert" -> sslcert = value
            "sslkey" -> sslkey = value
            "sslpassword" -> sslpassword = value
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
        other.ssl?.let { ssl = it }
        other.sslmode?.let { sslmode = it }
        other.sslrootcert?.let { sslrootcert = it }
        other.sslcert?.let { sslcert = it }
        other.sslkey?.let { sslkey = it }
        other.sslpassword?.let { sslpassword = it }
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
                        val parts = it.split("=")
                        if (parts.size == 2) {
                            val key = URLDecoder.decode(parts[0], "UTF-8")
                            val value = URLDecoder.decode(parts[1], "UTF-8")
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

    fun toUrl(): String {
        val h = serverName ?: "localhost"
        val p = portNumber ?: 5432
        val db = databaseName ?: "postgres"

        val urlBuilder = StringBuilder("jdbc:octavius://$h:$p/$db")

        val queryParams = mutableMapOf<String, String>()
        user?.let { queryParams["user"] = it }
        password?.let { queryParams["password"] = it }
        loginTimeout?.let { queryParams["loginTimeout"] = it.toString() }
        socketTimeout?.let { queryParams["socketTimeout"] = it.toString() }
        ssl?.let { queryParams["ssl"] = it.toString() }
        sslmode?.let { queryParams["sslmode"] = it.value }
        sslrootcert?.let { queryParams["sslrootcert"] = it }
        sslcert?.let { queryParams["sslcert"] = it }
        sslkey?.let { queryParams["sslkey"] = it }
        sslpassword?.let { queryParams["sslpassword"] = it }

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
