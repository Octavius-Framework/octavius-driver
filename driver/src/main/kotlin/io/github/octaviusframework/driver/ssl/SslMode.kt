package io.github.octaviusframework.driver.ssl

/**
 * How much protection the connection demands of its transport, in PostgreSQL's own vocabulary.
 *
 * The modes form a ladder, and the rung that matters is between [REQUIRE] and [VERIFY_CA]: everything
 * below [VERIFY_CA] encrypts the connection without establishing who is on the other end of it, which
 * an intermediary can satisfy with a certificate of its own. [ChannelBinding][io.github.octaviusframework.driver.auth.ChannelBinding]
 * is what closes that gap without a certificate chain to check against.
 *
 * @property value The spelling used in a JDBC URL and by `libpq`.
 */
enum class SslMode(val value: String) {
    /** Never encrypt. A server that demands TLS refuses the connection. */
    DISABLE("disable"),

    /** Encrypt if the server offers it, connect in the clear if not. The default. */
    PREFER("prefer"),

    /** Refuse to connect unencrypted. The certificate itself is not checked. */
    REQUIRE("require"),

    /** Encrypt, and verify the server's certificate chains to a trusted CA. Needs `sslrootcert`. */
    VERIFY_CA("verify-ca"),

    /** As [VERIFY_CA], and additionally require the certificate to name the host being connected to. */
    VERIFY_FULL("verify-full");

    companion object {
        /**
         * Parses a mode from its URL spelling, accepting the enum name as well.
         *
         * @param value `disable`, `prefer`, `require`, `verify-ca`, `verify-full`, or the equivalent
         *   enum name; case-insensitive.
         * @return The matching mode, or [PREFER] for anything unrecognized, including `null`.
         */
        fun of(value: String?): SslMode {
            return entries.find { it.value.equals(value, ignoreCase = true) || it.name.replace("_", "-").equals(value, ignoreCase = true) }
                ?: PREFER
        }
    }
}
