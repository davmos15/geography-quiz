#!/usr/bin/env python3
"""Export the country and capital alias table that GeoQuiz builds at seed time.

The app derives its answer aliases from mledoze/countries (ODbL 1.0) plus two
hand-written maps in CountryRepositoryImpl.kt. Because that derived table is a
"Derivative Database" under the ODbL, we publish it separately as
data/aliases.json under the same licence.

This script replicates the Kotlin seeding logic exactly:

  * CountryRepositoryImpl.seedFromAsset()  (filter, alias sets, short-code rule)
  * NormalizeInputUseCase.invoke()         (normalised form)

ABBREVIATIONS and CAPITAL_ALIASES are parsed straight out of the Kotlin source,
so the Kotlin file stays the single source of truth.

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
COUNTRIES_JSON = REPO_ROOT / "app" / "src" / "main" / "assets" / "countries.json"
REPOSITORY_KT = (
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "geoquiz" / "app"
    / "data" / "repository" / "CountryRepositoryImpl.kt"
)
OUTPUT = REPO_ROOT / "data" / "aliases.json"

# Must match `extraCountries` in CountryRepositoryImpl.seedFromAsset().
EXTRA_COUNTRIES_RE = re.compile(r"val\s+extraCountries\s*=\s*setOf\(([^)]*)\)")

SOURCE = {
    "name": "mledoze/countries",
    "url": "https://github.com/mledoze/countries",
    "licence": "ODbL-1.0",
    "file": "app/src/main/assets/countries.json",
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
    )
    out = kt_trim(out)
    return JAVA_WHITESPACE_RE.sub(" ", out)


# --------------------------------------------------------------------------
# Kotlin source parsing
# --------------------------------------------------------------------------

def _kt_string_literals(text: str) -> list[str]:
    literals = re.findall(r'"((?:[^"\\]|\\.)*)"', text)
    for lit in literals:
        if "\\" in lit or "$" in lit:
            raise SystemExit(
                f"Unsupported escape or template in Kotlin string literal: {lit!r}"
            )
    return literals


def parse_kotlin_map(source: str, name: str) -> dict[str, list[str]]:
    """Parse `private val NAME = mapOf("K" to listOf("a", "b"), ...)`."""
    m = re.search(rf"val\s+{name}\s*=\s*mapOf\(", source)
    if not m:
        raise SystemExit(f"Could not find `{name} = mapOf(` in {REPOSITORY_KT}")
    # Find the matching close paren of mapOf( ... ).
    depth, i = 1, m.end()
    in_str = False
    while depth and i < len(source):
        ch = source[i]
        if in_str:
            if ch == "\\":
                i += 1
            elif ch == '"':
                in_str = False
        elif ch == '"':
            in_str = True
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        i += 1
    body = source[m.end(): i - 1]

    entry_re = re.compile(r'"([A-Z]{3})"\s+to\s+listOf\(([^)]*)\)')
    result: dict[str, list[str]] = {}
    consumed = entry_re.sub("", body)
    if re.sub(r"[\s,]", "", re.sub(r"//[^\n]*", "", consumed)):
        raise SystemExit(f"Unexpected content in {name}: {consumed.strip()[:200]!r}")
    for key, values in entry_re.findall(body):
        if key in result:
            # Kotlin mapOf keeps the last value for a duplicate key.
            print(f"warning: duplicate key {key} in {name}", file=sys.stderr)
        result[key] = _kt_string_literals(values)
    if not result:
        raise SystemExit(f"Parsed no entries from {name}")
    return result


def parse_extra_countries(source: str) -> set[str]:
    m = EXTRA_COUNTRIES_RE.search(source)
    if not m:
        raise SystemExit("Could not find `extraCountries = setOf(...)` in Kotlin source")
    return set(_kt_string_literals(m.group(1)))


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

def _require(entry: dict, key: str, typ, default=None, nullable=False):
    """Mimic kotlinx.serialization decoding of CountryJson fields."""
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


def build() -> dict:
    kt_source = REPOSITORY_KT.read_text(encoding="utf-8")
    abbreviations = parse_kotlin_map(kt_source, "ABBREVIATIONS")
    capital_aliases_map = parse_kotlin_map(kt_source, "CAPITAL_ALIASES")
    extra_countries = parse_extra_countries(kt_source)

    raw = json.loads(COUNTRIES_JSON.read_text(encoding="utf-8"))

    countries = []
    for entry in raw:
        cca3 = _require(entry, "cca3", str)
        name = _require(entry, "name", dict)
        common = _require(name, "common", str)
        official = _require(name, "official", str)
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

        sort_key = lambda a: (a["normalized"], a["alias"])  # noqa: E731
        countries.append({
            "cca3": cca3,
            "name": common,
            "officialName": official,
            "capital": capitals[0] if capitals else "",
            "aliases": sorted(aliases, key=sort_key),
            "capitalAliases": sorted(cap_aliases, key=sort_key),
        })

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
            "app/src/main/java/com/geoquiz/app/data/repository/CountryRepositoryImpl.kt",
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
