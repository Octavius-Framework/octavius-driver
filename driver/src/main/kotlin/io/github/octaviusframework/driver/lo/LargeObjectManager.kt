package io.github.octaviusframework.driver.lo

import io.github.octaviusframework.driver.session.OctaviusSessionImpl


/**
 * Defines access modes for opening PostgreSQL Large Objects.
 */
object LargeObjectMode {
    /** Opens Large Object for writing only. */
    const val WRITE = 0x00020000
    /** Opens Large Object for reading only. */
    const val READ = 0x00040000
    /** Opens Large Object for both reading and writing. */
    const val READ_WRITE = READ or WRITE
}

/**
 * Manager for PostgreSQL Large Objects associated with a specific connection.
 * NOTE: All operations on Large Objects must be performed within a transaction
 * (BEGIN ... COMMIT/ROLLBACK) to be valid in PostgreSQL.
 * Note: PostgreSQL OIDs are unsigned 32-bit integers.
 * In the JVM, they are represented as signed Int, meaning values above 2.14B will appear as negative numbers if printed.
 * This is expected and perfectly safe for database operations.
 */
class LargeObjectManager internal constructor(private val session: OctaviusSessionImpl) {

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
    fun open(oid: Int, mode: Int = LargeObjectMode.READ_WRITE): LargeObject {
        val fd = session.createNativeQuery("SELECT lo_open($1, $2)")
            .fetchFieldStrict<Int>(oid, mode)

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
