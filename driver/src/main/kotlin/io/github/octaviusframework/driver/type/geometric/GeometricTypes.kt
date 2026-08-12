package io.github.octaviusframework.driver.type.geometric

data class PgPoint(val x: Double, val y: Double)

data class PgLine(val a: Double, val b: Double, val c: Double)

data class PgLseg(val p1: PgPoint, val p2: PgPoint)

data class PgBox(val high: PgPoint, val low: PgPoint)

data class PgPath(val closed: Boolean, val points: List<PgPoint>)

data class PgPolygon(val points: List<PgPoint>)

data class PgCircle(val center: PgPoint, val radius: Double)
