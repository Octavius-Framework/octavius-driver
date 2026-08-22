package io.github.octaviusframework.driver.ssl

/**
 * The TLS settings a connection is established under, gathered from the SSL-related connection properties.
 *
 * Every path here is optional and overrides a default rather than supplying something the JVM lacks:
 * left unset, the platform's own trust and key material is used, exactly as any other Java TLS client
 * would get it. Only [mode] has to be decided.
 *
 * @property mode How much protection the connection demands; see [SslMode].
 * @property rootCertPath Path to the CA certificate the server's chain is verified against, consulted
 *   only under [SslMode.VERIFY_CA] and [SslMode.VERIFY_FULL]. Left unset, verification falls back to
 *   the JVM's default trust store, so a server whose certificate already chains to a public CA - or to
 *   one installed in the platform truststore - needs nothing here.
 * @property certPath Path to the client certificate, for certificate authentication. Takes effect only
 *   together with [keyPath]: with either one missing the driver presents no client certificate at all,
 *   without complaint.
 * @property keyPath Path to the client private key that goes with [certPath]. PKCS#8 in PEM form,
 *   encrypted or not; the algorithm is read off [certPath] rather than assumed, so RSA and EC alike
 *   load without being named.
 * @property keyPassword Decrypts [keyPath] where that key is encrypted - a file opening on
 *   `BEGIN ENCRYPTED PRIVATE KEY`. An encrypted key without it raises
 *   `InitializationException(SSL_ERROR)` naming the property; an unencrypted key ignores it.
 */
data class SslConfiguration(
    val mode: SslMode,
    val rootCertPath: String? = null,
    val certPath: String? = null,
    val keyPath: String? = null,
    val keyPassword: String? = null
)
