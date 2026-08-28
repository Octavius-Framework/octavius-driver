package io.github.octaviusframework.migrations

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Where the migrator looks and what it accepts.
 *
 * @property sqlLocations Where the `.sql` files are. A location is either a classpath path -
 * `db/migration`, or `classpath:db/migration`, the prefix being optional because it is the usual case - or a
 * directory, `filesystem:./ops/sql`, resolved against the process's working directory. Subdirectories are
 * searched too. Empty is allowed for a project whose migrations are all Kotlin.
 * @property codePackages Packages holding [OctaviusMigration] classes, subpackages included. Empty is
 * allowed for a project whose migrations are all `.sql`; empty *along with* [sqlLocations] is not, there
 * being nothing to do.
 * @property classLoader Where to look for classes and classpath resources, for an application whose classes
 * are not on the loader that loaded this one - an OSGi container, a plugin host. `null` uses the default.
 */
data class MigratorConfig(
    val sqlLocations: List<String> = listOf("db/migration"),
    val codePackages: List<String> = emptyList(),
    val classLoader: ClassLoader? = null
)
