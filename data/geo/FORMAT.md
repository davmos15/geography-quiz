# GeoQuiz map layer format (GQGM, version 1)

This is the byte-level contract between the geodata pipeline
(`tools/geodata/build_geodata.py`, writer and reference reader in
`tools/geodata/geoformat.py`) and the app's Kotlin reader. Each map layer is
one file in `app/src/main/assets/geo/`. If you change anything here, bump the
format version, update the writer and reader together, and rebuild.

## Conventions

- **Byte order:** little-endian for every fixed-width number
  (`ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`).
- **Types:** `u8`, `u16`, `u32` unsigned; `i16`, `i32` two's-complement
  signed; `f64` IEEE 754 double.
- **string:** `u16` byte length, then that many bytes of UTF-8 (no
  terminator). An empty string is just `00 00`.
- **varint:** unsigned LEB128. 7 bits per byte, least significant group
  first, high bit set on every byte except the last. Values in these files
  fit in 32 bits (at most 5 bytes).
- **zigzag:** signed to unsigned mapping before varint encoding:
  `zz = (n << 1) ^ (n >> 31)`; decode with `n = (zz >>> 1) ^ -(zz & 1)`.
- **Coordinates:** longitude/latitude in degrees (WGS 84, unprojected; the
  app projects at draw time), quantised to `i16` against the layer bounds in
  the header (see "Quantisation").

The file ends exactly after the last feature. A reader should treat trailing
bytes, a short read, an unknown version or an unknown property type as a
corrupt asset.

## Header

| Offset | Size | Type | Field |
|---|---|---|---|
| 0 | 4 | bytes | Magic `GQGM` (`47 51 47 4D`) |
| 4 | 2 | u16 | Format version, currently `1` |
| 6 | 1 | u8 | Layer id (table below) |
| 7 | 1 | u8 | Geometry kind: `1` point, `2` line, `3` polygon |
| 8 | 8 | f64 | `minLon` |
| 16 | 8 | f64 | `minLat` |
| 24 | 8 | f64 | `maxLon` |
| 32 | 8 | f64 | `maxLat` |
| 40 | 4 | u32 | Feature count |
| 44 | var | string | Source description, for example `Natural Earth 5.1.2 ne_50m_admin_0_countries` |
| | 1 | u8 | Property count `P` |
| | var | | `P` property definitions: string name, then u8 type |

Property types: `1` string, `2` i32 (4 bytes), `3` u8 (1 byte).

Layer ids:

| Id | File | Kind | Content |
|---|---|---|---|
| 1 | `admin0_110m.bin` | polygon | Countries for the zoomed-out world view |
| 2 | `admin0_50m.bin` | polygon | Countries for zoomed-in views, regions and outlines |
| 3 | `tap_zones.bin` | point | Tap-zone centres for small countries |
| 4 | `admin1_50m.bin` | polygon | US states + DC and Canadian provinces/territories |
| 5 | `rivers_50m.bin` | line | Rivers, lake centrelines and canals |
| 6 | `lakes_50m.bin` | polygon | Lakes and reservoirs |
| 7 | `regions_50m.bin` | polygon | Physical regions (mountain ranges, deserts, plateaus, ...) |
| 8 | `geo_points_50m.bin` | point | Peaks, depressions, passes, capes, islands, waterfalls, poles |

## Features

`featureCount` records follow the header, each laid out as:

| Size | Type | Field |
|---|---|---|
| var | string | Feature id (unique within the file) |
| var | | One value per property, in header order, each in its declared type |
| 8 | 4 × i16 | Bounding box `minX, minY, maxX, maxY` (quantised) |
| 4 | 2 × i16 | Label point `x, y` (quantised) |
| var | varint | Part count `N` (always `0` for point layers) |
| var | | `N` parts |

Each part:

| Size | Type | Field |
|---|---|---|
| 1 | u8 | Part flags (below) |
| var | varint | Point count `M` |
| var | | `M` points, each `zigzag-varint dx` then `zigzag-varint dy` |

