package io.github.octaviusframework.migrations.fixtures.staticinit

import io.github.octaviusframework.driver.session.OctaviusSessionOperations
import io.github.octaviusframework.migrations.OctaviusMigration

/** Set by the static initialiser below, so a test can see exactly when it ran. */
object StaticInitWitness {
    var ran: Boolean = false
}

/**
 * A migration whose class has a static initialiser.
 *
 * The scan loads this class in order to check its constructor and to hold on to it. Loading is not
 * initialising, and the difference is what a test here pins down: nothing of yours runs until an instance
 * is built, which happens immediately before the migration does.
 */
class V40__Has_a_static_initialiser : OctaviusMigration {

    companion object {
        init {
            StaticInitWitness.ran = true
        }
    }

    override fun migrate(session: OctaviusSessionOperations) = Unit
}
