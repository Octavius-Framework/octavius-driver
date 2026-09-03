package io.github.octaviusframework.driver.lo

import io.github.octaviusframework.driver.session.OctaviusSession


private const val INV_WRITE = 0x00020000
private const val INV_READ = 0x00040000

/**
 * How a Large Object is opened.
 *
 * @property value The bit mask `lo_open` takes.
 */
enum class LargeObjectMode(val value: Int) {
    /** Opens the Large Object for writing only. */
    WRITE(INV_WRITE),

    /** Opens the Large Object for reading only. */
    READ(INV_READ),

    /** Opens the Large Object for both reading and writing. */
    READ_WRITE(INV_READ or INV_WRITE)
}

/**
 * Manager for PostgreSQL Large Objects associated with a specific connection.
 * NOTE: All operations on Large Objects must be performed within a transaction
 * (BEGIN ... COMMIT/ROLLBACK) to be valid in PostgreSQL.
 * Note: PostgreSQL OIDs are unsigned 32-bit integers.
 * In the JVM, they are represented as signed Int, meaning values above 2.14B will appear as negative numbers if printed.
 * This is expected and perfectly safe for database operations.
 */
class LargeObjectManager internal constructor(private val session: OctaviusSession) {

    /**
     * Creates a new empty Large Object and returns its OID.
     * @return The OID of the newly created Large Object.
     */
    fun create(): Int {
        return session.createNativeQuery("SELECT lo_create(0)")
            .fetchFieldStrict<Int>()
    }

    /**
     * Opens an existing Large Object and returns a descriptor allowing read/write operations.
     * @param oid The OID of the Large Object to open.
     * @param mode The access mode: [LargeObjectMode.READ], [LargeObjectMode.WRITE] or
     *   [LargeObjectMode.READ_WRITE], which is the default.
     * @return A [LargeObject] instance for reading/writing.
     */
    fun open(oid: Int, mode: LargeObjectMode = LargeObjectMode.READ_WRITE): LargeObject {
        val fd = session.createNativeQuery("SELECT lo_open($1, $2)")
            .fetchFieldStrict<Int>(oid, mode.value)

        return LargeObject(session, oid, fd)
    }

    /**
     * Deletes a Large Object from the database.
     * @param oid The OID of the Large Object to remove.
     */
    fun unlink(oid: Int) {
        session.createNativeQuery("SELECT lo_unlink($1)")
            .fetchFieldStrict<Int>(oid)
    }
}
