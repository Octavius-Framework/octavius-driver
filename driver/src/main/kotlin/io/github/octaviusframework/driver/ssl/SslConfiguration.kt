package io.github.octaviusframework.driver.ssl

data class SslConfiguration(
    val mode: SslMode,
    val rootCertPath: String? = null,
    val certPath: String? = null,
    val keyPath: String? = null,
    val keyPassword: String? = null
)
