package io.github.octaviusframework.migrations

import io.github.octaviusframework.driver.exception.OctaviusException

/**
 * Why a migration run stopped.
 *
 * Deliberately coarse, and meant for a log line or a metric to be keyed on. What a human needs in order to
 * fix anything is in `details`, which names the file, the class or the version at fault, and in the cause
 * where there is one - so the split here is by *kind of problem*, not by which check happened to catch it.
 */
enum class MigrationExceptionReason {
    /**
     * One migration cannot be used: its name carries no version, its script opens a transaction of its own,
     * it declares a directive nobody knows, or its class has no constructor to call.
     */
    INVALID_MIGRATION,

    /** Two migrations claim the same identity - the same version, or the same script in two places. */
    DUPLICATE_MIGRATION,

    /** The migrator was set up in a way it cannot work with: nowhere to look, or somewhere that is not there. */
    CONFIGURATION
}

/**
 * Exception thrown when a migration run cannot go on.
 *
 * Every one of these is raised before or instead of touching the database, or else after a failure has
 * already been rolled back - which is the reason there is no `sqlState` on it. A statement the server
 * refused arrives as the driver's own exception instead, unchanged, since the server said it better.
 *
 * @property reason What kind of problem this is, for a log line to be keyed on.
 * @property details What is actually wrong, naming the file, class or version it is wrong about.
 * @param cause The failure underneath, where this one is standing in front of another.
 */
class MigrationException(
    val reason: MigrationExceptionReason,
    val details: String? = null,
    cause: Throwable? = null
) : OctaviusException("MIGRATION_EXCEPTION:${reason.name}", cause = cause) {

    override fun getDetailedMessage(): String = buildString {
        appendLine("Reason: ${generateDeveloperMessage(reason)}")
        if (details != null) appendLine("Details: $details")
    }
}

private fun generateDeveloperMessage(reason: MigrationExceptionReason): String =
    when (reason) {
        MigrationExceptionReason.INVALID_MIGRATION ->
            "A migration cannot be used as it stands - see the details for what is wrong with it."
        MigrationExceptionReason.DUPLICATE_MIGRATION ->
            "Two migrations claim the same identity, and there is no way to tell which one ran."
        MigrationExceptionReason.CONFIGURATION ->
            "The migrator has nowhere usable to look for migrations."
    }
