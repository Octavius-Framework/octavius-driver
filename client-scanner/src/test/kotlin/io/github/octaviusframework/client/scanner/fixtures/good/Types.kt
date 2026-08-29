package io.github.octaviusframework.client.scanner.fixtures.good

import io.github.octaviusframework.annotation.DynamicallyMappable
import io.github.octaviusframework.annotation.PgCompositeType
import io.github.octaviusframework.annotation.PgEnumType
import io.github.octaviusframework.identifier.CaseConvention
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/** Name derived from the class: `ScanRank` -> `scan_rank`, values `PascalCase` -> `SNAKE_CASE_UPPER`. */
@PgEnumType
enum class ScanRank { Quaestor, Praetor, Consul }

/** Name derived from the class: `ScanProvince` -> `scan_province`. */
@PgCompositeType
data class ScanProvince(val name: String, val capital: String)

/** Name stated, and deliberately unrelated to the class name. */
@Serializable
@DynamicallyMappable("scan_grant")
data class ScanGrant(val iugera: Int)

/** Labels are lowercase in the database, which the annotation now says rather than forcing a manual call. */
@PgEnumType(name = "scan_office", pgConvention = CaseConvention.SNAKE_CASE_LOWER)
enum class ScanOffice { Quaestor, Praetor }

/**
 * A dynamic type whose payload carries a scanned enum.
 *
 * Neither class names a serializer, and the enum is not even `@Serializable`: the scan registered
 * [ScanOffice] as a PostgreSQL enum, and `@Contextual` is what asks for the labels that registration implies.
 */
@Serializable
@DynamicallyMappable("scan_appointment")
data class ScanAppointment(@Contextual val office: ScanOffice)
