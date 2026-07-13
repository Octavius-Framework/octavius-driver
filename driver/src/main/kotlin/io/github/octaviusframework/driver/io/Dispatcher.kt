package io.github.octaviusframework.driver.io

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// Global singletons for the entire library
internal val virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()
internal val virtualDispatcher = virtualExecutor.asCoroutineDispatcher()
