package io.github.octaviusframework.driver.io

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// Global singleton for the entire library
internal val virtualDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()