package io.github.octaviusframework.driver.copy

import io.github.octaviusframework.driver.exception.InvalidOperationException
import io.github.octaviusframework.driver.exception.InvalidOperationExceptionReason
import io.github.octaviusframework.driver.jdbc.OctaviusConnection
import io.github.octaviusframework.driver.jdbc.getOctaviusSession
import io.github.octaviusframework.driver.properties.OctaviusProperties
import io.github.octaviusframework.driver.session.OctaviusSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class CopyManagerTest {

    private lateinit var session: OctaviusSession

    private fun newSession(): OctaviusSession {
        val props = OctaviusProperties()
        props.user = "postgres"
        props.password = "1234"
        return getOctaviusSession("jdbc:octavius://localhost:5432/octavius_test", props)
    }

    @BeforeEach
    fun setup() {
        session = newSession()

        session.createNativeQuery("CREATE TABLE IF NOT EXISTS copy_test (id INT, name TEXT)").execute()
        session.createNativeQuery("TRUNCATE TABLE copy_test").execute()
    }

    @AfterEach
    fun teardown() {
        if (::session.isInitialized) {
            session.createNativeQuery("DROP TABLE IF EXISTS copy_test").execute()
            session.close()
        }
    }

    @Test
    fun testCopyInAndCopyOutWithStreams() {
        val copyManager = session.copy

        // 1. COPY IN
        val inputData = "1,Test1\n2,Test2\n3,Test3\n"
        val inputStream = ByteArrayInputStream(inputData.toByteArray(Charsets.UTF_8))
        
        val rowsAffected = copyManager.copyIn("COPY copy_test FROM STDIN WITH (FORMAT CSV)", inputStream)
        assertEquals(3, rowsAffected)

        // Verify data in the database
        val count = session.createNativeQuery("SELECT count(*) FROM copy_test").fetchFieldStrict<Long>()
        assertEquals(3L, count)

        // 2. COPY OUT
        val outputStream = ByteArrayOutputStream()
        copyManager.copyOut("COPY copy_test TO STDOUT WITH (FORMAT CSV)", outputStream)
        
        val outputData = outputStream.toString(Charsets.UTF_8.name())
        assertEquals("1,Test1\n2,Test2\n3,Test3\n", outputData)
    }

    @Test
    fun testCopyInAndCopyOutManualChunks() {
        val copyManager = session.copy

        // 1. COPY IN manually
        val copyIn = copyManager.copyIn("COPY copy_test FROM STDIN WITH (FORMAT CSV)")
        copyIn.writeToCopy("4,Test4\n".toByteArray(Charsets.UTF_8))
        copyIn.writeToCopy("5,Test5\n".toByteArray(Charsets.UTF_8))
        val rowsAffected = copyIn.endCopy()
        assertEquals(2, rowsAffected)

        // 2. COPY OUT manually
        val copyOut = copyManager.copyOut("COPY copy_test TO STDOUT WITH (FORMAT CSV)")
        val resultBytes = ByteArrayOutputStream()
        while (true) {
            val chunk = copyOut.readFromCopy() ?: break
            resultBytes.write(chunk)
        }
        
        val outputData = resultBytes.toString(Charsets.UTF_8.name())
        assertEquals("4,Test4\n5,Test5\n", outputData)
    }

    @Test
    fun testQueriesAreRejectedWhileCopyIsInProgress() {
        val copyIn = session.copy.copyIn("COPY copy_test FROM STDIN WITH (FORMAT CSV)")
        copyIn.writeToCopy("6,Test6\n".toByteArray(Charsets.UTF_8))

        val error = assertFailsWith<InvalidOperationException> {
            session.createNativeQuery("SELECT 1").fetchFieldStrict<Int>()
        }
        assertEquals(InvalidOperationExceptionReason.COPY_IN_PROGRESS, error.reason)

        // The rejection must not disturb the transfer itself
        assertEquals(1, copyIn.endCopy())
        assertEquals(1L, session.createNativeQuery("SELECT count(*) FROM copy_test").fetchFieldStrict<Long>())
    }

    @Test
    fun testSecondCopyOnTheSameSessionIsRejected() {
        val copyIn = session.copy.copyIn("COPY copy_test FROM STDIN WITH (FORMAT CSV)")
        try {
            val error = assertFailsWith<InvalidOperationException> {
                session.copy.copyOut("COPY copy_test TO STDOUT WITH (FORMAT CSV)")
            }
            assertEquals(InvalidOperationExceptionReason.COPY_IN_PROGRESS, error.reason)
        } finally {
            copyIn.cancelCopy()
        }
    }

    @Test
    fun testClosingSessionAbortsAnUnfinishedCopy() {
        val copyIn = session.copy.copyIn("COPY copy_test FROM STDIN WITH (FORMAT CSV)")
        copyIn.writeToCopy("7,Test7\n".toByteArray(Charsets.UTF_8))

        session.close() // No endCopy() - closing must abort the transfer, not commit it
        assertFalse(copyIn.isActive)

        session = newSession()
        val count = session.createNativeQuery("SELECT count(*) FROM copy_test").fetchFieldStrict<Long>()
        assertEquals(0L, count)
    }
}
