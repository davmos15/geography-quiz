#!/usr/bin/env python3
"""Tests for the GeoQuiz geodata pipeline.

    python tools/geodata/test_geodata.py          # or: python -m unittest tools/geodata/test_geodata.py

ContractTest reads the committed files in app/src/main/assets/geo/ with the
reference decoder only (standard library, no downloads), so it can run
anywhere, including CI.

ReproducibilityTest runs the full build twice in subprocesses with different
PYTHONHASHSEED values (offline, so it needs a warm tools/geodata/.cache/; it is
skipped otherwise) and checks the outputs are byte-identical to each other and
to the committed files.
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parents[1]
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets" / "geo"
LOG = REPO_ROOT / "data" / "geo" / "build_log.txt"
sys.dont_write_bytecode = True
sys.path.insert(0, str(HERE))

import geoformat as gf  # noqa: E402

FILES = {
    "admin0_110m.bin": (gf.LAYER_ADMIN0_110M, gf.KIND_POLYGON),
    "admin0_50m.bin": (gf.LAYER_ADMIN0_50M, gf.KIND_POLYGON),
    "tap_zones.bin": (gf.LAYER_TAP_ZONES, gf.KIND_POINT),
    "admin1_50m.bin": (gf.LAYER_ADMIN1_50M, gf.KIND_POLYGON),
    "rivers_50m.bin": (gf.LAYER_RIVERS_50M, gf.KIND_LINE),
    "lakes_50m.bin": (gf.LAYER_LAKES_50M, gf.KIND_POLYGON),
    "regions_50m.bin": (gf.LAYER_REGIONS_50M, gf.KIND_POLYGON),
    "geo_points_50m.bin": (gf.LAYER_GEO_POINTS_50M, gf.KIND_POINT),
}
TOTAL_BUDGET = 1_500_000


def app_countries() -> set[str]:
    """The app's 197 countries, same filter as tools/data/export_aliases.py."""
    data = json.loads((REPO_ROOT / "data" / "source" / "countries.json").read_text(encoding="utf-8"))
    extra = set(json.loads((REPO_ROOT / "data" / "source" / "alias_overrides.json")
                           .read_text(encoding="utf-8"))["extraCountries"])
    return {c["cca3"] for c in data if c.get("unMember") is True or c["cca3"] in extra}


def signed_area(pts) -> int:
    s = 0
    for i in range(len(pts)):
        x1, y1 = pts[i - 1]
        x2, y2 = pts[i]
        s += x1 * y2 - x2 * y1
    return s


