package io.github.octaviusframework.driver.type.geometric

/**
 * Represents a point in a 2-dimensional space.
 *
 * @property x The X coordinate of the point.
 * @property y The Y coordinate of the point.
 */
data class PgPoint(val x: Double, val y: Double)

/**
 * Represents an infinite line in a 2-dimensional space.
 * The linear equation is represented as ax + by + c = 0.
 *
 * @property a The 'a' coefficient of the linear equation.
 * @property b The 'b' coefficient of the linear equation.
 * @property c The 'c' coefficient of the linear equation.
 */
data class PgLine(val a: Double, val b: Double, val c: Double)

/**
 * Represents a finite line segment defined by its two endpoints.
 *
 * @property p1 The first endpoint of the segment.
 * @property p2 The second endpoint of the segment.
 */
data class PgLseg(val p1: PgPoint, val p2: PgPoint)

/**
 * Represents a rectangular box defined by its upper right (high) and lower left (low) corners.
 *
 * @property high The upper right corner of the box.
 * @property low The lower left corner of the box.
 */
data class PgBox(val high: PgPoint, val low: PgPoint)

/**
 * Represents a geometric path, consisting of a sequence of connected points.
 *
 * @property closed Indicates whether the path is closed (forms a polygon) or open.
 * @property points The list of points defining the path.
 */
data class PgPath(val closed: Boolean, val points: List<PgPoint>)

/**
 * Represents a polygon, consisting of a closed sequence of connected points.
 *
 * @property points The list of points defining the vertices of the polygon.
 */
data class PgPolygon(val points: List<PgPoint>)

/**
 * Represents a circle defined by a center point and a radius.
 *
 * @property center The center point of the circle.
 * @property radius The radius of the circle.
 */
data class PgCircle(val center: PgPoint, val radius: Double)
