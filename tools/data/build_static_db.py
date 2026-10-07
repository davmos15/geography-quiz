#!/usr/bin/env python3
"""Build GeoQuiz's prebuilt static content database.

Output: app/src/main/assets/databases/static.db, opened by the app with Room
`createFromAsset()` (see StaticDatabase.kt and DatabaseModule.kt).

Inputs:
  * data/source/countries.json        mledoze/countries (ODbL 1.0)
  * data/source/alias_overrides.json  hand-written aliases and extra countries
  * data/source/flag_colors.json      flag colours per country
  * data/source/flag_elements.json    flag elements per country
  * app/schemas/com.geoquiz.app.data.local.db.StaticDatabase/<VERSION>.json
        the schema Room exports at build time; tables, indices and the identity
        hash are taken from it so Room accepts the file as-is.

Country and alias derivation (filter, alias sets, normalisation) is shared with
tools/data/export_aliases.py, so data/aliases.json and this database always agree.

To change static content: edit the inputs, bump StaticDatabase.VERSION, build
the app once (to export the new schema JSON), then run this script. Never write
a migration; the app replaces the installed copy when the asset version rises.

Usage (from the repository root or anywhere):

    python tools/data/build_static_db.py           # regenerate the asset
    python tools/data/build_static_db.py --check   # exit 1 if the asset is stale

--check compares logical content (schema, user_version and every row), not
bytes, because SQLite's file layout can differ between SQLite versions.

Standard library only.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sqlite3
import sys
import tempfile
from pathlib import Path

sys.dont_write_bytecode = True  # keep tools/data free of __pycache__
sys.path.insert(0, str(Path(__file__).resolve().parent))
import export_aliases as rules  # noqa: E402  (shared derivation rules)

REPO_ROOT = rules.REPO_ROOT
FLAG_COLORS_JSON = rules.SOURCE_DIR / "flag_colors.json"
FLAG_ELEMENTS_JSON = rules.SOURCE_DIR / "flag_elements.json"
STATIC_DATABASE_KT = (
    REPO_ROOT / "app" / "src" / "main" / "java" / "com" / "geoquiz" / "app"
    / "data" / "local" / "db" / "StaticDatabase.kt"
)
SCHEMA_DIR = REPO_ROOT / "app" / "schemas" / "com.geoquiz.app.data.local.db.StaticDatabase"
OUTPUT = REPO_ROOT / "app" / "src" / "main" / "assets" / "databases" / "static.db"


def rel(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def static_db_version() -> int:
    m = re.search(r"const\s+val\s+VERSION\s*=\s*(\d+)", STATIC_DATABASE_KT.read_text(encoding="utf-8"))
    if not m:
        raise SystemExit(f"Could not find `const val VERSION = <n>` in {rel(STATIC_DATABASE_KT)}")
    return int(m.group(1))


def load_schema(version: int) -> dict:
    path = SCHEMA_DIR / f"{version}.json"
    if not path.exists():
        raise SystemExit(
            f"{rel(path)} not found. Build the app once (./gradlew assembleDebug) "
            "so Room exports the StaticDatabase schema, then run this script again."
        )
    database = json.loads(path.read_text(encoding="utf-8"))["database"]
    if database["version"] != version:
        raise SystemExit(f"{rel(path)} has version {database['version']}, expected {version}")
    return database


def load_flag_map(path: Path) -> dict[str, list[str]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise SystemExit(f"{rel(path)} must be an object of code -> list of strings")
    for key, values in data.items():
        if not isinstance(values, list) or not all(isinstance(v, str) for v in values):
            raise SystemExit(f"{rel(path)}: {key!r} must map to a list of strings")
    return data


def flag_values(flag_map: dict[str, list[str]], cca3: str, cca2: str) -> list[str]:
    """Look up by cca3, then cca2 (as the old Kotlin seeding did)."""
    if cca3 in flag_map:
        return flag_map[cca3]
    return flag_map.get(cca2, [])


def build_rows() -> dict[str, list[dict]]:
    """Rows per table, keyed by column name, in a deterministic order."""
    countries = sorted(rules.derive_countries(), key=lambda c: c["cca3"])
    flag_colors = load_flag_map(FLAG_COLORS_JSON)
    flag_elements = load_flag_map(FLAG_ELEMENTS_JSON)

    rows: dict[str, list[dict]] = {
        "countries": [],
        "aliases": [],
        "capital_aliases": [],
        "flag_colors": [],
        "flag_elements": [],
    }
    alias_key = lambda a: (a["normalized"], a["alias"])  # noqa: E731

    for c in countries:
        cca3 = c["cca3"]
        rows["countries"].append({
            "cca3": cca3,
            "commonName": c["common"],
            "officialName": c["official"],
            "region": c["region"],
            "subregion": c["subregion"],
            "nameLength": rules.kt_length(c["common"]),
            "capital": c["capitals"][0] if c["capitals"] else "",
        })
        for a in sorted(c["aliases"], key=alias_key):
            rows["aliases"].append({
                "id": len(rows["aliases"]) + 1,
                "countryCca3": cca3,
                "alias": a["alias"],
                "normalizedAlias": a["normalized"],
            })
        for a in sorted(c["capitalAliases"], key=alias_key):
            rows["capital_aliases"].append({
                "id": len(rows["capital_aliases"]) + 1,
                "countryCca3": cca3,
                "alias": a["alias"],
                "normalizedAlias": a["normalized"],
            })
        # Composite primary keys: duplicates in the source collapse to one row.
        for color in sorted(set(flag_values(flag_colors, cca3, c["cca2"]))):
            rows["flag_colors"].append({"countryCca3": cca3, "color": color})
        for element in sorted(set(flag_values(flag_elements, cca3, c["cca2"]))):
            rows["flag_elements"].append({"countryCca3": cca3, "element": element})
    return rows


def write_database(path: Path) -> dict[str, int]:
    version = static_db_version()
    schema = load_schema(version)
    rows = build_rows()

    entities = {e["tableName"]: e for e in schema["entities"]}
    if set(entities) != set(rows):
        raise SystemExit(
            f"Schema tables {sorted(entities)} do not match generated tables {sorted(rows)}. "
            "Update build_rows() in tools/data/build_static_db.py."
        )

    if path.exists():
        path.unlink()
    conn = sqlite3.connect(path)
    try:
        conn.execute("PRAGMA journal_mode = DELETE")
        conn.execute("PRAGMA encoding = 'UTF-8'")
        # Parents before children so the foreign keys would hold if enforced.
        order = ["countries"] + sorted(t for t in entities if t != "countries")
        with conn:
            for table in order:
                entity = entities[table]
                conn.execute(entity["createSql"].replace("${TABLE_NAME}", table))
                for index in entity.get("indices", []):
                    conn.execute(index["createSql"].replace("${TABLE_NAME}", table))
                columns = [f["columnName"] for f in entity["fields"]]
                table_rows = rows[table]
                if table_rows and set(table_rows[0]) != set(columns):
                    raise SystemExit(
                        f"{table}: schema columns {columns} do not match generated "
                        f"columns {sorted(table_rows[0])}"
                    )
                placeholders = ", ".join("?" for _ in columns)
                column_list = ", ".join(f"`{c}`" for c in columns)
                conn.executemany(
                    f"INSERT INTO `{table}` ({column_list}) VALUES ({placeholders})",
                    [tuple(r[c] for c in columns) for r in table_rows],
                )
            for query in schema["setupQueries"]:
                conn.execute(query)
            conn.execute(f"PRAGMA user_version = {int(version)}")
        bad = conn.execute("PRAGMA foreign_key_check").fetchall()
        if bad:
            raise SystemExit(f"Foreign key violations: {bad[:5]}")
        conn.execute("VACUUM")
    finally:
        conn.close()
    return {table: len(r) for table, r in rows.items()}


def dump(path: Path) -> list:
    """Logical content: user_version, schema objects and every row of every table."""
    conn = sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)
    try:
        out: list = [("user_version", conn.execute("PRAGMA user_version").fetchone()[0])]
        objects = conn.execute(
            "SELECT type, name, tbl_name, sql FROM sqlite_master ORDER BY type, name"
        ).fetchall()
        out.append(("schema", objects))
        for (_, name, _, _) in [o for o in objects if o[0] == "table"]:
            table_rows = conn.execute(f"SELECT * FROM `{name}`").fetchall()
            out.append((name, sorted(table_rows, key=repr)))
        return out
    finally:
        conn.close()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not write; exit 1 if the asset is missing or out of date.",
    )
    args = parser.parse_args(argv)

    if args.check:
        if not OUTPUT.exists():
            print(f"{rel(OUTPUT)} is missing. Run: python tools/data/build_static_db.py", file=sys.stderr)
            return 1
        fd, tmp_name = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        tmp = Path(tmp_name)
        try:
            write_database(tmp)
            stale = dump(tmp) != dump(OUTPUT)
        finally:
            tmp.unlink(missing_ok=True)
        if stale:
            print(f"{rel(OUTPUT)} is out of date. Run: python tools/data/build_static_db.py", file=sys.stderr)
            return 1
        print(f"{rel(OUTPUT)} is up to date.")
        return 0

    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    counts = write_database(OUTPUT)
    summary = ", ".join(f"{n} {t}" for t, n in counts.items())
    print(f"Wrote {rel(OUTPUT)} ({OUTPUT.stat().st_size:,} bytes, version {static_db_version()}): {summary}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
