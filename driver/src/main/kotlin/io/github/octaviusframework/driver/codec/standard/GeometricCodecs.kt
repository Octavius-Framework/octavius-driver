package io.github.octaviusframework.driver.codec.standard

import io.github.octaviusframework.driver.codec.TypeCodec
import io.github.octaviusframework.driver.io.PgByteWriter
import io.github.octaviusframework.driver.io.getDoubleBE
import io.github.octaviusframework.driver.io.getIntBE
import io.github.octaviusframework.driver.type.geometric.*

internal object PointCodec : TypeCodec<PgPoint> {
    override val pgTypeName = "point"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 600
    override val kotlinClass = PgPoint::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgPoint = { data, offset, _ ->
        PgPoint(
            x = data.getDoubleBE(offset),
            y = data.getDoubleBE(offset + 8)
        )
    }

    override val toBinary: (PgPoint, PgByteWriter) -> Unit = { value, writer ->
        writer.writeDouble(value.x)
        writer.writeDouble(value.y)
    }
}

internal object LsegCodec : TypeCodec<PgLseg> {
    override val pgTypeName = "lseg"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 601
    override val kotlinClass = PgLseg::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgLseg = { data, offset, _ ->
        PgLseg(
            p1 = PgPoint(data.getDoubleBE(offset), data.getDoubleBE(offset + 8)),
            p2 = PgPoint(data.getDoubleBE(offset + 16), data.getDoubleBE(offset + 24))
        )
    }

    override val toBinary: (PgLseg, PgByteWriter) -> Unit = { value, writer ->
        writer.writeDouble(value.p1.x)
        writer.writeDouble(value.p1.y)
        writer.writeDouble(value.p2.x)
        writer.writeDouble(value.p2.y)
    }
}

internal object BoxCodec : TypeCodec<PgBox> {
    override val pgTypeName = "box"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 603
    override val kotlinClass = PgBox::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgBox = { data, offset, _ ->
        PgBox(
            high = PgPoint(data.getDoubleBE(offset), data.getDoubleBE(offset + 8)),
            low = PgPoint(data.getDoubleBE(offset + 16), data.getDoubleBE(offset + 24))
        )
    }

    override val toBinary: (PgBox, PgByteWriter) -> Unit = { value, writer ->
        writer.writeDouble(value.high.x)
        writer.writeDouble(value.high.y)
        writer.writeDouble(value.low.x)
        writer.writeDouble(value.low.y)
    }
}

internal object LineCodec : TypeCodec<PgLine> {
    override val pgTypeName = "line"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 628
    override val kotlinClass = PgLine::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgLine = { data, offset, _ ->
        PgLine(
            a = data.getDoubleBE(offset),
            b = data.getDoubleBE(offset + 8),
            c = data.getDoubleBE(offset + 16)
        )
    }

    override val toBinary: (PgLine, PgByteWriter) -> Unit = { value, writer ->
        writer.writeDouble(value.a)
        writer.writeDouble(value.b)
        writer.writeDouble(value.c)
    }
}

internal object CircleCodec : TypeCodec<PgCircle> {
    override val pgTypeName = "circle"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 718
    override val kotlinClass = PgCircle::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgCircle = { data, offset, _ ->
        PgCircle(
            center = PgPoint(data.getDoubleBE(offset), data.getDoubleBE(offset + 8)),
            radius = data.getDoubleBE(offset + 16)
        )
    }

    override val toBinary: (PgCircle, PgByteWriter) -> Unit = { value, writer ->
        writer.writeDouble(value.center.x)
        writer.writeDouble(value.center.y)
        writer.writeDouble(value.radius)
    }
}

internal object PathCodec : TypeCodec<PgPath> {
    override val pgTypeName = "path"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 602
    override val kotlinClass = PgPath::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgPath = { data, offset, _ ->
        val closed = data[offset].toInt() != 0
        val npts = data.getIntBE(offset + 1)
        val points = ArrayList<PgPoint>(npts)
        var pos = offset + 5
        for (i in 0 until npts) {
            points.add(PgPoint(data.getDoubleBE(pos), data.getDoubleBE(pos + 8)))
            pos += 16
        }
        PgPath(closed, points)
    }

    override val toBinary: (PgPath, PgByteWriter) -> Unit = { value, writer ->
        writer.writeByte(if (value.closed) 1.toByte() else 0.toByte())
        writer.writeInt(value.points.size)
        for (p in value.points) {
            writer.writeDouble(p.x)
            writer.writeDouble(p.y)
        }
    }
}

internal object PolygonCodec : TypeCodec<PgPolygon> {
    override val pgTypeName = "polygon"
    override val pgSchema: String = "pg_catalog"
    override val oid: Int = 604
    override val kotlinClass = PgPolygon::class
    override val isDefaultForKotlinType = true

    override val fromBinary: (ByteArray, Int, Int) -> PgPolygon = { data, offset, _ ->
        val npts = data.getIntBE(offset)
        val points = ArrayList<PgPoint>(npts)
        var pos = offset + 4
        for (i in 0 until npts) {
            points.add(PgPoint(data.getDoubleBE(pos), data.getDoubleBE(pos + 8)))
            pos += 16
        }
        PgPolygon(points)
    }

    override val toBinary: (PgPolygon, PgByteWriter) -> Unit = { value, writer ->
        writer.writeInt(value.points.size)
        for (p in value.points) {
            writer.writeDouble(p.x)
            writer.writeDouble(p.y)
        }
    }
}