**Delta encoding.** Keep a cursor `(cx, cy)`. At the start of each
feature's geometry set it to the feature's `(minX, minY)` bbox corner. For
each point, `cx += dx; cy += dy`; the result is the point's quantised `(x, y)`.
The cursor carries on across the parts of the same feature (it is not reset
per part).

**Part flags:**

| Bit | Value | Meaning |
|---|---|---|
| 0 | `0x01` | Hole (polygon layers). Otherwise the part is an outer ring (or a line, in line layers). |
| 1 | `0x02` | Remote: the part is far from the feature's home territory (admin-0 only; see the README rule). Holes carry their outer ring's flags. |
| 2 | `0x04` | Wrap: the part's centre is more than 180° of longitude from the main part's centre, so it continues the feature across the antimeridian (for example Chukotka for Russia, the western Aleutians for the USA). To draw or frame the feature contiguously, add 360° to the part's longitudes if they are less than the main part's, otherwise subtract 360°. |
| 3–7 | | Reserved, written as 0; readers must ignore them. |

### Geometry rules

- **Polygons:** a polygon is an outer ring followed by zero or more holes
  that belong to it (each hole immediately follows its outer ring or the
  previous hole of the same polygon). The first part of every polygon
  feature is an outer ring. Rings are *not* closed: the last point is not a
  copy of the first; close them when drawing. Every ring has at least 3
  points and non-zero area. Outer rings are counter-clockwise and holes
  clockwise with x = longitude (east) and y = latitude (north) (the
  GeoJSON RFC 7946 convention). Even-odd or non-zero fill both work.
- **Lines:** each part is an open polyline with at least 2 points.
- **Points:** no parts. The point is the label point; the bbox is the point
  itself, except in `tap_zones.bin` (see below).
- **Antimeridian:** no segment spans more than 180° of longitude. Natural
  Earth already splits geometry at ±180°, so features such as Russia, Fiji
  and the USA have separate parts on each side that meet at x = −32768 /
  32767. Edges along the South Pole in Antarctica are split into steps of
  at most 90°.
- **Shared borders:** neighbouring polygons in the same layer use exactly
  the same quantised vertices along a shared border, so there are no gaps or
  overlaps between countries (or between states/provinces).
- **Draw order:** file order. In `admin0_110m.bin` the 29 countries that
  Natural Earth leaves out at 110m (small islands and microstates such as
  Andorra and San Marino) come last, use 50m geometry and are drawn on top of
  their neighbours.

## Quantisation

With `qx`, `qy` in `[-32768, 32767]`:

```
lon = minLon + (qx + 32768) * (maxLon - minLon) / 65535
lat = minLat + (qy + 32768) * (maxLat - minLat) / 65535
```

and the writer uses `qx = floor((lon - minLon) / (maxLon - minLon) * 65535 + 0.5) - 32768`
(same for latitude). Bounds are the extent of the layer's data. For a world
layer one unit is about 0.0055° of longitude (≈ 610 m at the equator) and
about 0.0027° of latitude (≈ 300 m). Quantisation uses one grid per layer
rather than one per feature so that shared borders stay identical; the
cost is that microstates such as Vatican City and Monaco are only a few grid
units across (use the tap zones for them).

## Properties per layer

Missing text is an empty string. `wikidata` is a Wikidata item id such as
`Q408` when Natural Earth supplies one. `scalerank` is Natural Earth's
importance rank (0 most important; use it to thin features when zoomed out).

**admin0_110m, admin0_50m** (feature id = app cca3 for the 197 playable
countries, Kosovo = `UNK`; Natural Earth `ADM0_A3` for other land)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth English name |
| `ne_a3` | string | Natural Earth `ADM0_A3` of the main source feature |
| `playable` | u8 | `1` for the app's 197 countries, `0` for other land (Antarctica, Greenland, Western Sahara, dependencies, ...) |
| `geom_level` | u8 | `110` or `50`: which Natural Earth scale the geometry came from |
| `area_km2` | i32 | Land area of this feature's geometry at this scale, km², rounded |
| `wikidata` | string | Wikidata id |

The label point is Natural Earth's `LABEL_X`/`LABEL_Y` (on the main
territory; use it for labels and as the fallback tap point).

