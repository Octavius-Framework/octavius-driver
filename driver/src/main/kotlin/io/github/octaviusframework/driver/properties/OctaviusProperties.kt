package io.github.octaviusframework.driver.properties

import java.util.Properties
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class OctaviusProperties(val info: Properties = Properties()) {

    var user: String? by PropertyDelegate(info)
    var password: String? by PropertyDelegate(info)
    var host: String? by PropertyDelegate(info)
    var port: Int? by IntPropertyDelegate(info)
    var database: String? by PropertyDelegate(info)
    
    var loginTimeout: Int? by IntPropertyDelegate(info)
    var socketTimeout: Int? by IntPropertyDelegate(info)
    
    var ssl: Boolean? by BooleanPropertyDelegate(info)
    var sslmode: String? by PropertyDelegate(info)
    var sslrootcert: String? by PropertyDelegate(info)
    var sslcert: String? by PropertyDelegate(info)
    var sslkey: String? by PropertyDelegate(info)
    var sslpassword: String? by PropertyDelegate(info)

    companion object {
        fun parse(url: String, info: Properties? = null): OctaviusProperties {
            val octaviusProperties = OctaviusProperties(Properties())
            info?.let { octaviusProperties.info.putAll(it) }

            val prefix = "jdbc:octavius://"
            if (url.startsWith(prefix)) {
                val withoutPrefix = url.substring(prefix.length)
                val slashIndex = withoutPrefix.indexOf('/')

                val hostPort = if (slashIndex != -1) withoutPrefix.substring(0, slashIndex) else withoutPrefix
                val dbPart = if (slashIndex != -1) withoutPrefix.substring(slashIndex + 1) else "postgres"

                octaviusProperties.database = dbPart.substringBefore('?')

                val query = if (dbPart.contains('?')) dbPart.substringAfter('?') else ""
                if (query.isNotEmpty()) {
                    query.split("&").forEach {
                        val parts = it.split("=")
                        if (parts.size == 2) {
                            octaviusProperties.info.setProperty(parts[0], parts[1])
                        }
                    }
                }

                val colonIndex = hostPort.indexOf(':')
                octaviusProperties.host = if (colonIndex != -1) hostPort.substring(0, colonIndex) else hostPort
                octaviusProperties.port = if (colonIndex != -1) hostPort.substring(colonIndex + 1).toIntOrNull() else 5432
            }
            return octaviusProperties
        }
    }

    fun toUrl(): String {
        val h = host ?: "localhost"
        val p = port ?: 5432
        val db = database ?: "postgres"

        val urlBuilder = StringBuilder("jdbc:octavius://$h:$p/$db")

        val queryParams = info.entries.filter {
            val key = it.key.toString()
            key != "host" && key != "port" && key != "database"
        }

        if (queryParams.isNotEmpty()) {
            urlBuilder.append("?")
            val queryString = queryParams.joinToString("&") { "${it.key}=${it.value}" }
            urlBuilder.append(queryString)
        }

        return urlBuilder.toString()
    }
}

class PropertyDelegate(private val properties: Properties) : ReadWriteProperty<Any?, String?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): String? {
        return properties.getProperty(property.name)
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
        if (value == null) {
            properties.remove(property.name)
        } else {
            properties.setProperty(property.name, value)
        }
    }
}

class IntPropertyDelegate(private val properties: Properties) : ReadWriteProperty<Any?, Int?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int? {
        return properties.getProperty(property.name)?.toIntOrNull()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int?) {
        if (value == null) {
            properties.remove(property.name)
        } else {
            properties.setProperty(property.name, value.toString())
        }
    }
}

class BooleanPropertyDelegate(private val properties: Properties) : ReadWriteProperty<Any?, Boolean?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean? {
        return properties.getProperty(property.name)?.toBoolean()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean?) {
        if (value == null) {
            properties.remove(property.name)
        } else {
            properties.setProperty(property.name, value.toString())
        }
    }
}
