package io.github.octaviusframework.driver.ssl

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason

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
         * A stated mode that matches nothing is refused rather than resolved to a default.
         *
         * @param value `disable`, `prefer`, `require`, `verify-ca`, `verify-full`, or the equivalent
         *   enum name; case-insensitive. `null` means the mode was not stated at all.
         * @return The matching mode, or [PREFER] when [value] is `null`.
         * @throws InvalidOperationException `INVALID_ARGUMENT` if [value] is stated but unrecognized.
         */
        fun of(value: String?): SslMode {
            if (value == null) return PREFER
            return entries.find { it.value.equals(value, ignoreCase = true) || it.name.replace("_", "-").equals(value, ignoreCase = true) }
                ?: throw InvalidOperationException(
                    InvalidOperationExceptionReason.INVALID_ARGUMENT,
                    details = "Unknown sslmode '$value'. Expected one of: ${entries.joinToString(", ") { it.value }}."
                )
        }
    }
}
