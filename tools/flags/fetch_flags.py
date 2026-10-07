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
