package io.github.octaviusframework.migrations.fixtures.noctor

import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.OctaviusMigration

/** A migration nothing can build: discovery has to say so at the scan, not when its turn comes. */
class V7__Needs_an_argument(private val schema: String) : OctaviusMigration {
    override fun migrate(session: OctaviusSessionOperations) = Unit
}
