package io.github.octaviusframework.container

/**
 * Wewnętrzny pojemnik na parametr, który wymusza użycie konkretnego OID.
 * Przydatny przy przekierowaniu typów (np. String na JSONB) lub dla nulli z określonym typem.
 */
data class PgTypedParameter(
    val value: Any?,
    val oid: UInt
)
