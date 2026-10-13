#!/usr/bin/env python3
"""Validate data/flag_lookalikes.json, the hand-authored lookalike flag sets for the flag speed round.

The file is written by hand for GeoQuiz (no third-party source), so this
script does not generate content. It checks the rules the app relies on and
keeps the file in one canonical format:

  * every country is one of the app's 197 (cca3, from export_aliases.derive_countries)
    and has a bundled flag SVG
  * set ids are unique kebab-case; each set has 2-4 distinct countries
  * tier is 1-3; in_app is clear, subtle or near_identical
  * every country in a set has a cue, and no cue names a country outside it
  * explanation and cues are short, and use Australian spelling
  * no two sets list exactly the same countries

Usage (from the repository root or anywhere):

    python tools/data/validate_flag_lookalikes.py           # validate and rewrite in canonical format
    python tools/data/validate_flag_lookalikes.py --check   # validate only; exit 1 on any problem

Standard library only.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_aliases import REPO_ROOT, derive_countries  # noqa: E402

DATA = REPO_ROOT / "data" / "flag_lookalikes.json"
FLAGS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "flags"

IN_APP = {"clear", "subtle", "near_identical"}
MAX_EXPLANATION = 320
MAX_CUE = 110
ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")

# American spellings that must not appear (CLAUDE.md: Australian spelling).
US_SPELLINGS = ("color", "center", "gray", "favorite", "organiz", "meter", "honor", "theater")


def us_spellings(text: str) -> list[str]:
    found = []
    for word in US_SPELLINGS:
        if re.search(rf"\b{word}", text, re.IGNORECASE):
            found.append(word)
    return found


def validate(data: dict, app_codes: set[str]) -> list[str]:
    errors: list[str] = []
    if data.get("version") != 1:
        errors.append("version must be 1")
    sets = data.get("sets")
    if not isinstance(sets, list) or not sets:
        return errors + ["sets must be a non-empty list"]

    seen_ids: set[str] = set()
    seen_members: dict[frozenset, str] = {}
    for i, s in enumerate(sets):
        where = f"sets[{i}] ({s.get('id', '?')})"
        sid = s.get("id")
        if not isinstance(sid, str) or not ID_RE.match(sid):
            errors.append(f"{where}: id must be kebab-case")
        elif sid in seen_ids:
            errors.append(f"{where}: duplicate id")
        else:
            seen_ids.add(sid)

        countries = s.get("countries")
        if not isinstance(countries, list) or not 2 <= len(countries) <= 4:
            errors.append(f"{where}: countries must list 2-4 codes")
            continue
        if len(set(countries)) != len(countries):
            errors.append(f"{where}: duplicate country")
        for c in countries:
            if c not in app_codes:
                errors.append(f"{where}: {c} is not one of the app's countries")
            elif not (FLAGS_DIR / f"{c.lower()}.svg").exists():
                errors.append(f"{where}: no bundled flag for {c}")
        key = frozenset(countries)
        if key in seen_members:
            errors.append(f"{where}: same countries as {seen_members[key]}")
        seen_members[key] = sid

        if s.get("tier") not in (1, 2, 3):
            errors.append(f"{where}: tier must be 1, 2 or 3")
        if s.get("in_app") not in IN_APP:
            errors.append(f"{where}: in_app must be one of {sorted(IN_APP)}")

        explanation = s.get("explanation")
        if not isinstance(explanation, str) or not explanation.strip():
            errors.append(f"{where}: explanation missing")
        elif len(explanation) > MAX_EXPLANATION:
            errors.append(f"{where}: explanation longer than {MAX_EXPLANATION} characters")
        else:
            for w in us_spellings(explanation):
                errors.append(f"{where}: explanation uses US spelling '{w}'")

        cues = s.get("cues")
        if not isinstance(cues, dict):
            errors.append(f"{where}: cues must be an object")
            continue
        if set(cues) != set(countries):
            errors.append(f"{where}: cues must cover exactly the set's countries")
        for c, text in cues.items():
            if not isinstance(text, str) or not text.strip():
                errors.append(f"{where}: empty cue for {c}")
            elif len(text) > MAX_CUE:
                errors.append(f"{where}: cue for {c} longer than {MAX_CUE} characters")
            else:
                for w in us_spellings(text):
                    errors.append(f"{where}: cue for {c} uses US spelling '{w}'")
    return errors


def render(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true",
                        help="Do not write; exit 1 if the file is invalid or not in canonical format.")
    args = parser.parse_args(argv)

    raw = DATA.read_bytes().decode("utf-8").replace("\r\n", "\n")
    data = json.loads(raw)
    app_codes = {c["cca3"] for c in derive_countries()}
    errors = validate(data, app_codes)
    rel = DATA.relative_to(REPO_ROOT).as_posix()
    if errors:
        for e in errors:
            print(f"{rel}: {e}", file=sys.stderr)
        return 1

    text = render(data)
    if args.check:
        if raw != text:
            print(f"{rel} is not in canonical format. Run: python tools/data/validate_flag_lookalikes.py",
                  file=sys.stderr)
            return 1
    elif raw != text:
        DATA.write_bytes(text.encode("utf-8"))

    countries = {c for s in data["sets"] for c in s["countries"]}
    by_view = {v: sum(1 for s in data["sets"] if s["in_app"] == v) for v in sorted(IN_APP)}
    print(f"{rel}: {len(data['sets'])} sets, {len(countries)} countries, in_app {by_view}. OK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
