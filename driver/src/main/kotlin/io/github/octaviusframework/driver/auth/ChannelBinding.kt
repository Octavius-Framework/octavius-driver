package io.github.octaviusframework.driver.auth

/**
 * How hard the driver insists on binding the SCRAM exchange to the TLS channel underneath it.
 *
 * Channel binding stops an intermediary from relaying an authentication exchange it cannot compute
 * a proof for: the client proof covers a hash of the certificate the server presented, so a proxy
 * that terminates TLS with a certificate of its own produces a proof the real server rejects. It
 * earns its keep under `sslmode=require`, where the connection is encrypted but nothing checks who
 * is on the other end of it.
 */
enum class ChannelBinding(val value: String) {
    /** Never offer channel binding; the exchange declares no support for it. */
    DISABLE("disable"),

    /** Bind when the connection is encrypted and the server offers it, carry on when not. Default. */
    PREFER("prefer"),

    /** Refuse to authenticate at all unless the exchange is bound to the TLS channel. */
    REQUIRE("require");

    companion object {
        fun of(value: String?): ChannelBinding {
            return entries.find { it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: PREFER
        }
    }
}
