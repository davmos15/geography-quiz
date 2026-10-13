#!/usr/bin/env python3
"""Build GeoQuiz's bundled map layers from pinned Natural Earth downloads.

One command, from the repository root or anywhere:

    python tools/geodata/build_geodata.py            # build app/src/main/assets/geo/*.bin
    python tools/geodata/build_geodata.py --check    # rebuild in memory, exit 1 if stale
    python tools/geodata/build_geodata.py --offline  # never download (cache must be warm)

The first run creates tools/geodata/.venv and installs the pinned, hash-checked
build dependencies from requirements.txt (pyshp only), then re-runs itself
inside it. Natural Earth zips are downloaded once into tools/geodata/.cache/
and verified against the SHA-256 values below; nothing is fetched at app
runtime.

Outputs (one file per layer, format in data/geo/FORMAT.md):

    app/src/main/assets/geo/admin0_110m.bin   countries, world view
    app/src/main/assets/geo/admin0_50m.bin    countries, zoomed in / outlines
    app/src/main/assets/geo/tap_zones.bin     tap points for small countries
    app/src/main/assets/geo/admin1_50m.bin    US states + DC, Canadian provinces/territories
    app/src/main/assets/geo/rivers_50m.bin    rivers and lake centrelines
    app/src/main/assets/geo/lakes_50m.bin     lakes
    app/src/main/assets/geo/regions_50m.bin   physical regions (ranges, deserts, ...)
    app/src/main/assets/geo/geo_points_50m.bin  peaks, depressions, capes, ...
    data/geo/build_log.txt                    code mapping, remote parts, sizes

Every run decodes what it wrote and validates it (all 197 countries in both
admin-0 levels, admin-1 counts, ring rules, antimeridian, per-layer and total
size budgets). See tools/geodata/README.md for the rules.
"""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import subprocess
import sys
import urllib.request
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
VENV = HERE / ".venv"
CACHE = HERE / ".cache"
REQUIREMENTS = HERE / "requirements.txt"
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "geo"
LOG_PATH = REPO_ROOT / "data" / "geo" / "build_log.txt"

PYSHP_VERSION = "3.1.6"


# --------------------------------------------------------------------------
# Dependency bootstrap (stdlib only above this point)
# --------------------------------------------------------------------------

def _venv_python() -> Path:
    return VENV / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def _ensure_deps() -> None:
    try:
        import shapefile  # noqa: F401
    except ImportError:
        pass
    else:
        if shapefile.__version__ != PYSHP_VERSION:
            sys.exit(
                f"pyshp {shapefile.__version__} found, {PYSHP_VERSION} required. "
                f"Run: {_venv_python()} -m pip install --require-hashes -r {REQUIREMENTS}"
            )
        return
    py = _venv_python()
    if py.exists() and Path(sys.executable).resolve() == py.resolve():
        sys.exit(f"pyshp missing in {VENV}. Run: {py} -m pip install --require-hashes -r {REQUIREMENTS}")
    if not py.exists():
        print(f"Creating {VENV.relative_to(REPO_ROOT).as_posix()} ...")
        subprocess.check_call([sys.executable, "-m", "venv", str(VENV)])
    if subprocess.call([str(py), "-c", "import shapefile"], stderr=subprocess.DEVNULL) != 0:
        print("Installing pinned build dependencies ...")
        subprocess.check_call(
            [str(py), "-m", "pip", "install", "--quiet", "--require-hashes", "-r", str(REQUIREMENTS)]
        )
    sys.exit(subprocess.call([str(py), str(Path(__file__).resolve())] + sys.argv[1:]))


if __name__ == "__main__":
    _ensure_deps()

import shapefile  # noqa: E402

sys.dont_write_bytecode = True   # keep tools/ free of __pycache__ folders
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(REPO_ROOT / "tools" / "data"))

import geom  # noqa: E402
import geoformat as gf  # noqa: E402
from export_aliases import derive_countries  # noqa: E402  (the app's 197 countries)


# --------------------------------------------------------------------------
# Pinned sources (Natural Earth, public domain)
# --------------------------------------------------------------------------

NE_RELEASE = "5.1.2"
NE_BASE = f"https://naciscdn.org/naturalearth/{NE_RELEASE}"

# name -> (path under NE_BASE, SHA-256 of the zip, version in the zip's VERSION.txt)
SOURCES = {
    "ne_110m_admin_0_countries": (
        "110m/cultural/ne_110m_admin_0_countries.zip",
        "0f243aeac8ac6cf26f0417285b0bd33ac47f1b5bdb719fd3e0df37d03ea37110", "5.1.1"),
    "ne_50m_admin_0_countries": (
        "50m/cultural/ne_50m_admin_0_countries.zip",
        "5fed433373581fa648920435f937d95f2d3c0200e067409c6478dcdf1b853139", "5.1.1"),
    "ne_50m_admin_0_tiny_countries": (
        "50m/cultural/ne_50m_admin_0_tiny_countries.zip",
        "e6b63e61220a65a78dfddd67cdec3aadd1e335e904de12c98442e693e62ce9d2", "5.1.1"),
    "ne_50m_admin_1_states_provinces": (
        "50m/cultural/ne_50m_admin_1_states_provinces.zip",
        "61f79e6705e62a55d6bcf698394295a589af5e24a4b2684c6519dd35c1300bf6", "5.1.1"),
    "ne_50m_rivers_lake_centerlines": (
        "50m/physical/ne_50m_rivers_lake_centerlines.zip",
        "c607d9d7e7702827a7996fff6dc17b87a338c5ed3b52d12c402e0c9669cc7b56", "5.0.0"),
    "ne_50m_lakes": (
        "50m/physical/ne_50m_lakes.zip",
        "f28d42c286d96b57a17aac2cbeb432f8c65532c20063495711fbc64e24666df3", "5.0.0"),
    "ne_50m_geography_regions_polys": (
        "50m/physical/ne_50m_geography_regions_polys.zip",
        "a6e7ac257f6f0847ed80e6dc6e776441456652c093cfe90c7bf26dc8069f0d03", "5.0.0"),
    "ne_50m_geography_regions_points": (
        "50m/physical/ne_50m_geography_regions_points.zip",
        "c4498acc334dc84209bdac0f3262a1762a7983ed66d50073cab17bd3435cdfb9", "5.0.0"),
    "ne_50m_geography_regions_elevation_points": (
        "50m/physical/ne_50m_geography_regions_elevation_points.zip",
        "b23b3f62c1a003ea733e2cc161148d7d4f8726462f0b45592b7ca3b52cafe6c7", "5.0.0"),
}


