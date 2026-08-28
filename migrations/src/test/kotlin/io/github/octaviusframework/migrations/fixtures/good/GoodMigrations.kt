package io.github.octaviusframework.migrations.fixtures.good

import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.OctaviusMigration

/**
 * Fixtures for the discovery tests. None of these is ever constructed by a discovery test - that they are
 * not is one of the things being tested.
 */
class V5__Backfill_provinces : OctaviusMigration {
    override fun migrate(session: OctaviusSessionOperations) = Unit
}

class V6_1__Rename_legions : OctaviusMigration {
    override val transactional: Boolean get() = false
    override fun migrate(session: OctaviusSessionOperations) = Unit
}

class R__Rebuild_indexes : OctaviusMigration {
    override fun migrate(session: OctaviusSessionOperations) = Unit
}
