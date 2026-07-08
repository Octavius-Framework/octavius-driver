package io.github.octaviusframework.driver.ssl

enum class SslMode(val value: String) {
    DISABLE("disable"),
    PREFER("prefer"),
    REQUIRE("require"),
    VERIFY_CA("verify-ca"),
    VERIFY_FULL("verify-full");

    companion object {
        fun of(value: String?): SslMode {
            return entries.find { it.value.equals(value, ignoreCase = true) || it.name.replace("_", "-").equals(value, ignoreCase = true) }
                ?: PREFER
        }
    }
}