**tap_zones** (id = cca3; one point per playable country with less than
20 000 km² of 50m land)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth English name |
| `area_km2` | i32 | 50m land area, km² |
| `point_source` | u8 | `1` Natural Earth tiny-countries point, `2` admin-0 label point |

The bbox is the extent of the country's non-remote 50m parts (so a reader can
size the tap circle for spread-out island nations); the label point is the
tap centre.

**admin1_50m** (id = ISO 3166-2, for example `US-CA`, `CA-ON`)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth name (for example `Québec`, `District of Columbia`) |
| `name_en` | string | Natural Earth English name |
| `postal` | string | Postal abbreviation (`CA`, `ON`) |
| `country` | string | `USA` or `CAN` |
| `type` | string | `State`, `Federal District`, `Province` or `Territory` |
| `wikidata` | string | Wikidata id |

**rivers_50m** (id = Natural Earth `ne_id`), **lakes_50m** (id = `ne_id`)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth name |
| `name_en` | string | Natural Earth English name |
| `kind` | string | Rivers: `River`, `Lake Centerline`, `Canal`. Lakes: `Lake`, `Reservoir`, `Alkaline Lake` |
| `scalerank` | u8 | Natural Earth scale rank |
| `wikidata` | string | Wikidata id |

**regions_50m** (id = `ne_id`)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth label name (often upper case, for example `ANDES`) |
| `name_en` | string | Natural Earth English name |
| `kind` | string | Natural Earth class, for example `Range/mtn`, `Desert`, `Plateau`, `Island group`, `Continent` |
| `region` | string | Continent name from Natural Earth |
| `scalerank` | u8 | Natural Earth scale rank |
| `wikidata` | string | Wikidata id |

**geo_points_50m** (id = `ne_id`)

| Name | Type | Meaning |
|---|---|---|
| `name` | string | Natural Earth name |
| `name_en` | string | Natural Earth English name |
| `kind` | string | `mountain`, `depression`, `pass`, `cape`, `island`, `waterfall`, `pole`, `plain` |
| `elevation_m` | i32 | Metres above sea level (negative for depressions); `-2147483648` (`Int.MIN_VALUE`) when unknown |
| `region` | string | Continent name from Natural Earth |
| `scalerank` | u8 | Natural Earth scale rank |
| `wikidata` | string | Wikidata id |

## Reading with `java.nio.ByteBuffer` (sketch)

```kotlin
val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
fun str(): String { val n = buf.short.toInt() and 0xFFFF; val b = ByteArray(n); buf.get(b); return String(b, Charsets.UTF_8) }
fun varint(): Int { var r = 0; var s = 0; while (true) { val b = buf.get().toInt() and 0xFF; r = r or ((b and 0x7F) shl s); if (b and 0x80 == 0) return r; s += 7 } }
fun zigzag(): Int { val z = varint(); return (z ushr 1) xor -(z and 1) }

check(buf.int == 0x4D475147)            // "GQGM" read as a little-endian int
val version = buf.short.toInt() and 0xFFFF
val layerId = buf.get().toInt() and 0xFF
val kind = buf.get().toInt() and 0xFF
val minLon = buf.double; val minLat = buf.double; val maxLon = buf.double; val maxLat = buf.double
val count = buf.int                      // u32; fits in Int for these files
val source = str()
val props = List(buf.get().toInt() and 0xFF) { str() to (buf.get().toInt() and 0xFF) }
repeat(count) {
    val id = str()
    val values = props.map { (_, t) -> when (t) { 1 -> str(); 2 -> buf.int; 3 -> buf.get().toInt() and 0xFF; else -> error("type $t") } }
    val minX = buf.short; val minY = buf.short; val maxX = buf.short; val maxY = buf.short
    val labelX = buf.short; val labelY = buf.short
    var cx = minX.toInt(); var cy = minY.toInt()
    repeat(varint()) {
        val flags = buf.get().toInt() and 0xFF
        val n = varint()
        repeat(n) { cx += zigzag(); cy += zigzag() /* point (cx, cy) */ }
    }
}
check(!buf.hasRemaining())
```

The Python reader `geoformat.decode()` is the reference implementation; the
pipeline decodes and checks every file it writes with it.
