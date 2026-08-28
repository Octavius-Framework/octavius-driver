package io.github.octaviusframework.migrations.fixtures.badname

import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.OctaviusMigration

/** Implements the interface, carries no version: the scan reads the name and refuses it. */
class NotAMigrationName : OctaviusMigration {
    override fun migrate(session: OctaviusSessionOperations) = Unit
}
