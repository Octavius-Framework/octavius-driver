package io.github.octaviusframework.migrations.history

import io.github.octaviusframework.driver.exception.ConcurrencyException
import io.github.octaviusframework.driver.exception.ConcurrencyExceptionReason
import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.MigrationException
import io.github.octaviusframework.migrations.MigrationExceptionReason
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.zip.CRC32
import kotlin.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * The advisory lock that makes two instances starting at once safe.
 *
 * It **waits** rather than giving up on the spot, bounded by `lock_timeout` - the instance that lost the race
 * should start a moment later, not fail to start. The key comes from the history table's name, so two
 * applications keeping separate histories in one database do not wait for each other.
 */
internal class MigrationLock(schema: String, table: String, private val timeout: Duration) {

    private val key = crc32("$schema.$table")

    /**
     * Takes the lock, runs [block], and gives the lock back however that went.
     *
     * @throws MigrationException `LOCK_NOT_ACQUIRED` if the wait ran out - which means another migrator is
     * still going, or a session somewhere is holding the lock and not moving.
     */
    fun <T> withLock(session: OctaviusSessionOperations, block: () -> T): T {
        val previousTimeout = acquire(session)
        try {
            return block()
        } finally {
            release(session, previousTimeout)
        }
    }

    /** @return the session's previous `lock_timeout`, to be put back afterwards. */
    private fun acquire(session: OctaviusSessionOperations): String {
        val previousTimeout = session.createNativeQuery("SHOW lock_timeout").fetchFieldStrict<String>()

        session.createNativeQuery("SET lock_timeout = '${timeout.inWholeMilliseconds}ms'").execute()
        logger.debug { "Waiting for the migration lock ($NAMESPACE, $key), up to $timeout" }

        try {
            // Simple query, rows discarded: pg_advisory_lock answers `void`, which is a row all the same.
            session.createNativeQuery("SELECT pg_advisory_lock($NAMESPACE, $key)").execute(ignoreRows = true)
        } catch (e: ConcurrencyException) {
            if (e.reason != ConcurrencyExceptionReason.LOCK_NOT_AVAILABLE) throw e
            restoreTimeout(session, previousTimeout)
            throw MigrationException(
                MigrationExceptionReason.LOCK_NOT_ACQUIRED,
                "Waited $timeout for the migration lock and did not get it. Another migrator is still " +
                    "running, or a session is holding the lock without finishing - pg_locks, filtered to " +
                    "locktype = 'advisory' and objid = $key, says which backend has it.",
                cause = e
            )
        } catch (e: Throwable) {
            restoreTimeout(session, previousTimeout)
            throw e
        }

        return previousTimeout
    }

    private fun release(session: OctaviusSessionOperations, previousTimeout: String) {
        try {
            session.createNativeQuery("SELECT pg_advisory_unlock($NAMESPACE, $key)").execute(ignoreRows = true)
        } catch (e: Exception) {
            // Logged and no further: a lock this session cannot give back goes when the session does, and
            // raising here would replace whatever the run was already failing with.
            logger.warn(e) { "Could not release the migration lock ($NAMESPACE, $key)" }
        }
        restoreTimeout(session, previousTimeout)
    }

    private fun restoreTimeout(session: OctaviusSessionOperations, previousTimeout: String) {
        try {
            session.createNativeQuery("SET lock_timeout = '$previousTimeout'").execute()
        } catch (e: Exception) {
            logger.warn(e) { "Could not put lock_timeout back to '$previousTimeout'" }
        }
    }

    private companion object {
        /**
         * The first half of the key, shared by every Octavius migrator.
         *
         * Advisory locks live in one space for the whole database, shared with anything else that takes them,
         * so the pair has to be unlikely to collide with a number somebody else picked. This half says
         * "Octavius", the other says which history table.
         */
        val NAMESPACE = crc32("io.github.octaviusframework.migrations")

        /** CRC32 folded into the signed 32-bit integer `pg_advisory_lock` takes. */
        fun crc32(text: String): Int {
            val crc = CRC32()
            crc.update(text.toByteArray(Charsets.UTF_8))
            return crc.value.toInt()
        }
    }
}
