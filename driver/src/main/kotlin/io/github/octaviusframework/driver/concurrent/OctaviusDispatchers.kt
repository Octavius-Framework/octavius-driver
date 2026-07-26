package io.github.octaviusframework.driver.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


/**
 * Global object providing pre-configured concurrent components optimized for database operations.
 *
 * It acts as a central registry for Coroutine dispatchers and traditional Java execution primitives
 * built on top of Project Loom's Virtual Threads (Java 21+).
 *
 * Utilizing virtual threads ensures that blocking I/O calls, typical in database drivers,
 * are handled with high scalability and performance without monopolizing expensive platform (OS) threads.
 */
object OctaviusDispatchers {

    /**
     * A global [ExecutorService] using Java 21+ Virtual Threads.
     * Use this for integrating with legacy Java APIs (e.g. CompletableFuture).
     */
    val VirtualExecutor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * A globally shared [CoroutineDispatcher] backed by Virtual Threads.
     * Use this for launching database operations via Kotlin Coroutines.
     *
     * Example:
     * ```
     * launch(OctaviusDispatchers.Virtual) {
     *     session.createNativeQuery("...").execute()
     * }
     * ```
     */
    val Virtual: CoroutineDispatcher = VirtualExecutor.asCoroutineDispatcher()
}