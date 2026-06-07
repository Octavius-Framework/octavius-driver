package io.github.octaviusframework.io

import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

// Globalny singleton dla całej biblioteki
internal val virtualDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()