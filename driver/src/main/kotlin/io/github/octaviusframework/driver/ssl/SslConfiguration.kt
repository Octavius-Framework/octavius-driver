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
 * @property keyPath Path to the client private key that goes with [certPath]. It must be an
 *   **unencrypted** PKCS#8 RSA key in PEM form; an encrypted one fails to load whatever [keyPassword]
 *   says.
 * @property keyPassword Applied to the in-memory keystore the driver assembles from [certPath] and
 *   [keyPath]. It does **not** decrypt the key file - the key is parsed before this is read - so it has
 *   no effect a caller can observe, and an encrypted key raises
 *   `InitializationException(SSL_ERROR)` rather than being unlocked by it.
 */
data class SslConfiguration(
    val mode: SslMode,
    val rootCertPath: String? = null,
    val certPath: String? = null,
    val keyPath: String? = null,
    val keyPassword: String? = null
)