class ContractTest(unittest.TestCase):
    layers: dict = {}

    @classmethod
    def setUpClass(cls):
        cls.layers = {name: gf.decode((ASSETS / name).read_bytes()) for name in FILES}

    def test_files_and_ids(self):
        self.assertEqual(sorted(p.name for p in ASSETS.iterdir()), sorted(FILES))
        for name, (lid, kind) in FILES.items():
            layer = self.layers[name]
            self.assertEqual((layer.layer_id, layer.kind), (lid, kind), name)
            ids = [f.id for f in layer.features]
            self.assertEqual(len(ids), len(set(ids)), f"{name}: duplicate ids")

    def test_round_trip(self):
        for name in FILES:
            self.assertEqual(gf.encode(self.layers[name]), (ASSETS / name).read_bytes(), name)

    def test_all_197_countries_in_both_admin0_levels(self):
        app = app_countries()
        self.assertEqual(len(app), 197)
        for name in ("admin0_110m.bin", "admin0_50m.bin"):
            playable = {f.id for f in self.layers[name].features if f.props[2] == 1}
            self.assertEqual(playable, app, name)
            for f in self.layers[name].features:
                if f.props[2] == 0:
                    self.assertNotIn(f.id, app)

    def test_admin1_counts(self):
        ids = [f.id for f in self.layers["admin1_50m.bin"].features]
        self.assertEqual(sum(i.startswith("US-") for i in ids), 51)
        self.assertEqual(sum(i.startswith("CA-") for i in ids), 13)
        self.assertIn("US-DC", ids)
        self.assertIn("CA-NU", ids)

    def test_geometry_rules(self):
        for name, layer in self.layers.items():
            q = gf.Quantiser(layer.bounds)
            for f in layer.features:
                if layer.kind == gf.KIND_POINT:
                    self.assertEqual(f.parts, [], f"{name} {f.id}")
                    continue
                self.assertTrue(f.parts, f"{name} {f.id}: no parts")
                if layer.kind == gf.KIND_POLYGON:
                    self.assertFalse(f.parts[0].flags & gf.FLAG_HOLE, f"{name} {f.id}: starts with a hole")
                for part in f.parts:
                    pts = part.points
                    if layer.kind == gf.KIND_LINE:
                        self.assertGreaterEqual(len(pts), 2)
                        segs = zip(pts, pts[1:])
                    else:
                        self.assertGreaterEqual(len(pts), 3, f"{name} {f.id}")
                        a = signed_area(pts)
                        if part.flags & gf.FLAG_HOLE:
                            self.assertLess(a, 0, f"{name} {f.id}: hole winding")
                        else:
                            self.assertGreater(a, 0, f"{name} {f.id}: outer winding")
                        segs = zip(pts, pts[1:] + pts[:1])
                    for a, b in segs:
                        self.assertLessEqual(abs(q.lon(a[0]) - q.lon(b[0])), 180.0,
                                             f"{name} {f.id}: antimeridian")
                    for x, y in pts:
                        self.assertTrue(f.bbox[0] <= x <= f.bbox[2] and f.bbox[1] <= y <= f.bbox[3])

    def test_remote_and_wrap_examples(self):
        by = {f.id: f for f in self.layers["admin0_50m.bin"].features}
        q = gf.Quantiser(self.layers["admin0_50m.bin"].bounds)

        def parts_near(fid, lon, lat):
            out = []
            for p in by[fid].parts:
                if p.flags & gf.FLAG_HOLE:
                    continue
                xs = [q.lon(x) for x, _ in p.points]
                ys = [q.lat(y) for _, y in p.points]
                if min(xs) - 1 <= lon <= max(xs) + 1 and min(ys) - 1 <= lat <= max(ys) + 1:
                    out.append(p)
            return out

        def remote(fid, lon, lat):
            ps = parts_near(fid, lon, lat)
            self.assertTrue(ps, f"{fid} has no part near {lon},{lat}")
            return all(p.flags & gf.FLAG_REMOTE for p in ps)

        self.assertTrue(remote("FRA", -53.0, 4.0))      # French Guiana
        self.assertTrue(remote("FRA", 55.5, -21.1))     # Reunion
        self.assertFalse(remote("FRA", 9.0, 42.1))      # Corsica
        self.assertTrue(remote("USA", -155.5, 19.6))    # Hawaii
        self.assertFalse(remote("USA", -150.0, 64.0))   # Alaska
        self.assertTrue(remote("ESP", -15.5, 28.0))     # Canary Islands
        self.assertFalse(remote("NOR", 16.0, 78.5))     # Svalbard
        self.assertFalse(remote("ECU", -91.0, -0.7))    # Galapagos
        self.assertTrue(any(p.flags & gf.FLAG_WRAP for p in by["RUS"].parts))   # Chukotka
        self.assertTrue(any(p.flags & gf.FLAG_WRAP for p in by["FJI"].parts))

    def test_tap_zones_cover_microstates(self):
        ids = {f.id for f in self.layers["tap_zones.bin"].features}
        for c in ("VAT", "MCO", "SMR", "LIE", "AND", "NRU", "TUV", "SGP", "MLT", "BHR"):
            self.assertIn(c, ids)
        self.assertTrue(ids <= app_countries())

    def test_budget(self):
        total = sum((ASSETS / name).stat().st_size for name in FILES)
        self.assertLessEqual(total, TOTAL_BUDGET)


class ReproducibilityTest(unittest.TestCase):
    def test_two_builds_are_identical_and_match_committed(self):
        cache = HERE / ".cache"
        if not cache.exists() or not any(cache.iterdir()):
            self.skipTest("tools/geodata/.cache is empty; run build_geodata.py once first")
        outs = []
        with tempfile.TemporaryDirectory() as tmp:
            for seed in ("1", "12345"):
                out = Path(tmp) / seed
                env = dict(os.environ, PYTHONHASHSEED=seed)
                r = subprocess.run(
                    [sys.executable, str(HERE / "build_geodata.py"), "--offline", "--out-dir", str(out)],
                    env=env, capture_output=True, text=True,
                )
                self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
                outs.append({p.name: p.read_bytes() for p in sorted(out.iterdir())})
        self.assertEqual(outs[0], outs[1])
        committed = {name: (ASSETS / name).read_bytes() for name in FILES}
        committed["build_log.txt"] = LOG.read_bytes().replace(b"\r\n", b"\n")
        self.assertEqual(outs[0], committed)


if __name__ == "__main__":
    unittest.main()
