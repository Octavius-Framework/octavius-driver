package io.github.octaviusframework.client.scanner.fixtures.wrongname

import io.github.octaviusframework.annotation.PgEnumType

/** Names a type the database does not have - the typo a scan is most likely to carry. */
@PgEnumType(name = "no_such_enum_type")
enum class Misnamed { A, B }
