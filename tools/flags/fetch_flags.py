#!/usr/bin/env python3
"""Fetch pinned flag-icons SVGs into the app's assets.

Downloads a pinned flag-icons release (lipis/flag-icons, MIT) from the npm
registry, verifies its SHA-256, and writes the 4x3 SVG for every country the
app uses to app/src/main/assets/flags/<cca3 lower>.svg, so the app never needs
a cca3 -> cca2 mapping at runtime. Also copies the flag-icons LICENSE.

Stdlib only. Run from anywhere:  python tools/flags/fetch_flags.py
"""

from __future__ import annotations

import hashlib
import io
import json
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

FLAG_ICONS_VERSION = "7.5.0"
TARBALL_URL = (
    f"https://registry.npmjs.org/flag-icons/-/flag-icons-{FLAG_ICONS_VERSION}.tgz"
)
TARBALL_SHA256 = "c0b80bf0e08006a60f56621d6bc49f8c7131f4d1fef6737a165a673431f4b518"

# Non-UN-member entries the app includes alongside UN members.
# Keep in sync with extraCountries in data/source/alias_overrides.json.
EXTRA_CCA3 = {"VAT", "PSE", "TWN", "UNK"}

REPO_ROOT = Path(__file__).resolve().parents[2]
COUNTRIES_JSON = REPO_ROOT / "data" / "source" / "countries.json"
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "flags"
LICENCE_COPY = Path(__file__).resolve().parent / "LICENSE.flag-icons"


def used_countries() -> list[tuple[str, str]]:
    """Return (cca3, cca2) for every country the app shows."""
    with COUNTRIES_JSON.open(encoding="utf-8") as f:
        data = json.load(f)
    result = []
    for c in data:
        cca3 = c["cca3"]
        if c.get("unMember") is True or cca3 in EXTRA_CCA3:
            result.append((cca3, c["cca2"]))
    return sorted(result)


# SVG <marker> is not drawn by AndroidSVG (coil-svg), so a flag that places its
# emblems with marker-mid shows without them. Only the US flag does this in
# 7.5.0 (its 50 stars). expand_us_star_markers() rewrites it with one explicit
# star path per vertex, drawing exactly the same shapes.
US_STAR_MARKER = '<path fill="#fff" d="m14 0 9 27L0 10h28L5 27z"/>'
US_STAR_PATH_START = '<path fill="none" marker-mid="url(#us-a)" d="'


def _polyline_vertices(d: str) -> list[tuple[float, float]]:
    """Vertices of a path made only of m (with implicit relative lines), h and L."""
    import re

    tokens = re.findall(r"[mhLz]|-?\d+(?:\.\d+)?", d)
    x = y = 0.0
    points: list[tuple[float, float]] = []
    cmd = ""
    i = 0
    while i < len(tokens):
        if tokens[i] in ("m", "h", "L", "z"):
            cmd = tokens[i]
            i += 1
        if cmd == "z":
            break
        if cmd == "m":
            x, y = x + float(tokens[i]), y + float(tokens[i + 1])
            i += 2
        elif cmd == "h":
            x += float(tokens[i])
            i += 1
        elif cmd == "L":
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
        else:
            sys.exit(f"unexpected path command {cmd!r} in US star path")
        points.append((x, y))
    return points


def expand_us_star_markers(svg: bytes) -> bytes:
    text = svg.decode("utf-8")
    start = text.find(US_STAR_PATH_START)
    marker_start = text.find("<marker")
    if start < 0 or marker_start < 0 or US_STAR_MARKER not in text:
        sys.exit("US flag no longer matches the expected marker layout; update fetch_flags.py")
    d_start = start + len(US_STAR_PATH_START)
    d = text[d_start:text.index('"', d_start)]
    # marker-mid draws at every vertex except the first (the closing z adds none).
    mids = _polyline_vertices(d)[1:]
    if len(mids) != 50:
        sys.exit(f"expected 50 US stars, found {len(mids)}")
    stars = "".join(
        f"M{px + 14:g} {py:g}l9 27L{px:g} {py + 10:g}h28L{px + 5:g} {py + 27:g}z"
        for px, py in mids
    )
    # The marker definition sits directly before the starred path; replace both.
    path_end = text.index("/>", start) + len("/>")
    text = text[:marker_start] + f'<path fill="#fff" d="{stars}"/>' + text[path_end:]
    return text.encode("utf-8")


def download() -> bytes:
    print(f"Downloading {TARBALL_URL}")
    with urllib.request.urlopen(TARBALL_URL, timeout=60) as resp:
        payload = resp.read()
    digest = hashlib.sha256(payload).hexdigest()
    if digest != TARBALL_SHA256:
        sys.exit(
            f"SHA-256 mismatch for {TARBALL_URL}\n"
            f"  expected {TARBALL_SHA256}\n  got      {digest}"
        )
    print(f"SHA-256 verified: {digest}")
    return payload


def main() -> int:
    countries = used_countries()
    payload = download()

    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz") as tar:
        members = {m.name: m for m in tar.getmembers() if m.isfile()}

        def read(name: str) -> bytes | None:
            m = members.get(name)
            if m is None:
                return None
            f = tar.extractfile(m)
            return f.read() if f else None

        licence = read("package/LICENSE")
        if licence is None:
            sys.exit("LICENSE not found in tarball")

        svgs: dict[str, bytes] = {}
        missing = []
        for cca3, cca2 in countries:
            svg = read(f"package/flags/4x3/{cca2.lower()}.svg")
            if svg is None:
                missing.append(f"{cca3} (cca2 {cca2})")
            else:
                if cca3 == "USA":
                    svg = expand_us_star_markers(svg)
                svgs[cca3.lower()] = svg

    if missing:
        print("ERROR: no flag-icons SVG for:", file=sys.stderr)
        for m in missing:
            print(f"  {m}", file=sys.stderr)
        return 1

    if OUT_DIR.exists():
        shutil.rmtree(OUT_DIR)
    OUT_DIR.mkdir(parents=True)
    total = 0
    for code, svg in sorted(svgs.items()):
        (OUT_DIR / f"{code}.svg").write_bytes(svg)
        total += len(svg)
    (OUT_DIR / "LICENSE").write_bytes(licence)
    LICENCE_COPY.write_bytes(licence)

    print(
        f"Wrote {len(svgs)} SVGs ({total / 1024:.1f} KiB) and LICENSE to "
        f"{OUT_DIR.relative_to(REPO_ROOT)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
