package io.github.octaviusframework.client

/**
 * Carries a [DataResult.Failure] out through a transaction that only understands exceptions.
 *
 * Built without a stack trace and without suppression: it never reaches the caller and is never logged, so
 * capturing a trace for it would be paying for a diagnostic nobody reads. The failure it carries has the
 * driver's own trace and query context already.
 */
internal class RollbackSignal(val failure: DataResult.Failure) :
    RuntimeException(null, null, false, false)
