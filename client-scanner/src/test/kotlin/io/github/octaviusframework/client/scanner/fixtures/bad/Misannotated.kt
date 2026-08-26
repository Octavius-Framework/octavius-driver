package io.github.octaviusframework.client.scanner.fixtures.bad

import io.github.octaviusframework.annotation.PgEnumType

/** Carries the enum annotation without being an enum. Scanned only by the test that expects the refusal. */
@PgEnumType
data class NotAnEnum(val value: String)
