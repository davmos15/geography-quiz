# Geodata pipeline

Turns pinned Natural Earth downloads (public domain) into the compact binary
map layers the app bundles in `app/src/main/assets/geo/`. Decision D3 in
`docs/UPGRADE_PLAN.md` sets the format (int16-quantised, delta-encoded, one
file per layer) and the 1.5 MB budget for all layers together. The byte-level
format is in [`data/geo/FORMAT.md`](../../data/geo/FORMAT.md). Each build
writes a log to [`data/geo/build_log.txt`](../../data/geo/build_log.txt):
code mapping, merges, remote parts, tap zones, simplification statistics and
sizes.

Made with Natural Earth. Free vector and raster map data @ naturalearthdata.com.

## Running

From the repository root (Python 3.10 or later):

```
python tools/geodata/build_geodata.py            # build everything, print the size report
python tools/geodata/build_geodata.py --check    # rebuild in memory; exit 1 if any committed file differs
python tools/geodata/build_geodata.py --offline  # never download; fail if the cache is cold
python tools/geodata/build_geodata.py --verbose  # also print the build log
python tools/geodata/test_geodata.py             # contract tests + two-build reproducibility test
```

The first run creates `tools/geodata/.venv` and installs the build
dependency from `requirements.txt` (pyshp 3.1.6, pinned with
`--require-hashes`), then re-runs itself inside the venv. Natural Earth zips
are downloaded once into `tools/geodata/.cache/` and their SHA-256 is checked
on every run. Both folders are git-ignored. A full build takes about a minute.

The app never downloads anything: the `.bin` files are committed and bundled.

**`--check` and CI.** `--check` rebuilds from the Natural Earth zips, so it
needs the cache or network access (about 4.7 MB from naciscdn.org) plus the
pyshp install. `test_geodata.py`'s contract tests need neither: they decode
the committed files with the standard library only. CI does not run either
yet.

**Reproducibility.** Output depends only on the pinned inputs: no
timestamps, sorted outputs, no reliance on set or dict order of strings
(the test builds twice with different `PYTHONHASHSEED` values and compares
bytes). Distances and areas use `math` trigonometry; they only feed
threshold decisions with wide margins and rounded log values, so another
platform's maths library is not expected to change the output.

## Sources

Natural Earth release **5.1.2** from `https://naciscdn.org/naturalearth/5.1.2/`
(the versioned path, not the "latest" one). The zips' own `VERSION.txt` says
5.1.1 for the cultural layers and 5.0.0 for the physical layers, which is
what the 5.1.2 release contains; the script checks both the SHA-256 and the
`VERSION.txt`. Checksums are in `build_geodata.py` (`SOURCES`) and in the
build log.

| Zip | Used for |
|---|---|
| `110m/cultural/ne_110m_admin_0_countries.zip` | `admin0_110m.bin` |
| `50m/cultural/ne_50m_admin_0_countries.zip` | `admin0_50m.bin`, 110m fill, `tap_zones.bin` |
| `50m/cultural/ne_50m_admin_0_tiny_countries.zip` | `tap_zones.bin` points |
| `50m/cultural/ne_50m_admin_1_states_provinces.zip` | `admin1_50m.bin` |
| `50m/physical/ne_50m_rivers_lake_centerlines.zip` | `rivers_50m.bin` |
| `50m/physical/ne_50m_lakes.zip` | `lakes_50m.bin` |
| `50m/physical/ne_50m_geography_regions_polys.zip` | `regions_50m.bin` |
| `50m/physical/ne_50m_geography_regions_points.zip` | `geo_points_50m.bin` |
| `50m/physical/ne_50m_geography_regions_elevation_points.zip` | `geo_points_50m.bin` |

The app's 197 countries come from `derive_countries()` in
`tools/data/export_aliases.py` (UN members plus `extraCountries` in
`data/source/alias_overrides.json`), the same list as `static.db`. Only the
cca3 codes are used here; no mledoze/countries data is copied into the map
files. Wikidata ids in the files are Natural Earth's own `WIKIDATAID`
fields.

## Layers