# --------------------------------------------------------------------------
# Rules (documented in tools/geodata/README.md)
# --------------------------------------------------------------------------

# Douglas-Peucker tolerance per layer, in degrees (applied once per shared arc).
TOLERANCE = {
    "admin0_110m": 0.01,
    "admin0_50m": 0.01,
    "admin1_50m": 0.01,
    "rivers_50m": 0.01,
    "lakes_50m": 0.01,
    "regions_50m": 0.05,
}

# Size budgets in bytes (1 KB = 1000 bytes). The build fails if a layer or the
# total goes over. Total cap from decision D3: 1.5 MB.
BUDGET = {
    "admin0_110m.bin": 120_000,
    "admin0_50m.bin": 650_000,
    "tap_zones.bin": 15_000,
    "admin1_50m.bin": 200_000,
    "rivers_50m.bin": 150_000,
    "lakes_50m.bin": 100_000,
    "regions_50m.bin": 220_000,
    "geo_points_50m.bin": 40_000,
}
TOTAL_BUDGET = 1_500_000

# Natural Earth ADM0_A3 codes merged into an app country (the app has no separate entry).
MERGE_INTO = {"SOL": "SOM", "CYN": "CYP"}
# Natural Earth ADM0_A3 codes whose app cca3 is not their ISO code.
CODE_OVERRIDES = {"KOS": "UNK"}

# Remote-part rule: see README "Remote parts".
LINK_KM = 600.0
FAR_KM = 2500.0
# Coarse continent boxes (lon_min, lat_min, lon_max, lat_max), first match wins.
# They only classify parts that are more than LINK_KM from the rest of their
# country, so they need not follow real continental boundaries closely.
CONTINENT_BOXES = [
    ("Antarctica", (-180.0, -90.0, 180.0, -60.0)),
    ("Asia", (34.5, 12.0, 63.0, 42.0)),            # Arabia, Levant, Iran
    ("Oceania", (130.0, -60.0, 180.0, 23.0)),      # Melanesia, Micronesia, NZ, east Australia
    ("Oceania", (112.0, -60.0, 130.0, -10.0)),     # west Australia
    ("Oceania", (-180.0, -60.0, -100.0, 0.0)),     # Polynesia south of the equator
    ("Oceania", (-180.0, 0.0, -120.0, 30.0)),      # Hawaii, Line Islands
    ("South America", (-93.0, -60.0, -25.0, 7.0)),
    ("South America", (-77.0, 7.0, -59.0, 12.6)),
    ("Europe", (-25.0, 62.5, -13.0, 67.0)),        # Iceland
    ("North America", (-75.0, 59.0, -10.0, 84.0)),  # Greenland
    ("Europe", (-25.0, 35.0, 45.0, 84.0)),
    ("Europe", (45.0, 50.0, 66.0, 84.0)),
    ("Africa", (-20.0, -36.0, 52.0, 37.5)),
    ("Africa", (52.0, -27.0, 65.0, -3.0)),         # Mascarenes, Seychelles
    ("Asia", (45.0, -11.0, 180.0, 84.0)),
    ("Asia", (-180.0, 50.0, -168.5, 84.0)),        # Chukotka east of 180
    ("North America", (-170.0, 7.0, -50.0, 84.0)),
]

# Tap zones: playable countries whose 50m land area is below this get a point.
TAP_AREA_KM2 = 20_000

ADMIN1_EXPECTED = {"US": 51, "CA": 13}


# --------------------------------------------------------------------------
# Download and read
# --------------------------------------------------------------------------

