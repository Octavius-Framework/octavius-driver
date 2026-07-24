package io.github.octaviusframework.driver.ssl

import io.github.octaviusframework.driver.exception.JdbcExceptionMessage
import io.github.octaviusframework.driver.exception.OctaviusJdbcException
import io.github.octaviusframework.driver.io.PgStream
import io.github.octaviusframework.driver.message.frontend.SSLRequestMessage
import io.github.octaviusframework.driver.properties.OctaviusProperties

class SslNegotiator(private val stream: PgStream) {

    fun negotiate(host: String, port: Int, properties: OctaviusProperties) {
        val sslMode = properties.sslmode ?: if (properties.ssl == true) SslMode.REQUIRE else SslMode.PREFER

        val config = SslConfiguration(
            mode = sslMode,
            rootCertPath = properties.sslrootcert,
            certPath = properties.sslcert,
            keyPath = properties.sslkey,
            keyPassword = properties.sslpassword
        )

        if (config.mode == SslMode.DISABLE) return

        stream.sendMessage(SSLRequestMessage())
        stream.flush()

        when (val response = stream.inputStream.readByte().toInt().toChar()) {
            'S' -> stream.upgradeToSSL(host, port, config)
            'N' -> {
                if (config.mode == SslMode.REQUIRE || config.mode == SslMode.VERIFY_CA || config.mode == SslMode.VERIFY_FULL) {
                    stream.close()
                    throw OctaviusJdbcException(JdbcExceptionMessage.SSL_ERROR, "Server does not support SSL, but sslmode=${config.mode.value} was specified.")
                }
            }
            else -> {
                stream.close()
                throw OctaviusJdbcException(JdbcExceptionMessage.SSL_ERROR, "Unexpected SSL negotiation response: $response")
            }
        }
    }
}
