package io.github.octaviusframework.driver.ssl

import io.github.octaviusframework.driver.exception.ExecutionAbortedException
import io.github.octaviusframework.driver.exception.ExecutionAbortedExceptionReason
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.ssl.SslMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.concurrent.Executors
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@EnabledIfEnvironmentVariable(named = "TEST_SSL", matches = "true")
class SslIntegrationTest {

    private val url = "jdbc:octavius://localhost:5433/octavius_test"

    @Test
    fun testSslModeRequire() {
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.REQUIRE
        }

        val session = getOctaviusSession(url, properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with REQUIRE")
        } finally {
            session.close()
        }
    }

    @Test
    fun testNegotiatedProtocolIsTls13() {
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.REQUIRE
        }

        val session = getOctaviusSession(url, properties)

        try {
            // The driver enables TLS 1.2 and 1.3 and lets the server pick, so against a server that
            // offers both - PostgreSQL 18 does, out of the box - the answer has to be 1.3. Asserting
            // "one of the two" would pass with the driver pinned to 1.2, which is exactly the failure
            // this exists to catch: an SSLContext asked for by version caps the handshake at that
            // version whatever enabledProtocols says afterwards, and nothing else reports it.
            val version = session.createNativeQuery("SELECT version FROM pg_stat_ssl WHERE pid = pg_backend_pid()")
                .fetchFieldStrict<String>()
            assertEquals("TLSv1.3", version, "Server and driver both offer TLS 1.3; the handshake should land on it")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModePrefer() {
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.PREFER
        }

        val session = getOctaviusSession(url, properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with PREFER (server supports it)")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModeDisable() {
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = false
            sslmode = SslMode.DISABLE
        }

        val session = getOctaviusSession(url, properties)

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
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            // Not providing trust store or CA certs on purpose
        }

        assertThrows<Exception>("Should throw because we haven't configured a valid CA certificate") {
            getOctaviusSession(url, properties)
        }
    }

    @Test
    fun testSslModeVerifyCaWithCert() {
        val rootCert = System.getenv("SSL_ROOT_CERT")
        assumeTrue(rootCert != null, "No root cert provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            sslrootcert = rootCert
        }

        val session = getOctaviusSession(url, properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with VERIFY_CA")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslModeVerifyFullWithCert() {
        val rootCert = System.getenv("SSL_ROOT_CERT")
        assumeTrue(rootCert != null, "No root cert provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_FULL
            sslrootcert = rootCert
        }

        // Host must match the certificate's CN (localhost)
        val session = getOctaviusSession(url, properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with VERIFY_FULL")
        } finally {
            session.close()
        }
    }

    @Test
    fun testSslClientAuth() {
        val rootCert = System.getenv("SSL_ROOT_CERT")
        val clientCert = System.getenv("SSL_CERT")
        val clientKey = System.getenv("SSL_KEY")
        assumeTrue(rootCert != null && clientCert != null && clientKey != null, "No client certs provided.")

        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.VERIFY_CA
            sslrootcert = rootCert
            sslcert = clientCert
            sslkey = clientKey
        }

        val session = getOctaviusSession(url, properties)

        try {
            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Connection should be SSL encrypted with client certificates")
        } finally {
            session.close()
        }
    }

    @Test
    fun testCancelQueryOverSsl() {
        val properties = OctaviusProperties().apply {
            user = "postgres"
            password = "1234"
            ssl = true
            sslmode = SslMode.REQUIRE
        }

        val session = getOctaviusSession(url, properties)
        val executor = Executors.newSingleThreadExecutor()

        try {
            // A cancel request cannot travel on the session's own connection, so it opens a second
            // one - which has to negotiate TLS for itself before the request can land. If it did
            // not, the request would never reach the server under REQUIRE and pg_sleep would run
            // to completion instead of being aborted.
            executor.submit {
                Thread.sleep(200)
                session.cancelQuery()
            }

            val exception = assertFailsWith<ExecutionAbortedException> {
                session.createNativeQuery("SELECT pg_sleep(5)").fetchRowStrict()
            }
            assertEquals(ExecutionAbortedExceptionReason.QUERY_CANCELED, exception.reason)
            assertEquals("57014", exception.sqlState)

            val isSsl = session.createNativeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid()").fetchFieldStrict<Boolean>()
            assertTrue(isSsl, "Session should survive the cancellation, still encrypted")
        } finally {
            executor.shutdown()
            session.close()
        }
    }
}
