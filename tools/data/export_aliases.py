#!/usr/bin/env python3
"""Export the country and capital alias table that GeoQuiz ships in its static database.

The app derives its answer aliases from mledoze/countries (ODbL 1.0) plus the
hand-written maps in data/source/alias_overrides.json. Because that derived
table is a "Derivative Database" under the ODbL, we publish it separately as
data/aliases.json under the same licence.

This module is also the single home of the derivation rules, shared with
tools/data/build_static_db.py (which builds app/src/main/assets/databases/static.db):

  * derive_countries()   filter (UN members + extraCountries), alias sets,
                         short-code rule, capital aliases
  * normalize_input()    exact port of NormalizeInputUseCase.invoke(); the
                         Robolectric StaticDatabaseTest checks every shipped
                         alias against the Kotlin implementation

Usage (from the repository root or anywhere):

    python tools/data/export_aliases.py           # regenerate data/aliases.json
    python tools/data/export_aliases.py --check   # exit 1 if the file is stale

Standard library only.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unicodedata
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = REPO_ROOT / "data" / "source"
COUNTRIES_JSON = SOURCE_DIR / "countries.json"
OVERRIDES_JSON = SOURCE_DIR / "alias_overrides.json"
OUTPUT = REPO_ROOT / "data" / "aliases.json"

SOURCE = {
    "name": "mledoze/countries",
    "url": "https://github.com/mledoze/countries",
    "licence": "ODbL-1.0",
    "file": "data/source/countries.json",
}


# --------------------------------------------------------------------------
# Kotlin / Java character semantics
# --------------------------------------------------------------------------

def kt_is_whitespace(ch: str) -> bool:
    """Kotlin Char.isWhitespace() == Character.isWhitespace || isSpaceChar."""
    if ch in "\t\n\x0b\x0c\r\x1c\x1d\x1e\x1f":
        return True
    return unicodedata.category(ch) in ("Zs", "Zl", "Zp")


def kt_is_blank(s: str) -> bool:
    """Kotlin CharSequence.isBlank()."""
    return all(kt_is_whitespace(c) for c in s)


def kt_trim(s: str) -> str:
    """Kotlin String.trim() (trims chars where Char.isWhitespace())."""
    start, end = 0, len(s)
    while start < end and kt_is_whitespace(s[start]):
        start += 1
    while end > start and kt_is_whitespace(s[end - 1]):
        end -= 1
    return s[start:end]


def kt_is_upper(ch: str) -> bool:
    """Kotlin Char.isUpperCase(): category Lu or Other_Uppercase.

    For the Latin data in countries.json this is equivalent to Python's
    str.isupper() on a single cased character; Other_Uppercase only covers
    circled/enclosed letters, which don't occur in the source.
    """
    return unicodedata.category(ch) == "Lu" or (ch.isupper() and len(ch) == 1)


def kt_length(s: str) -> int:
    """Kotlin String.length counts UTF-16 code units."""
    return len(s.encode("utf-16-le")) // 2


# Java regex `\s` without UNICODE_CHARACTER_CLASS is ASCII-only.
JAVA_WHITESPACE_RE = re.compile(r"[ \t\n\x0b\x0c\r]+")
# Java `\p{InCombiningDiacriticalMarks}` is the U+0300..U+036F block only.
DIACRITICS_RE = re.compile("[̀-ͯ]")


def normalize_input(text: str) -> str:
    """Python port of NormalizeInputUseCase.invoke()."""
    if kt_is_blank(text):
        return ""
    decomposed = unicodedata.normalize("NFD", text)
    no_diacritics = DIACRITICS_RE.sub("", decomposed)
    out = (
        no_diacritics.lower()
        .replace("-", " ")
        .replace("'", "")
        .replace("’", "")
        .replace("‘", "")
        .replace("ʼ", "")
        .replace(".", "")
        .replace(",", " ")
        .replace("&", " and ")
    )
    out = kt_trim(out)
    out = JAVA_WHITESPACE_RE.sub(" ", out)
    # Word-level canonicalisation: "st" -> "saint", drop a leading "the".
    words = ["saint" if w == "st" else w for w in out.split(" ")]
    if len(words) > 1 and words[0] == "the":
        words = words[1:]
    return " ".join(words)


# --------------------------------------------------------------------------
# Inputs
# --------------------------------------------------------------------------

def _string_list(value, where: str) -> list[str]:
    if not isinstance(value, list) or not all(isinstance(v, str) for v in value):
        raise SystemExit(f"{where} must be a list of strings")
    return value


def _string_list_map(value, where: str) -> dict[str, list[str]]:
    if not isinstance(value, dict):
        raise SystemExit(f"{where} must be an object")
    for key in value:
        if not re.fullmatch(r"[A-Z]{3}", key):
            raise SystemExit(f"{where}: key {key!r} is not a cca3 code")
    return {k: _string_list(v, f"{where}.{k}") for k, v in value.items()}


def load_overrides() -> tuple[dict[str, list[str]], dict[str, list[str]], set[str]]:
    """Return (abbreviations, capital_aliases, extra_countries) from alias_overrides.json."""
    data = json.loads(OVERRIDES_JSON.read_text(encoding="utf-8"))
    abbreviations = _string_list_map(data.get("abbreviations"), "abbreviations")
    capital_aliases = _string_list_map(data.get("capitalAliases"), "capitalAliases")
    extra = set(_string_list(data.get("extraCountries"), "extraCountries"))
    return abbreviations, capital_aliases, extra


# --------------------------------------------------------------------------
# Derivation (shared with build_static_db.py)
# --------------------------------------------------------------------------

def _require(entry: dict, key: str, typ, default=None, nullable=False):
    """Mimic the kotlinx.serialization decoding the app used before Phase 2."""
    if key not in entry:
        if default is None and not nullable:
            raise SystemExit(f"countries.json entry missing required field {key!r}")
        return default
    value = entry[key]
    if value is None:
        if nullable:
            return None
        raise SystemExit(f"countries.json field {key!r} is null but not nullable")
    if not isinstance(value, typ):
        raise SystemExit(f"countries.json field {key!r} has type {type(value).__name__}")
    return value


def derive_countries() -> list[dict]:
    """Every country the app shows, in countries.json order.

    Each record has cca3, cca2, common, official, region, subregion, capitals,
    aliases and capitalAliases. The alias lists hold {"alias", "normalized"}
    dicts in insertion order (as the old Kotlin LinkedHashSet seeding did).
    """
    abbreviations, capital_aliases_map, extra_countries = load_overrides()
    raw = json.loads(COUNTRIES_JSON.read_text(encoding="utf-8"))

    countries = []
    for entry in raw:
        cca3 = _require(entry, "cca3", str)
        cca2 = _require(entry, "cca2", str, default="")
        name = _require(entry, "name", dict)
        common = _require(name, "common", str)
        official = _require(name, "official", str)
        region = _require(entry, "region", str)
        subregion = _require(entry, "subregion", str, nullable=True) or ""
        alt_spellings = _require(entry, "altSpellings", list, default=[])
        un_member = _require(entry, "unMember", bool, default=False)
        capitals = _require(entry, "capital", list, default=[])

        if not (un_member or cca3 in extra_countries):
            continue

        # Country name aliases: LinkedHashSet in insertion order.
        alias_set: dict[str, None] = {}
        alias_set[common] = None
        alias_set[official] = None
        for a in alt_spellings:
            alias_set[a] = None
        abbrevs = abbreviations.get(cca3)
        if abbrevs is not None:
            for a in abbrevs:
                alias_set[a] = None
        allowed_short = set(abbrevs) if abbrevs is not None else set()

        aliases = []
        for alias in alias_set:
            if kt_is_blank(alias):
                continue
            # Drop codes such as "FR" or "FRA" unless whitelisted for this country.
            if (
                kt_length(alias) <= 3
                and all(kt_is_upper(c) for c in alias)
                and alias not in allowed_short
            ):
                continue
            aliases.append({"alias": alias, "normalized": normalize_input(alias)})

        # Capital aliases.
        capital_set: dict[str, None] = {}
        for cap in capitals:
            capital_set[cap] = None
        for cap in capital_aliases_map.get(cca3, []):
            capital_set[cap] = None

        cap_aliases = []
        for cap in capital_set:
            if kt_is_blank(cap):
                continue
            cap_aliases.append({"alias": cap, "normalized": normalize_input(cap)})

        countries.append({
            "cca3": cca3,
            "cca2": cca2,
            "common": common,
            "official": official,
            "region": region,
            "subregion": subregion,
            "capitals": capitals,
            "aliases": aliases,
            "capitalAliases": cap_aliases,
        })
    check_collisions(countries)
    return countries


def check_collisions(countries: list[dict]) -> None:
    """Fail if a normalised alias is empty or would answer for two different countries.

    The app resolves an answer by exact match on the normalised form, so a shared
    form would make one of the two countries impossible to name (and would let
    the typo matcher treat an exact answer as ambiguous).
    """
    problems = []
    for kind in ("aliases", "capitalAliases"):
        owners: dict[str, set[str]] = {}
        for c in countries:
            for a in c[kind]:
                if not a["normalized"]:
                    problems.append(f"{kind}: {a['alias']!r} ({c['cca3']}) normalises to an empty string")
                owners.setdefault(a["normalized"], set()).add(c["cca3"])
        for normalized, cca3s in sorted(owners.items()):
            if len(cca3s) > 1:
                problems.append(f"{kind}: {normalized!r} is shared by {', '.join(sorted(cca3s))}")
    if problems:
        raise SystemExit("Alias collisions:\n  " + "\n  ".join(problems))


# --------------------------------------------------------------------------
# Build data/aliases.json
# --------------------------------------------------------------------------

def build() -> dict:
    sort_key = lambda a: (a["normalized"], a["alias"])  # noqa: E731
    countries = [
        {
            "cca3": c["cca3"],
            "name": c["common"],
            "officialName": c["official"],
            "capital": c["capitals"][0] if c["capitals"] else "",
            "aliases": sorted(c["aliases"], key=sort_key),
            "capitalAliases": sorted(c["capitalAliases"], key=sort_key),
        }
        for c in derive_countries()
    ]
    countries.sort(key=lambda c: c["cca3"])

    return {
        "licence": "ODbL-1.0",
        "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
        "attribution": (
            "Contains information from mledoze/countries "
            "(https://github.com/mledoze/countries), made available under the "
            "Open Database License (ODbL) v1.0."
        ),
        "source": SOURCE,
        "generatedBy": "tools/data/export_aliases.py",
        "rulesFrom": [
            "data/source/alias_overrides.json",
            "app/src/main/java/com/geoquiz/app/domain/usecase/NormalizeInputUseCase.kt",
        ],
        "countryCount": len(countries),
        "aliasCount": sum(len(c["aliases"]) for c in countries),
        "capitalAliasCount": sum(len(c["capitalAliases"]) for c in countries),
        "countries": countries,
    }


def render(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not write; exit 1 if data/aliases.json is missing or out of date.",
    )
    args = parser.parse_args(argv)

    data = build()
    text = render(data)

    if args.check:
        current = OUTPUT.read_bytes().decode("utf-8") if OUTPUT.exists() else None
        # Ignore CRLF vs LF so a Windows checkout with core.autocrlf passes.
        if current is not None:
            current = current.replace("\r\n", "\n")
        if current != text:
            print(
                f"{OUTPUT.relative_to(REPO_ROOT).as_posix()} is out of date. "
                "Run: python tools/data/export_aliases.py",
                file=sys.stderr,
            )
            return 1
        print(f"{OUTPUT.relative_to(REPO_ROOT).as_posix()} is up to date.")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    # Write bytes so line endings are always LF regardless of platform.
    OUTPUT.write_bytes(text.encode("utf-8"))
    print(
        f"Wrote {OUTPUT.relative_to(REPO_ROOT).as_posix()}: "
        f"{data['countryCount']} countries, {data['aliasCount']} aliases, "
        f"{data['capitalAliasCount']} capital aliases."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
