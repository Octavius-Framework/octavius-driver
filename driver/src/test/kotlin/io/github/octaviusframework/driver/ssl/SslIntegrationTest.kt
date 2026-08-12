package io.github.octaviusframework.driver.ssl

import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

class SslIntegrationTest {

    @Test
    fun testSslModeRequire() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        assumeTrue(runSslTest, "Skipping SSL tests locally. Set TEST_SSL=true.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.REQUIRE
        }

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with REQUIRE")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModePrefer() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        assumeTrue(runSslTest, "Skipping SSL tests locally.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.PREFER
        }

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with PREFER (server supports it)")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModeDisable() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        assumeTrue(runSslTest, "Skipping SSL tests locally.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = false
            sslmode = SslMode.DISABLE
        }

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            // Depending on the server configuration, it might reject non-SSL connections, but default postgres docker image allows both.
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(!isSsl, "Connection should NOT be SSL encrypted with DISABLE")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModeVerifyCaFailsWithoutCert() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        assumeTrue(runSslTest, "Skipping SSL tests locally.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            // Not providing trust store or CA certs on purpose
        }

        assertThrows<Exception>("Should throw because we haven't configured a valid CA certificate") {
            getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)
        }
    }

    @Test
    fun testSslModeVerifyCaWithCert() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        val rootCert = System.getenv("SSL_ROOT_CERT")
        assumeTrue(runSslTest && rootCert != null, "Skipping SSL tests locally or no root cert provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            sslrootcert = rootCert
        }

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with VERIFY_CA")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModeVerifyFullWithCert() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        val rootCert = System.getenv("SSL_ROOT_CERT")
        assumeTrue(runSslTest && rootCert != null, "Skipping SSL tests locally or no root cert provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_FULL
            sslrootcert = rootCert
        }

        // Host must match the certificate's CN (localhost)
        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with VERIFY_FULL")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslClientAuth() {
        val runSslTest = System.getenv("TEST_SSL") == "true"
        val rootCert = System.getenv("SSL_ROOT_CERT")
        val clientCert = System.getenv("SSL_CERT")
        val clientKey = System.getenv("SSL_KEY")
        assumeTrue(runSslTest && rootCert != null && clientCert != null && clientKey != null, "Skipping SSL tests locally or no client certs provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            sslrootcert = rootCert
            sslcert = clientCert
            sslkey = clientKey
        }

        val session = getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with client certificates")
        } finally {
            session.close()
        }
    }
}