| File | Content | Feature id |
|---|---|---|
| `admin0_110m.bin` | Countries for the zoomed-out world view: Natural Earth 110m, plus 50m geometry for the 29 app countries 110m leaves out (drawn last) | cca3 / NE `ADM0_A3` |
| `admin0_50m.bin` | Countries for zoomed-in views, region presets and outlines | cca3 / NE `ADM0_A3` |
| `tap_zones.bin` | One point per small playable country (see below) | cca3 |
| `admin1_50m.bin` | 50 US states + DC, 10 Canadian provinces + 3 territories (the build fails on any other count) | ISO 3166-2 (`US-CA`, `CA-ON`) |
| `rivers_50m.bin` | Rivers, lake centrelines and canals | NE `ne_id` |
| `lakes_50m.bin` | Lakes, reservoirs, alkaline lakes | NE `ne_id` |
| `regions_50m.bin` | Physical regions: ranges, deserts, plateaus, plains, islands, continents, ... | NE `ne_id` |
| `geo_points_50m.bin` | Mountains (with elevation), depressions, a pass, capes, islands, waterfalls, poles | NE `ne_id` |

Both admin-0 levels contain every one of the 197 countries (the build
fails otherwise) and also all other Natural Earth land (Antarctica,
Greenland, Western Sahara, dependencies), marked `playable = 0` with their NE
`ADM0_A3` code, so the map has no missing land. Each country carries Natural
Earth's label point (`LABEL_X`/`LABEL_Y`) and its land area.

Where Natural Earth splits one feature into several records with the same
`ne_id` (10 rivers, 6 lakes, 5 regions), the records are merged into one
feature with several parts; the record with the lowest scale rank provides
the properties. The log lists them.

## Country code mapping

Rule, in order (`map_admin0()`):

1. `MERGE_INTO`: Somaliland (`SOL`) is merged into Somalia (`SOM`) and
   Northern Cyprus (`CYN`) into Cyprus (`CYP`). The merge dissolves their
   shared border, so the result is one outline.
2. `CODE_OVERRIDES`: Kosovo (`KOS`) becomes `UNK`, the app's code.
3. Natural Earth `ISO_A3` if it is one of the 197. This also covers
   `PSX` → `PSE` (Palestine) and `SDS` → `SSD` (South Sudan), whose
   `ADM0_A3` differs from the ISO code.
4. `ISO_A3_EH` if it is one of the 197 and equals `ADM0_A3`: France and
   Norway, whose `ISO_A3` is `-99` in Natural Earth.
5. Anything else is non-playable land with id = `ADM0_A3`. This includes the
   Australian Indian Ocean Territories (`IOA`) and Ashmore and Cartier
   Islands (`ATC`), whose `ISO_A3_EH` is `AUS`: they are kept separate,
   like other dependencies.

Two Natural Earth features mapping to the same country without a merge rule,
or a non-playable `ADM0_A3` that collides with an app code, stop the build.

**Point of view.** The layers use Natural Earth's default (de facto) point of
view: for example Crimea is part of Russia, the Golan Heights part of
Israel, Western Sahara is separate non-playable land, and Kashmir is divided
along the lines of control (the Siachen Glacier, `KAS`, is separate
non-playable land). Natural Earth 5.1.2 does not publish point-of-view
variants of these files. See the open questions in the 4.1 report.

## Simplification and topology

Polygon layers use shared-arc topology (`geom.Topology`, in the style of
TopoJSON): every ring in the layer is cut at junctions (points whose
neighbouring vertices differ between the rings using them), each distinct
arc is simplified once with Douglas–Peucker, and rings are rebuilt from the
simplified arcs. Natural Earth's neighbouring polygons share exact vertices,
so a border between two countries (or states, or a country and an enclave
hole such as Lesotho in South Africa) is written identically in both
features. There are no gaps or slivers by construction, and quantisation
keeps this because one grid is used per layer.

| Layer | Tolerance (degrees) |
|---|---|
| admin0_110m | 0.01 (Natural Earth 110m is already generalised) |
| admin0_50m | 0.01 (about two grid units) |
| admin1_50m | 0.01 |
| rivers_50m | 0.01 (per line, end points kept, so tributaries stay joined) |
| lakes_50m | 0.01 |
| regions_50m | 0.05 (label areas; larger tolerances save little and add self-crossings) |

Measured after simplification and quantisation (strict X-shaped crossings
between segments, in the log):

- admin0_50m, admin1_50m, lakes_50m: 0 crossings within or between features.
- admin0_110m: 0 within features; 12 between features, all where 50m-sourced
  Andorra and Liechtenstein overlap the coarser 110m outlines of France,
  Spain, Austria and Switzerland. This is expected: they are drawn on top.
- regions_50m: Natural Earth's regions overlap each other by design (a range
  inside a plateau inside a continent), so crossings between features are
  normal; 39 self-crossings within features remain from simplification.