def fetch(name: str, offline: bool) -> bytes:
    path, sha, _ = SOURCES[name]
    url = f"{NE_BASE}/{path}"
    cached = CACHE / f"{NE_RELEASE}_{Path(path).name}"
    if cached.exists():
        data = cached.read_bytes()
        if hashlib.sha256(data).hexdigest() == sha:
            return data
        print(f"Cached {cached.name} has the wrong SHA-256, fetching again")
    if offline:
        sys.exit(f"{cached} missing or stale and --offline was given")
    print(f"Downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "GeoQuiz-geodata/1"})
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = resp.read()
    digest = hashlib.sha256(data).hexdigest()
    if digest != sha:
        sys.exit(f"SHA-256 mismatch for {url}\n  expected {sha}\n  got      {digest}")
    CACHE.mkdir(parents=True, exist_ok=True)
    cached.write_bytes(data)
    return data


def read_layer(name: str, offline: bool) -> list[tuple[dict, object]]:
    """Return [(record dict, pyshp shape)] for a Natural Earth zip."""
    data = fetch(name, offline)
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = z.namelist()

        def member(ext: str) -> bytes:
            hits = [n for n in names if n.lower().endswith(f"{name}.{ext}".lower())]
            if len(hits) != 1:
                raise SystemExit(f"{name}: expected one .{ext}, found {hits}")
            return z.read(hits[0])

        version = member("VERSION.txt").decode("ascii").strip()
        if version != SOURCES[name][2]:
            raise SystemExit(f"{name}: VERSION.txt says {version}, expected {SOURCES[name][2]}")
        r = shapefile.Reader(
            shp=io.BytesIO(member("shp")), shx=io.BytesIO(member("shx")),
            dbf=io.BytesIO(member("dbf")), encoding="utf-8",
        )
        fields = [f[0] for f in r.fields[1:]]
        out = []
        for sr in r.iterShapeRecords():
            out.append((dict(zip(fields, list(sr.record))), sr.shape))
    return out


def shape_rings(shape) -> list[geom.Ring]:
    pts = shape.points
    parts = list(shape.parts) + [len(pts)]
    rings = []
    for a, b in zip(parts, parts[1:]):
        r = geom.open_ring(pts[a:b])
        if len(r) >= 3:
            rings.append(r)
    return rings


def shape_lines(shape) -> list[list[geom.Pt]]:
    pts = shape.points
    parts = list(shape.parts) + [len(pts)]
    lines = []
    for a, b in zip(parts, parts[1:]):
        line = []
        for p in pts[a:b]:
            p = (float(p[0]), float(p[1]))
            if not line or line[-1] != p:
                line.append(p)
        if len(line) >= 2:
            lines.append(line)
    return lines


def s(v) -> str:
    """Natural Earth text field as a clean string ('' for missing)."""
    if v is None:
        return ""
    v = str(v).strip()
    return "" if v in ("-99", "-1") else v


def wikidata(rec: dict) -> str:
    v = s(rec.get("WIKIDATAID", rec.get("wikidataid", "")))
    return v if v.startswith("Q") else ""


# --------------------------------------------------------------------------
# Remote parts
# --------------------------------------------------------------------------

def continent_of(lon: float, lat: float) -> str:
    for name, (x0, y0, x1, y1) in CONTINENT_BOXES:
        if x0 <= lon <= x1 and y0 <= lat <= y1:
            return name
    return "open ocean"


def classify_parts(outers: list[geom.Ring], with_remote: bool):
    """Return (main index, flags per outer, details per remote outer).

    main = the largest outer ring by area. A part is "home" if it is within
    LINK_KM of a home part (chained), or within FAR_KM of a home part and in
    the same continent box as the main part. Everything else is remote.
    Wrap = the part's centre is more than 180 degrees of longitude from the
    main part's centre (it continues the country across the antimeridian).
    """
    areas = [geom.ring_area_km2(r) for r in outers]
    main = max(range(len(outers)), key=lambda i: (areas[i], -i))
    centres = [geom.vertex_mean(r) for r in outers]
    flags = [0] * len(outers)
    mlon = centres[main][0]
    for i, c in enumerate(centres):
        if abs(c[0] - mlon) > 180.0:
            flags[i] |= gf.FLAG_WRAP
    details = {}
    if not with_remote or len(outers) == 1:
        return main, flags, details
    sph = [geom.SphereRing(r) for r in outers]
    main_cont = continent_of(*centres[main])
    link = geom.km_to_chord(LINK_KM)
    far = geom.km_to_chord(FAR_KM)
    home = [main]
    pending = [i for i in range(len(outers)) if i != main]

    def dist_to_home(i: int, stop: float) -> float:
        best = float("inf")
        cands = sorted(home, key=lambda h: sph[i].box_gap(sph[h]))
        for h in cands:
            if sph[i].box_gap(sph[h]) >= best:
                break
            d = sph[i].min_chord(sph[h], stop)
            if d < best:
                best = d
            if best <= stop:
                break
        return best

    while True:
        grew = True
        while grew:
            grew = False
            rest = []
            for i in pending:
                if dist_to_home(i, link) <= link:
                    home.append(i)
                    grew = True
                else:
                    rest.append(i)
            pending = rest
        joined = []
        rest = []
        for i in pending:
            if continent_of(*centres[i]) == main_cont and dist_to_home(i, 0.0) <= far:
                joined.append(i)
            else:
                rest.append(i)
        pending = rest
        if not joined:
            break
        home.extend(joined)
    for i in pending:
        flags[i] |= gf.FLAG_REMOTE
        d = dist_to_home(i, 0.0)
        details[i] = (centres[i], continent_of(*centres[i]), geom.chord_to_km(d), areas[i])
    return main, flags, details


# --------------------------------------------------------------------------
# Polygon layer assembly
# --------------------------------------------------------------------------

class PolyFeature:
    """A polygon feature before quantisation."""

    def __init__(self, fid: str, props: list, polys: list, label=None, remote=False):
        self.id = fid
        self.props = props
        self.polys = polys          # [(outer, [holes])] with shapefile winding
        self.label = label          # (lon, lat) or None for an interior point
        self.remote = remote        # apply the remote-part rule
        self.extra: dict = {}


def layer_bounds(points) -> tuple[float, float, float, float]:
    x0, y0, x1, y1 = geom.bbox(points)
    return (x0, y0, x1, y1)


def build_polygon_layer(name: str, layer_id: int, source: str, schema, feats: list[PolyFeature],
                        tol: float, log: list[str], bounds=None):
    """Simplify with shared arcs, quantise and assemble a polygon layer."""
    rings: list[geom.Ring] = []
    index = []  # per feature: [(outer ring id, [hole ring ids])]
    for f in feats:
        fi = []
        for outer, holes in f.polys:
            oid = len(rings)
            rings.append(outer)
            hids = []
            for h in holes:
                hids.append(len(rings))
                rings.append(h)
            fi.append((oid, hids))
        index.append(fi)
    if bounds is None:
        bounds = layer_bounds(p for r in rings for p in r)
    quant = gf.Quantiser(bounds)
    topo = geom.Topology(rings)
    protected = topo.simplify(tol)
    src_pts, simp_pts = topo.point_counts()
    topo.quantise(quant.q)
    shared_arcs = sum(1 for u in topo.arc_uses if u > 1)

    def is_polar(y: int) -> bool:
        return abs(quant.lat(y)) >= 89.9

    def finish(ring: list) -> list:
        return geom.densify_long_edges(geom.clean_int_ring(ring), is_polar, 16384)

    out_feats = []
    collapsed = []
    fallback = []
    remote_log = []
    crossing_input = []
    total_points = 0
    for fno, (f, fi) in enumerate(zip(feats, index)):
        outers_src = [rings[oid] for oid, _ in fi]
        main, flags, details = classify_parts(outers_src, f.remote)
        parts = []
        main_q = None
        for k, (oid, hids) in enumerate(fi):
            ring = finish(topo.ring_points(oid))
            if len(ring) < 3 or geom.signed_area(ring) == 0:
                collapsed.append((f.id, k, len(rings[oid])))
                continue
            if geom.signed_area(ring) < 0:
                ring = ring[::-1]
            parts.append(gf.Part(flags[k], ring))
            if k == main:
                main_q = (ring, [])
            for hid in hids:
                hole = finish(topo.ring_points(hid))
                if len(hole) < 3 or geom.signed_area(hole) == 0:
                    collapsed.append((f.id, f"hole of {k}", len(rings[hid])))
                    continue
                if geom.signed_area(hole) > 0:
                    hole = hole[::-1]
                parts.append(gf.Part(flags[k] | gf.FLAG_HOLE, hole))
                if k == main:
                    main_q[1].append(hole)
        if not parts:
            # Keep the feature: a one-unit triangle at its main ring's position.
            x, y = quant.q(geom.vertex_mean(outers_src[main]))
            x = min(x, gf.Q_MAX - 1)
            y = min(y, gf.Q_MAX - 1)
            tri = [(x, y), (x + 1, y), (x, y + 1)]
            parts.append(gf.Part(flags[main], tri))
            main_q = (tri, [])
            fallback.append(f.id)
        for k, (centre, cont, km, area) in sorted(details.items()):
            remote_log.append(
                f"  {f.id:<8} part {k:>3}: centre {centre[0]:8.2f} {centre[1]:7.2f}  "
                f"{cont:<14} {round(km, -1):>7.0f} km from home  {round(area):>9.0f} km2"
            )
        pts = [p for part in parts for p in part.points]
        fb = geom.bbox(pts)
        if f.label is not None:
            label = quant.q(f.label)
        else:
            if main_q is None:
                main_q = (parts[0].points, [])
            lx, ly = geom.interior_point(main_q[0], main_q[1])
            label = (int(round(lx)), int(round(ly)))
        total_points += len(pts)
        for part in parts:
            crossing_input.append((fno, part.points))
        out_feats.append(gf.Feature(f.id, f.props, tuple(fb), tuple(label), parts))
    same, other, pairs = geom.count_crossings(crossing_input)
    layer = gf.Layer(layer_id, gf.KIND_POLYGON, tuple(bounds), source, schema, out_feats)
    stats = {
        "tolerance": tol,
        "source_points": sum(len(r) for r in rings),
        "arc_points_before": src_pts,
        "arc_points_after": simp_pts,
        "points_written": total_points,
        "arcs": len(topo.arcs),
        "shared_arcs": shared_arcs,
        "junctions": topo.junction_count,
        "protected_arcs": protected,
        "collapsed": collapsed,
        "fallback": fallback,
        "crossings_same": same,
        "crossings_other": other,
    }
    log.append(f"[{name}] polygon layer")
    log.append(
        f"  tolerance {tol} deg; {len(feats)} features, {len(rings)} rings, "
        f"{stats['source_points']} source points -> {total_points} points written"
    )
    log.append(
        f"  shared-arc topology: {len(topo.arcs)} arcs ({shared_arcs} shared by two rings), "
        f"{topo.junction_count} junctions, {protected} arcs kept unsimplified to stop rings collapsing"
    )
    log.append(
        f"  segment crossings after simplification and quantisation: {same} within a feature, "
        f"{other} between features (shared borders are identical, so no gaps or slivers)"
    )
    if pairs and len(pairs) <= 40:
        log.append("  features involved: " + ", ".join(
            out_feats[a].id if a == b else f"{out_feats[a].id}/{out_feats[b].id}" for a, b in pairs))
    elif pairs:
        log.append(f"  {len(pairs)} feature pairs involved (overlapping source polygons)")
    wraps = [f"{f.id} {sum(1 for p in f.parts if p.flags & gf.FLAG_WRAP)}"
             for f in out_feats if any(p.flags & gf.FLAG_WRAP for p in f.parts)]
    if wraps:
        log.append("  parts flagged wrap (continue the feature across the antimeridian): " + ", ".join(wraps))
    if collapsed:
        log.append(f"  rings dropped because they collapsed below one grid unit: {len(collapsed)}")
        for fid, k, n in collapsed:
            log.append(f"    {fid} ring {k} ({n} source points)")
    if fallback:
        log.append(f"  features kept as a one-unit triangle: {', '.join(fallback)}")
    if remote_log:
        log.append(f"  remote parts ({len(remote_log)}):")
        log.extend(remote_log)
    return layer, stats


# --------------------------------------------------------------------------
# Layers
# --------------------------------------------------------------------------

ADMIN0_SCHEMA = [
    ("name", gf.PROP_STRING),
    ("ne_a3", gf.PROP_STRING),
    ("playable", gf.PROP_UINT8),
    ("geom_level", gf.PROP_UINT8),
    ("area_km2", gf.PROP_INT32),
    ("wikidata", gf.PROP_STRING),
]


def map_admin0(rec: dict, app: dict) -> tuple[str | None, str]:
    """Map a Natural Earth admin-0 record to an app cca3. Returns (cca3 or None, rule)."""
    a3 = s(rec["ADM0_A3"])
    if a3 in MERGE_INTO:
        return MERGE_INTO[a3], f"merged into {MERGE_INTO[a3]}"
    if a3 in CODE_OVERRIDES:
        return CODE_OVERRIDES[a3], f"override {a3} -> {CODE_OVERRIDES[a3]}"
    iso = s(rec["ISO_A3"])
    if iso in app:
        return iso, "ISO_A3" if iso == a3 else f"ISO_A3 {iso} (ADM0_A3 {a3})"
    eh = s(rec["ISO_A3_EH"])
    if eh in app and eh == a3:
        return eh, f"ISO_A3 is -99; ISO_A3_EH {eh} = ADM0_A3"
    return None, ""


def admin0_features(rows, app: dict, level: int, log: list[str]) -> dict[str, dict]:
    """Group NE admin-0 records by output id; returns id -> info."""
    groups: dict[str, dict] = {}
    log.append(f"[admin0 {level}m] code mapping ({len(rows)} Natural Earth features)")
    exceptions = []
    unmapped = []
    for rec, shape in rows:
        a3 = s(rec["ADM0_A3"])
        cca3, rule = map_admin0(rec, app)
        name = s(rec.get("NAME_EN")) or s(rec.get("NAME"))
        if cca3 is None:
            fid = a3
            if fid in app:
                raise SystemExit(f"non-playable NE feature {a3} collides with an app cca3")
            hint = ""
            eh = s(rec["ISO_A3_EH"])
            if eh in app:
                hint = f" (ISO_A3_EH says {eh}; kept separate)"
            unmapped.append(f"  {a3:<4} {name} [{s(rec['TYPE'])}]{hint}")
        else:
            fid = cca3
            if rule != "ISO_A3":
                exceptions.append(f"  {a3:<4} {name} -> {cca3}: {rule}")
        g = groups.get(fid)
        primary = a3 not in MERGE_INTO
        if g is None:
            g = {"id": fid, "playable": cca3 is not None, "recs": [], "rings": [], "primary": None}
            groups[fid] = g
        elif cca3 is not None and not any(r["ADM0_A3"] in MERGE_INTO for r in [rec] + g["recs"]):
            raise SystemExit(f"two Natural Earth features map to {fid} without a merge rule")
        g["recs"].append(rec)
        g["rings"].append(shape_rings(shape))
        if primary:
            g["primary"] = rec
    log.append(f"  mapping exceptions ({len(exceptions)}):")
    log.extend(sorted(exceptions))
    log.append(f"  not one of the app's countries, kept as non-playable land ({len(unmapped)}):")
    log.extend(sorted(unmapped))
    return groups


def group_polys(g: dict, log: list[str]) -> list:
    if len(g["rings"]) == 1:
        rings = g["rings"][0]
    else:
        allrings = [r for rs in g["rings"] for r in rs]
        rings, cancelled = geom.dissolve(allrings)
        names = " + ".join(s(r["ADM0_A3"]) for r in g["recs"])
        log.append(f"  dissolved {names} -> {g['id']}: {cancelled} shared border segments removed, "
                   f"{len(allrings)} rings -> {len(rings)}")
        if cancelled == 0:
            raise SystemExit(f"merge for {g['id']} shares no border segments")
    polys, orphans = geom.assign_holes(rings)
    if orphans:
        log.append(f"  {g['id']}: {orphans} hole ring(s) with no outer, treated as outer")
    return polys


def admin0_props(g: dict, level: int, polys) -> list:
    rec = g["primary"]
    area = sum(geom.ring_area_km2(o) - sum(geom.ring_area_km2(h) for h in hs) for o, hs in polys)
    return [
        s(rec.get("NAME_EN")) or s(rec.get("NAME")),
        s(rec["ADM0_A3"]),
        1 if g["playable"] else 0,
        level,
        int(round(area)),
        wikidata(rec),
    ]


def build_admin0(data, app, log):
    rows50 = data["ne_50m_admin_0_countries"]
    rows110 = data["ne_110m_admin_0_countries"]
    g50 = admin0_features(rows50, app, 50, log)
    feats50 = []
    for fid in sorted(g50):
        g = g50[fid]
        polys = group_polys(g, log)
        rec = g["primary"]
        f = PolyFeature(fid, admin0_props(g, 50, polys), polys,
                        label=(float(rec["LABEL_X"]), float(rec["LABEL_Y"])), remote=True)
        feats50.append(f)
    missing50 = sorted(set(app) - set(g50))
    if missing50:
        raise SystemExit(f"countries missing from admin-0 50m: {missing50}")

    g110 = admin0_features(rows110, app, 110, log)
    feats110 = []
    for fid in sorted(g110):
        g = g110[fid]
        polys = group_polys(g, log)
        rec = g["primary"]
        feats110.append(PolyFeature(fid, admin0_props(g, 110, polys), polys,
                                    label=(float(rec["LABEL_X"]), float(rec["LABEL_Y"])), remote=True))
    fill = sorted(c for c in app if c not in g110)
    log.append(f"  countries absent from 110m, filled from 50m geometry and drawn last ({len(fill)}): "
               + ", ".join(fill))
    by50 = {f.id: f for f in feats50}
    for fid in fill:
        f = by50[fid]
        props = list(f.props)
        props[3] = 50
        feats110.append(PolyFeature(fid, props, f.polys, label=f.label, remote=True))
    log.append("")

    l110, st110 = build_polygon_layer("admin0_110m", gf.LAYER_ADMIN0_110M,
                                      f"Natural Earth {NE_RELEASE} ne_110m_admin_0_countries (+50m fill)",
                                      ADMIN0_SCHEMA, feats110, TOLERANCE["admin0_110m"], log)
    log.append("")
    l50, st50 = build_polygon_layer("admin0_50m", gf.LAYER_ADMIN0_50M,
                                    f"Natural Earth {NE_RELEASE} ne_50m_admin_0_countries",
                                    ADMIN0_SCHEMA, feats50, TOLERANCE["admin0_50m"], log)
    log.append("")
    return l110, l50, feats50, fill


def build_tap_zones(data, app, feats50, l50, fill110, log):
    tiny_rows = data["ne_50m_admin_0_tiny_countries"]
    tiny_pts = {}
    tiny_unmapped = []
    for rec, shape in tiny_rows:
        cca3, _ = map_admin0(rec, app)
        if cca3 is None:
            tiny_unmapped.append(s(rec["ADM0_A3"]))
            continue
        p = shape.points[0]
        tiny_pts[cca3] = (float(p[0]), float(p[1]))
    out50 = {f.id: f for f in l50.features}
    src50 = {f.id: f for f in feats50}
    quant = gf.Quantiser(l50.bounds)
    feats = []
    rows = []
    for fid in sorted(app):
        f = src50[fid]
        area = f.props[4]
        if area >= TAP_AREA_KM2:
            continue
        if fid in tiny_pts:
            where, srcno = tiny_pts[fid], 1
        else:
            where, srcno = f.label, 2
        home = [p for part in out50[fid].parts
                if not part.flags & (gf.FLAG_REMOTE | gf.FLAG_WRAP) for p in part.points]
        x0, y0, x1, y1 = geom.bbox(home)
        lo = quant.deg((x0, y0))
        hi = quant.deg((x1, y1))
        feats.append((fid, f.props[0], area, srcno, where, lo, hi))
        rows.append(f"  {fid}  {area:>6} km2  {'NE tiny-countries point' if srcno == 1 else 'NE label point'}"
                    f"  {f.props[0]}")
    missing = [c for c in fill110 if c not in {x[0] for x in feats}]
    if missing:
        raise SystemExit(f"countries absent from 110m but without a tap zone: {missing}")
    pts = [x[4] for x in feats] + [x[5] for x in feats] + [x[6] for x in feats]
    bounds = layer_bounds(pts)
    q = gf.Quantiser(bounds)
    schema = [("name", gf.PROP_STRING), ("area_km2", gf.PROP_INT32), ("point_source", gf.PROP_UINT8)]
    out = []
    for fid, name, area, srcno, where, lo, hi in feats:
        a = q.q(lo)
        b = q.q(hi)
        out.append(gf.Feature(fid, [name, area, srcno], (a[0], a[1], b[0], b[1]), q.q(where), []))
    layer = gf.Layer(gf.LAYER_TAP_ZONES, gf.KIND_POINT, bounds,
                     f"Natural Earth {NE_RELEASE} ne_50m_admin_0_tiny_countries + ne_50m_admin_0_countries",
                     schema, out)
    log.append(f"[tap_zones] playable countries under {TAP_AREA_KM2} km2 of 50m land ({len(out)}):")
    log.extend(rows)
    log.append("  NE tiny-country points not used (other territories, or parts of larger countries): "
               + ", ".join(sorted(tiny_unmapped)))
    log.append("")
    return layer


def build_admin1(data, log):
    rows = data["ne_50m_admin_1_states_provinces"]
    feats = []
    counts = {"US": 0, "CA": 0}
    for rec, shape in rows:
        a2 = s(rec["iso_a2"])
        if a2 not in counts:
            continue
        counts[a2] += 1
        code = s(rec["iso_3166_2"])
        if not code.startswith(a2 + "-"):
            raise SystemExit(f"admin-1 {rec['name']} has iso_3166_2 {code!r}")
        polys, _ = geom.assign_holes(shape_rings(shape))
        props = [s(rec["name"]), s(rec["name_en"]), s(rec["postal"]),
                 {"US": "USA", "CA": "CAN"}[a2], s(rec["type_en"]), wikidata(rec)]
        feats.append(PolyFeature(code, props, polys,
                                 label=(float(rec["longitude"]), float(rec["latitude"]))))
    if counts != ADMIN1_EXPECTED:
        raise SystemExit(f"admin-1 counts {counts}, expected {ADMIN1_EXPECTED}")
    feats.sort(key=lambda f: f.id)
    ids = [f.id for f in feats]
    if len(set(ids)) != len(ids):
        raise SystemExit("duplicate admin-1 codes")
    schema = [("name", gf.PROP_STRING), ("name_en", gf.PROP_STRING), ("postal", gf.PROP_STRING),
              ("country", gf.PROP_STRING), ("type", gf.PROP_STRING), ("wikidata", gf.PROP_STRING)]
    layer, _ = build_polygon_layer("admin1_50m", gf.LAYER_ADMIN1_50M,
                                   f"Natural Earth {NE_RELEASE} ne_50m_admin_1_states_provinces (US, CA)",
                                   schema, feats, TOLERANCE["admin1_50m"], log)
    log.append(f"  {counts['US']} US (50 states + DC), {counts['CA']} Canadian provinces and territories")
    log.append("")
    return layer


def ne_id(rec: dict) -> str:
    v = rec.get("ne_id", rec.get("NE_ID"))
    if v in (None, "", 0):
        raise SystemExit(f"record without ne_id: {rec.get('name')}")
    return str(int(v))


def scalerank(rec: dict) -> int:
    v = rec.get("scalerank", rec.get("SCALERANK", 0))
    try:
        v = int(v)
    except (TypeError, ValueError):
        v = 0
    return max(0, min(255, v))


def group_by_ne_id(rows, label: str, log: list[str]):
    """Group records that share an ne_id (Natural Earth splits some features).

    Returns [(id, primary record, [shapes])] sorted by numeric id. The primary
    record (whose properties are kept) is the one with the lowest scalerank,
    then the first in file order.
    """
    groups: dict[str, list] = {}
    for n, (rec, shape) in enumerate(rows):
        groups.setdefault(ne_id(rec), []).append((scalerank(rec), n, rec, shape))
    out = []
    merged = []
    for fid in sorted(groups, key=int):
        items = sorted(groups[fid], key=lambda x: (x[0], x[1]))
        out.append((fid, items[0][2], [x[3] for x in sorted(items, key=lambda x: x[1])]))
        if len(items) > 1:
            names = sorted({s(x[2].get("name", x[2].get("NAME"))) or "(no name)" for x in items})
            merged.append(f"    {fid}: {len(items)} records merged ({' / '.join(names)})")
    if merged:
        log.append(f"  {label}: Natural Earth records sharing an ne_id, merged into one feature ({len(merged)}):")
        log.extend(merged)
    return out


def build_rivers(data, log):
    head: list[str] = []
    lines = []
    for fid, rec, shapes in group_by_ne_id(data["ne_50m_rivers_lake_centerlines"], "rivers_50m", head):
        parts = [line for shp in shapes for line in shape_lines(shp)]
        if parts:
            lines.append((fid, rec, parts))
    bounds = layer_bounds(p for _, _, parts in lines for line in parts for p in line)
    q = gf.Quantiser(bounds)
    tol = TOLERANCE["rivers_50m"]
    feats = []
    src = 0
    written = 0
    for fid, rec, parts in lines:
        out_parts = []
        for line in parts:
            src += len(line)
            simp = geom.douglas_peucker(line, tol)
            ql = []
            for p in simp:
                qp = q.q(p)
                if not ql or ql[-1] != qp:
                    ql.append(qp)
            if len(ql) == 1:
                a, b = q.q(line[0]), q.q(line[-1])
                ql = [a, (a[0] + 1, a[1])] if a == b else [a, b]
            out_parts.append(gf.Part(0, ql))
            written += len(ql)
        longest = max(out_parts, key=lambda p: len(p.points))
        label = longest.points[len(longest.points) // 2]
        pts = [p for part in out_parts for p in part.points]
        props = [s(rec["name"]), s(rec["name_en"]), s(rec["featurecla"]), scalerank(rec), wikidata(rec)]
        feats.append(gf.Feature(fid, props, tuple(geom.bbox(pts)), label, out_parts))
    schema = [("name", gf.PROP_STRING), ("name_en", gf.PROP_STRING), ("kind", gf.PROP_STRING),
              ("scalerank", gf.PROP_UINT8), ("wikidata", gf.PROP_STRING)]
    layer = gf.Layer(gf.LAYER_RIVERS_50M, gf.KIND_LINE, bounds,
                     f"Natural Earth {NE_RELEASE} ne_50m_rivers_lake_centerlines", schema, feats)
    log.append("[rivers_50m] line layer")
    log.append(f"  tolerance {tol} deg; {len(feats)} features, {src} source points -> {written} points written")
    log.extend(head)
    log.append("")
    return layer


def build_lakes(data, log):
    head: list[str] = []
    feats = []
    for fid, rec, shapes in group_by_ne_id(data["ne_50m_lakes"], "lakes_50m", head):
        polys = []
        for shp in shapes:
            polys.extend(geom.assign_holes(shape_rings(shp))[0])
        if not polys:
            continue
        props = [s(rec["name"]), s(rec["name_en"]), s(rec["featurecla"]), scalerank(rec), wikidata(rec)]
        feats.append(PolyFeature(fid, props, polys))
    schema = [("name", gf.PROP_STRING), ("name_en", gf.PROP_STRING), ("kind", gf.PROP_STRING),
              ("scalerank", gf.PROP_UINT8), ("wikidata", gf.PROP_STRING)]
    layer, _ = build_polygon_layer("lakes_50m", gf.LAYER_LAKES_50M,
                                   f"Natural Earth {NE_RELEASE} ne_50m_lakes", schema, feats,
                                   TOLERANCE["lakes_50m"], log)
    log.extend(head)
    log.append("")
    return layer


def build_regions(data, log):
    head: list[str] = []
    feats = []
    for fid, rec, shapes in group_by_ne_id(data["ne_50m_geography_regions_polys"], "regions_50m", head):
        polys = []
        for shp in shapes:
            polys.extend(geom.assign_holes(shape_rings(shp))[0])
        if not polys:
            continue
        props = [s(rec["NAME"]), s(rec["NAME_EN"]), s(rec["FEATURECLA"]), s(rec["REGION"]),
                 scalerank(rec), wikidata(rec)]
        feats.append(PolyFeature(fid, props, polys))
    schema = [("name", gf.PROP_STRING), ("name_en", gf.PROP_STRING), ("kind", gf.PROP_STRING),
              ("region", gf.PROP_STRING), ("scalerank", gf.PROP_UINT8), ("wikidata", gf.PROP_STRING)]
    layer, _ = build_polygon_layer("regions_50m", gf.LAYER_REGIONS_50M,
                                   f"Natural Earth {NE_RELEASE} ne_50m_geography_regions_polys",
                                   schema, feats, TOLERANCE["regions_50m"], log)
    by_kind: dict[str, list[int]] = {}
    for f in layer.features:
        k = by_kind.setdefault(f.props[2], [0, 0])
        k[0] += 1
        k[1] += len(gf.encode_feature(layer.schema, f))
    log.append("  bytes by kind: " + ", ".join(
        f"{k} {v[0]}/{v[1]}" for k, v in sorted(by_kind.items(), key=lambda kv: -kv[1][1])))
    log.extend(head)
    log.append("")
    return layer


def build_geo_points(data, log):
    items = []
    for key in ("ne_50m_geography_regions_elevation_points", "ne_50m_geography_regions_points"):
        for rec, shape in data[key]:
            p = shape.points[0]
            elev = gf.INT32_MISSING
            if key.endswith("elevation_points"):
                ev = rec.get("elevation")
                if ev is not None and ev != "":
                    elev = int(round(float(ev)))
            props = [s(rec["name"]), s(rec["name_en"]), s(rec["featurecla"]), elev,
                     s(rec["region"]), scalerank(rec), wikidata(rec)]
            items.append((ne_id(rec), props, (float(p[0]), float(p[1]))))
    items.sort(key=lambda x: int(x[0]))
    if len({x[0] for x in items}) != len(items):
        raise SystemExit("duplicate ne_id in geography points")
    bounds = layer_bounds(x[2] for x in items)
    q = gf.Quantiser(bounds)
    feats = []
    for fid, props, p in items:
        qp = q.q(p)
        feats.append(gf.Feature(fid, props, (qp[0], qp[1], qp[0], qp[1]), qp, []))
    schema = [("name", gf.PROP_STRING), ("name_en", gf.PROP_STRING), ("kind", gf.PROP_STRING),
              ("elevation_m", gf.PROP_INT32), ("region", gf.PROP_STRING),
              ("scalerank", gf.PROP_UINT8), ("wikidata", gf.PROP_STRING)]
    layer = gf.Layer(gf.LAYER_GEO_POINTS_50M, gf.KIND_POINT, bounds,
                     f"Natural Earth {NE_RELEASE} ne_50m_geography_regions_elevation_points + "
                     "ne_50m_geography_regions_points", schema, feats)
    kinds: dict[str, int] = {}
    for _, props, _ in items:
        kinds[props[2]] = kinds.get(props[2], 0) + 1
    log.append(f"[geo_points_50m] {len(feats)} points: "
               + ", ".join(f"{k} {v}" for k, v in sorted(kinds.items())))
    log.append("")
    return layer


# --------------------------------------------------------------------------
# Validation of the encoded files
# --------------------------------------------------------------------------

def validate(blobs: dict[str, bytes], app: dict) -> list[str]:
    """Decode every file and check the contract. Returns a list of problems."""
    problems = []
    decoded = {}
    for fname, blob in blobs.items():
        try:
            layer = gf.decode(blob)
        except ValueError as e:
            problems.append(f"{fname}: cannot decode: {e}")
            continue
        decoded[fname] = layer
        if gf.encode(layer) != blob:
            problems.append(f"{fname}: re-encoding differs")
        ids = [f.id for f in layer.features]
        if len(set(ids)) != len(ids):
            problems.append(f"{fname}: duplicate feature ids")
        for f in layer.features:
            x0, y0, x1, y1 = f.bbox
            if not (x0 <= x1 and y0 <= y1):
                problems.append(f"{fname}: {f.id} bad bbox")
            for part in f.parts:
                for p in part.points:
                    if not (x0 <= p[0] <= x1 and y0 <= p[1] <= y1):
                        problems.append(f"{fname}: {f.id} point outside its bbox")
                        break
            if layer.kind == gf.KIND_POINT:
                if f.parts:
                    problems.append(f"{fname}: {f.id} point feature with parts")
                continue
            if not f.parts:
                problems.append(f"{fname}: {f.id} has no geometry")
                continue
            if layer.kind == gf.KIND_POLYGON and f.parts[0].flags & gf.FLAG_HOLE:
                problems.append(f"{fname}: {f.id} starts with a hole")
            for part in f.parts:
                pts = part.points
                n = len(pts)
                if layer.kind == gf.KIND_LINE:
                    if n < 2:
                        problems.append(f"{fname}: {f.id} line with {n} points")
                    seq = list(zip(pts, pts[1:]))
                else:
                    if n < 3:
                        problems.append(f"{fname}: {f.id} ring with {n} points")
                    area = geom.signed_area(pts)
                    if part.flags & gf.FLAG_HOLE and area >= 0:
                        problems.append(f"{fname}: {f.id} hole not clockwise")
                    if not part.flags & gf.FLAG_HOLE and area <= 0:
                        problems.append(f"{fname}: {f.id} outer ring not counter-clockwise")
                    seq = list(zip(pts, pts[1:] + pts[:1]))
                q = gf.Quantiser(layer.bounds)
                for a, b in seq:
                    if abs(q.lon(a[0]) - q.lon(b[0])) > 180.0:
                        problems.append(f"{fname}: {f.id} segment crosses the antimeridian")
                        break
    for key, lid in (("admin0_110m.bin", gf.LAYER_ADMIN0_110M), ("admin0_50m.bin", gf.LAYER_ADMIN0_50M)):
        layer = decoded.get(key)
        if layer is None:
            continue
        if layer.layer_id != lid:
            problems.append(f"{key}: wrong layer id")
        play = {f.id for f in layer.features if f.props[2] == 1}
        miss = sorted(set(app) - play)
        if miss:
            problems.append(f"{key}: missing countries {miss}")
        extra = sorted(play - set(app))
        if extra:
            problems.append(f"{key}: playable features not in the app list {extra}")
    a1 = decoded.get("admin1_50m.bin")
    if a1 is not None:
        us = sum(1 for f in a1.features if f.id.startswith("US-"))
        ca = sum(1 for f in a1.features if f.id.startswith("CA-"))
        if (us, ca) != (51, 13):
            problems.append(f"admin1_50m.bin: {us} US and {ca} CA features")
    total = 0
    for fname, blob in blobs.items():
        total += len(blob)
        if len(blob) > BUDGET[fname]:
            problems.append(f"{fname}: {len(blob)} bytes is over its budget of {BUDGET[fname]}")
    if total > TOTAL_BUDGET:
        problems.append(f"total {total} bytes is over the {TOTAL_BUDGET} byte budget (D3)")
    if sum(BUDGET.values()) > TOTAL_BUDGET:
        problems.append("per-layer budgets add up to more than the total budget")
    return problems


# --------------------------------------------------------------------------
# Main
# --------------------------------------------------------------------------

def build(offline: bool, verbose: bool = False) -> tuple[dict[str, bytes], str]:
    app = {c["cca3"]: c for c in derive_countries()}
    if len(app) != 197:
        raise SystemExit(f"expected 197 app countries, found {len(app)}")
    data = {name: read_layer(name, offline) for name in SOURCES}

    log: list[str] = [
        "GeoQuiz geodata build log (generated by tools/geodata/build_geodata.py; do not edit)",
        "",
        f"Natural Earth release {NE_RELEASE} (public domain), {NE_BASE}/",
    ]
    for name, (path, sha, ver) in SOURCES.items():
        log.append(f"  {path}  VERSION.txt {ver}  sha256 {sha}")
    log.append(f"App countries: {len(app)} (data/source/countries.json via tools/data/export_aliases.py)")
    log.append("")

    l110, l50, feats50, fill110 = build_admin0(data, app, log)
    layers = {
        "admin0_110m.bin": l110,
        "admin0_50m.bin": l50,
        "tap_zones.bin": build_tap_zones(data, app, feats50, l50, fill110, log),
        "admin1_50m.bin": build_admin1(data, log),
        "rivers_50m.bin": build_rivers(data, log),
        "lakes_50m.bin": build_lakes(data, log),
        "regions_50m.bin": build_regions(data, log),
        "geo_points_50m.bin": build_geo_points(data, log),
    }
    blobs = {name: gf.encode(layer) for name, layer in layers.items()}
    problems = validate(blobs, app)
    if verbose or problems:
        print("\n".join(log))
    if problems:
        size_report(blobs)
        for p in problems[:50]:
            print(f"ERROR: {p}", file=sys.stderr)
        raise SystemExit(f"{len(problems)} validation problem(s)")

    log.append("Sizes (bytes; budget per layer; 1 KB = 1000 bytes)")
    total = 0
    for name, blob in blobs.items():
        total += len(blob)
        sha = hashlib.sha256(blob).hexdigest()
        log.append(f"  {name:<20} {len(blob):>9}  budget {BUDGET[name]:>9}  "
                   f"{len(layers[name].features):>4} features  sha256 {sha}")
    log.append(f"  {'total':<20} {total:>9}  budget {TOTAL_BUDGET:>9}")
    text = "\n".join(log) + "\n"
    return blobs, text


def size_report(blobs: dict[str, bytes]) -> None:
    total = 0
    print(f"{'file':<22}{'bytes':>10}{'budget':>10}{'used':>7}")
    for name, blob in blobs.items():
        total += len(blob)
        print(f"{name:<22}{len(blob):>10}{BUDGET[name]:>10}{len(blob) / BUDGET[name]:>7.0%}")
    print(f"{'total':<22}{total:>10}{TOTAL_BUDGET:>10}{total / TOTAL_BUDGET:>7.0%}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="Do not write; exit 1 if the committed files differ from a fresh build.")
    parser.add_argument("--offline", action="store_true",
                        help="Never download; fail if a Natural Earth zip is not in the cache.")
    parser.add_argument("--verbose", action="store_true", help="Print the build log.")
    parser.add_argument("--out-dir", type=Path, default=None,
                        help="Write the .bin files and build_log.txt here instead (used by tests).")
    args = parser.parse_args(argv)

    blobs, log_text = build(args.offline, args.verbose)
    size_report(blobs)

    if args.check:
        stale = []
        for name, blob in blobs.items():
            path = OUT_DIR / name
            if not path.exists() or path.read_bytes() != blob:
                stale.append(path.relative_to(REPO_ROOT).as_posix())
        current_log = LOG_PATH.read_bytes().decode("utf-8").replace("\r\n", "\n") if LOG_PATH.exists() else None
        if current_log != log_text:
            stale.append(LOG_PATH.relative_to(REPO_ROOT).as_posix())
        extra = sorted(p.name for p in OUT_DIR.glob("*") if p.name not in blobs) if OUT_DIR.exists() else []
        stale.extend(f"unexpected file {OUT_DIR.relative_to(REPO_ROOT).as_posix()}/{e}" for e in extra)
        if stale:
            print("Out of date: " + ", ".join(stale) + "\nRun: python tools/geodata/build_geodata.py",
                  file=sys.stderr)
            return 1
        print("Map assets are up to date.")
        return 0

    out_dir = args.out_dir or OUT_DIR
    log_path = (args.out_dir / "build_log.txt") if args.out_dir else LOG_PATH
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.bin"):
        if old.name not in blobs:
            old.unlink()
    for name, blob in blobs.items():
        (out_dir / name).write_bytes(blob)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    log_path.write_bytes(log_text.encode("utf-8"))
    print(f"Wrote {len(blobs)} layers to {out_dir} and the build log to {log_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
