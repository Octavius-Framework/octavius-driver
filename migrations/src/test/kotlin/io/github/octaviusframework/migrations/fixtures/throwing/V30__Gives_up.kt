package io.github.octaviusframework.migrations.fixtures.throwing

import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.OctaviusMigration

/** A migration that fails in its own code rather than in the database, to check what the run makes of that. */
class V30__Gives_up : OctaviusMigration {
    override fun migrate(session: OctaviusSessionOperations): Unit = throw IllegalStateException("the aqueduct is dry")
}
