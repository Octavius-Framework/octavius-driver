package io.github.octaviusframework.migrations

import kotlin.time.Instant

/** What kind of thing a history row records. */
enum class MigrationType {
    /** A `.sql` file. */
    SQL,

    /** An [OctaviusMigration] class. */
    CODE,

    /**
     * Not a migration at all: the marker written when an existing database was adopted, saying which version
     * it was already at. Everything at or below it is taken as run without being run.
     */
    BASELINE
}

/**
 * Where a migration got to.
 *
 * A transactional migration is only ever [SUCCESS] here: it wrote its row inside the transaction that ran it,
 * so a failure took the row with it and left no trace. The other two belong to migrations that ran without
 * one, where the database really is part-way and only a person can say what to do about it.
 */
enum class MigrationState {
    /**
     * Started and not heard from since - written before the first statement of a migration that runs outside
     * a transaction. A row still saying this on the next run means the process died in the middle of it.
     */
    RUNNING,

    /** Finished. */
    SUCCESS,

    /** A statement failed, and the ones before it in the same file had already been committed. */
    FAILED
}

/**
 * A row of the history table: a migration that has run against this database, and how it went.
 *
 * @property version The version, or `null` for a repeatable migration.
 * @property description What the name said.
 * @property type Whether this was a `.sql` file or a class.
 * @property script The file name or the class's full name - what identifies it.
 * @property checksum What the file hashed to when it ran, or `null` where there is nothing to compare
 * against, which is every migration written in Kotlin that did not declare one.
 * @property state Where it got to.
 * @property failedStatement Which statement of the script failed, counting from one - only ever set on a
 * migration that ran outside a transaction, since one that ran inside left no row at all.
 * @property executionTimeMs How long it took.
 * @property installedBy The database user that ran it.
 * @property installedOn When.
 */
class AppliedMigration internal constructor(
    val version: MigrationVersion?,
    val description: String,
    val type: MigrationType,
    val script: String,
    val checksum: Long?,
    val state: MigrationState,
    val failedStatement: Int?,
    val executionTimeMs: Long,
    val installedBy: String,
    val installedOn: Instant
) {
    override fun toString(): String = "${version?.canonical ?: "R"} $description ($state)"
}