**Small rings.** Rings never collapse during simplification: a closed ring
keeps at least a triangle, and if a ring would end up with fewer than three
distinct points its arcs are kept unsimplified. A ring can still vanish if
it is smaller than one grid unit after quantisation; that happened once in
110m (a 3-point islet of North Korea) and 14 times in the regions layer, and
the log lists each one. If a feature would lose all its rings it keeps a
one-unit triangle (not needed by the current data; tap zones cover the
microstates).

**Antimeridian.** Natural Earth 5.1.2 already splits geometry at ±180°;
the build checks that no segment spans more than 180° of longitude. Edges
along the South Pole (Antarctica) are split into steps of at most 90° after
quantisation. Parts that continue a feature across the line get the `wrap`
flag: in admin0_50m Russia (Chukotka), the USA (western Aleutians), Fiji,
Kiribati and New Zealand (Chatham Islands).

## Remote parts

Admin-0 parts (outer rings) far from a country's home territory get the
`remote` flag so region framing (task 4.4) can ignore them:

1. The main part is the largest by area.
2. Home grows from the main part: any part within `LINK_KM` = 600 km of a
   home part joins it (repeated, so island chains link up).
3. A part not yet linked also joins if it is within `FAR_KM` = 2500 km of
   home **and** its centre is in the same continent box as the main part's
   centre. Repeat steps 2–3 until nothing changes.
4. Everything left is remote.

Continent boxes (`CONTINENT_BOXES`) are coarse latitude/longitude
rectangles; anything outside them is "open ocean". They only classify parts
more than 600 km from the rest of their country. The rule gives:

- Remote at 50m: Hawaii (USA); Canary Islands (ESP); Madeira and the Azores
  (PRT); French Guiana, Guadeloupe, Martinique, Mayotte and Réunion (FRA);
  Bonaire, Saba and Sint Eustatius (NLD); Tokelau (NZL); Easter Island
  (CHL); the Prince Edward Islands (ZAF); Cocos (Keeling) Islands (part of
  non-playable `IOA`).
- Not remote: Alaska and the Aleutians, Svalbard and Jan Mayen, Galápagos,
  Corsica, Balearics, Kaliningrad, Chatham Islands, Andaman and Nicobar,
  Socotra, Kiribati's island groups.
- At 110m only French Guiana and Hawaii are remote (the other islands are
  not in 110m).

The full list with distances is in the build log.

## Tap zones

`tap_zones.bin` has one point per playable country with less than
**20 000 km²** of land in the 50m data (`TAP_AREA_KM2`), 46 countries. That
covers all microstates, small island nations and the 29 countries missing
from 110m (checked by the build). The point is Natural Earth's tiny-countries
point where it has one for that country, otherwise its admin-0 label point.
Each feature's bbox is the extent of the country's 50m parts that are
neither remote nor wrap, so spread-out archipelagos can get a larger circle.
The admin-0 layers also carry every country's label point and `area_km2`, so
task 4.3 can use a different threshold, or one that depends on zoom, without
rebuilding.

## Budgets

Sizes in bytes (1 KB = 1000 bytes); the build fails if a layer or the total
goes over. Current sizes are in the log.

| File | Budget | Current |
|---|---|---|
| `admin0_110m.bin` | 120 000 | 45 535 |
| `admin0_50m.bin` | 650 000 | 170 348 |
| `tap_zones.bin` | 15 000 | 1 770 |
| `admin1_50m.bin` | 200 000 | 49 060 |
| `rivers_50m.bin` | 150 000 | 81 509 |
| `lakes_50m.bin` | 100 000 | 51 102 |
| `regions_50m.bin` | 220 000 | 185 289 |
| `geo_points_50m.bin` | 40 000 | 21 165 |
| **Total** | **1 500 000** (D3) | **605 778** |

In `regions_50m.bin` the seven `Continent` polygons take about 48 KB and
`Island`/`Island group` about 36 KB (breakdown in the log). Dropping them
is the easiest saving if a later phase doesn't need them.

## Files

| File | Purpose |
|---|---|
| `build_geodata.py` | The pipeline: download, map codes, build layers, validate, write, `--check` |
| `geom.py` | Geometry helpers (standard library): topology, simplification, dissolve, holes, distances |
| `geoformat.py` | Writer and reference reader for the GQGM format |
| `test_geodata.py` | Contract tests on the committed files and the reproducibility test |
| `requirements.txt` | Pinned, hash-checked build dependency (pyshp) |
