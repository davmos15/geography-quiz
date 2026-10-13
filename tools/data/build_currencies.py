#!/usr/bin/env python3
"""Build data/currencies.json: the currency data for the Currencies mode.

The question shows a currency's name, symbol and code; the player answers the
country. A currency used by more than one app country ("shared") may only be
asked as "which of these uses..." with exactly one valid option among the
choices shown -- this file just exposes the full country list per currency so
the app's question builder can enforce that; it does not build questions.

Two independent, approved sources feed this file:

  * mledoze/countries (ODbL 1.0, data/source/countries.json, `currencies`
    field), restricted to the app's 197 countries (derive_countries() in
    tools/data/export_aliases.py -- imported, not duplicated). This gives
    the ISO 4217 code, name, symbol and which app countries use it.
  * Wikidata (CC0), queried once and pinned as a snapshot
    (data/source/wikidata_currencies.json, query text + retrieval date +
    SHA-256): one short fact per code, generated from a template using
    properties whose meaning is verified (P571 inception, P580 start time,
    P1365 replaces with its P585 point-in-time qualifier, P17 country, P1549
    demonym on the country item -- used only to detect when a fact would
    name the country the player is asked to guess).

mledoze gives no signal for which currency is "primary" when a country lists
more than one; every one of those decisions is reviewed and documented below
(PRIMARY_OVERRIDES), same house style as the OVERRIDES table in
tools/data/build_borders.py: nothing is silently guessed or silently dropped.
Anomalies in the source data (a non-standard code, a symbol mledoze disagrees
with itself about, a country mledoze lists with no currency at all) are kept
in the output and flagged, never silently fixed or removed -- see
QUESTIONABLE_CODES, SYMBOL_OVERRIDES and MISSING_CURRENCY_OVERRIDES.

Usage (from the repository root or anywhere):

    python tools/data/build_currencies.py            # build from the pinned
                                                       # snapshot (offline)
    python tools/data/build_currencies.py --refresh   # re-query Wikidata,
                                                       # overwrite the
                                                       # snapshot, then build
    python tools/data/build_currencies.py --check     # exit 1 if either
                                                       # output file is stale
                                                       # (offline; reads the
                                                       # existing snapshot)

Standard library only.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))
import export_aliases as ea  # noqa: E402  derive_countries(): the 197-country filter

COUNTRIES_JSON = REPO_ROOT / "data" / "source" / "countries.json"
SNAPSHOT_JSON = REPO_ROOT / "data" / "source" / "wikidata_currencies.json"
OUTPUT_JSON = REPO_ROOT / "data" / "currencies.json"
OUTPUT_REPORT = REPO_ROOT / "data" / "currencies_report.md"

VERSION = 1

WIKIDATA_ENDPOINT = "https://query.wikidata.org/sparql"
USER_AGENT = "GeoQuiz-dataprep/1.0 (https://github.com/davmos15/geography-quiz)"

SOURCE_MLEDOZE = {
    "name": "mledoze/countries",
    "url": "https://github.com/mledoze/countries",
    "file": "data/source/countries.json",
    "field": "currencies",
    "licence": "ODbL-1.0",
    "version": "not recorded; see data/SOURCES.md (sha256 9c5b9005e0230a4bc218a9a2b3b9640fe25dc34ad774b2f13018284bba15528b)",
    "role": "primary: ISO 4217 code, name, symbol and which app countries use it",
}

ATTRIBUTION = (
    "Contains information from mledoze/countries "
    "(https://github.com/mledoze/countries), made available under the "
    "Open Database License (ODbL) v1.0."
)


# --------------------------------------------------------------------------
# Reviewed overrides to mledoze's `currencies` field, restricted to the
# app's 197 countries. Every entry here is investigated and documented so
# nothing is silently changed or silently dropped; see
# data/currencies_report.md for the evidence each decision rests on.
# --------------------------------------------------------------------------

# 1. mledoze lists a country with an empty `currencies: {}` object.
MISSING_CURRENCY_OVERRIDES: dict[str, dict] = {
    "FSM": {
        "add": {"USD": {"name": "United States dollar", "symbol": "$"}},
        "reason": (
            "mledoze's FSM entry has an empty currencies object. The Federated "
            "States of Micronesia uses the US dollar under its Compact of Free "
            "Association with the United States, exactly as mledoze's own MHL "
            "(Marshall Islands) and PLW (Palau) entries already record for the "
            "other two compact-of-free-association states; USD is already a "
            "valid code in this dataset for those countries. No new source is "
            "used here -- this fills in the one country mledoze left blank with "
            "a code it already lists for its closest comparators."
        ),
    },
}

# 2. Within one ISO 4217 code, mledoze gives more than one `symbol` across the
# countries that use it. The symbol kept is the one given by the currency's
# own eponymous/originating app country; the other countries' variant is
# recorded here (and in the report) rather than discarded.
SYMBOL_OVERRIDES: dict[str, dict] = {
    "CHF": {
        "symbol": "Fr.",
        "from": "CHE",
        "variants": {"LIE": "Fr"},
        "reason": "mledoze gives 'Fr.' for Switzerland (CHE) and 'Fr' (no full "
                  "stop) for Liechtenstein (LIE); kept Switzerland's as the "
                  "franc's originating country.",
    },
    "EGP": {
        "symbol": "£",
        "from": "EGY",
        "variants": {"PSE": "E£"},
        "reason": "mledoze gives plain '£' for Egypt (EGY, matching how it also "
                  "writes GBP) and 'E£' for the Palestinian territories' "
                  "secondary use (PSE); kept Egypt's own entry.",
    },
    "JOD": {
        "symbol": "د.ا",
        "from": "JOR",
        "variants": {"PSE": "JD"},
        "reason": "mledoze gives the Arabic symbol for Jordan (JOR, consistent "
                  "with how it writes several other non-Latin-script "
                  "currencies in this dataset) and the Latin abbreviation 'JD' "
                  "for the Palestinian territories' secondary use (PSE); kept "
                  "Jordan's own entry.",
    },
}

# 3. Primary currency per multi-currency app country. mledoze's `currencies`
# field doesn't mark one; each decision is reviewed and reasoned below (also
# in data/currencies_report.md). `None` means no safe pick was found --
# reported, not guessed, per the task brief's explicit "or report that
# mledoze doesn't distinguish this" option.
PRIMARY_OVERRIDES: dict[str, str | None] = {
    "TUV": "AUD",
    "KIR": "AUD",
    "KHM": "KHR",
    "BRN": "BND",
    "PAN": "PAB",
    "SWZ": "SZL",
    "NAM": "NAD",
    "BTN": "BTN",
    "LSO": "LSL",
    "BHS": "BSD",
    "CUB": "CUP",
    "ZWE": "USD",
    "PSE": None,
}

PRIMARY_OVERRIDE_NOTES: dict[str, str] = {
    "TUV": "Tuvalu issues no banknotes; the Tuvaluan dollar (TVD) exists only "
           "as coins pegged 1:1 to, and used alongside, the Australian dollar, "
           "which is the currency actually used in everyday transactions.",
    "KIR": "Same arrangement as Tuvalu: the Kiribati dollar (KID) is coins "
           "only, pegged 1:1 to and co-circulating with AUD.",
    "KHM": "The riel is Cambodia's own, central-bank-issued currency; the US "
           "dollar circulates widely alongside it, but KHR remains the "
           "official legal tender.",
    "BRN": "The Brunei dollar is Brunei's own currency. The Brunei-Singapore "
           "Currency Interchangeability Agreement (1967) makes SGD "
           "interchangeable and also legal tender, but BND is Brunei's own.",
    "PAN": "The balboa is Panama's own currency by name and the one normally "
           "given as the answer, but Panama mints only balboa coins and uses "
           "US dollar banknotes for paper money, so USD is the currency "
           "actually seen day to day -- flagged as uncertain.",
    "SWZ": "The lilangeni is Eswatini's own currency, pegged 1:1 to and "
           "co-circulating with the South African rand.",
    "NAM": "Same arrangement as Eswatini: the Namibian dollar is pegged 1:1 "
           "to and co-circulates with the South African rand.",
    "BTN": "The ngultrum is Bhutan's own currency (its ISO 4217 code happens "
           "to be the same three letters as Bhutan's cca3, BTN), pegged 1:1 "
           "to and co-circulating with the Indian rupee.",
    "LSO": "Same arrangement as Eswatini and Namibia: the loti is pegged 1:1 "
           "to and co-circulates with the South African rand.",
    "BHS": "The Bahamian dollar is pegged 1:1 to, and co-circulates with, the "
           "US dollar.",
    "CUB": "Cuba abolished the convertible peso (CUC) on 1 January 2021, "
           "unifying to the Cuban peso (CUP); mledoze's snapshot still lists "
           "both for CUB. CUP is chosen as primary; CUC is kept in the "
           "output, marked questionable/obsolete (see QUESTIONABLE_CODES), "
           "not dropped.",
    "ZWE": "Of the multi-currency basket mledoze lists (legalised 2009-2019 "
           "and still widely used in practice), the US dollar has been the "
           "dominant transaction currency for most of that period; no "
           "Zimbabwean-dollar code (ZWL/ZWG) appears in this mledoze "
           "snapshot to prefer instead. Low confidence -- flagged.",
    "PSE": "No official currency; usage differs by area (ILS and JOD mainly "
           "in the West Bank, EGP mainly in Gaza) and mledoze's data gives no "
           "signal either way. Reported, not guessed: primaryCurrency is "
           "null for PSE.",
}

# 4. Individual currency codes flagged as questionable. Kept in the output
# exactly as mledoze gives them -- never silently dropped or silently
# "fixed" -- with a note here and in the report.
QUESTIONABLE_CODES: dict[str, str] = {
    "CUC": "Cuba abolished the convertible peso on 1 January 2021; mledoze's "
           "snapshot of data/source/countries.json still lists it for CUB "
           "alongside CUP. Kept, not dropped; CUP is primary for CUB (see "
           "PRIMARY_OVERRIDES).",
    "SLL": "Sierra Leone redenominated its currency on 1 July 2022 (1,000 "
           "old leones = 1 new leone, new ISO code SLE, coincidentally the "
           "same three letters as Sierra Leone's cca3); mledoze's snapshot "
           "still lists only the pre-2022 SLL for SLE, and the Wikidata "
           "snapshot has no P498=SLL match either. Kept as mledoze gives it "
           "-- this pipeline only uses currencies mledoze actually lists, "
           "and does not add SLE since mledoze doesn't; accepted by the owner (2026-10-13).",
}

# Entries double-checked against the brief's list of examples and found to
# already be current in mledoze -- no override needed, listed here (and in
# the report) so the check is visible, not just absent.
VERIFIED_CURRENT: dict[str, str] = {
    "HRV": "Croatia: mledoze already lists only EUR (adopted the euro on "
           "1 January 2023); no override needed.",
    "VEN": "Venezuela: mledoze already lists only VES (the bolívar soberano, "
           "current since 2018; no VEF); no override needed.",
}

# 5. Wikidata gave more than one exact P498 match for these codes. Picked by
# inspecting the candidates' own labels (see data/currencies_report.md); the
# rejected match in each case is clearly not the currency itself.
QID_DISAMBIGUATION: dict[str, str] = {
    "GBP": "http://www.wikidata.org/entity/Q25224",   # "pound sterling"; the
           # other match, Q323138, is "economy of the United Kingdom".
    "INR": "http://www.wikidata.org/entity/Q80524",   # "Indian rupee"; the
           # other match, Q114975111, is "Digital Rupee" (the central bank
           # digital currency pilot) -- a different thing with the same
           # P498 value.
    "SYP": "http://www.wikidata.org/entity/Q240468",  # "Syrian pound"; the
           # other match, Q84573291, is "list of Syrian coins".
}

# Well-known shared-currency unions, named per the task brief. Any other
# code used by two or more app countries gets an automatic slug of its
# mledoze name instead (see group_label()); both kinds are listed in
# `groups` and in the report.
WELL_KNOWN_GROUPS: dict[str, str] = {
    "EUR": "euro",
    "USD": "us-dollar",
    "XOF": "cfa-franc-west",
    "XAF": "cfa-franc-central",
    "XCD": "east-caribbean-dollar",
}


# --------------------------------------------------------------------------
# mledoze: per-country currencies, restricted to the app's 197 countries
# --------------------------------------------------------------------------

def load_country_currencies() -> tuple[dict[str, dict], dict[str, str]]:
    """Return (cca3 -> {code: {"name", "symbol"}}, notes about overrides applied)."""
    app_set = {c["cca3"] for c in ea.derive_countries()}
    raw = json.loads(COUNTRIES_JSON.read_text(encoding="utf-8"))
    by_cca3 = {c["cca3"]: c for c in raw}

    result: dict[str, dict] = {}
    notes: dict[str, str] = {}
    for cca3 in sorted(app_set):
        entry = by_cca3.get(cca3)
        if entry is None:
            raise SystemExit(f"app country {cca3} is missing from {COUNTRIES_JSON}")
        currencies = entry.get("currencies") or {}
        if not currencies:
            override = MISSING_CURRENCY_OVERRIDES.get(cca3)
            if override is None:
                raise SystemExit(
                    f"{cca3} has no currencies in mledoze and no entry in "
                    "MISSING_CURRENCY_OVERRIDES -- investigate and add one."
                )
            currencies = override["add"]
            notes[cca3] = override["reason"]
        result[cca3] = currencies

    stale = set(MISSING_CURRENCY_OVERRIDES) - set(notes)
    if stale:
        raise SystemExit(
            f"MISSING_CURRENCY_OVERRIDES entries no longer needed (mledoze now "
            f"lists a currency): {sorted(stale)}. Remove them."
        )
    return result, notes


# 6. Countries whose mledoze entry is a basket of foreign currencies accepted
# locally rather than currencies the country issues or has adopted. Only the
# primary currency counts as "used by" the country; the rest are recorded in
# `listedInBasketBy` so that, for example, "which of these uses the Japanese
# yen?" never has Zimbabwe as a second valid answer. A code used only by a
# basket country is left out of `currencies` and listed in the report (ZWB,
# "Zimbabwean bonds", is not a registered ISO 4217 code and has no Wikidata
# P498 match either).
BASKET_COUNTRIES: dict[str, str] = {
    "ZWE": "USD",  # 2009-2019 multi-currency system; see PRIMARY_OVERRIDE_NOTES
}

# 7. Lead review of every generated fact (2026-10-13). Wikidata often dates the
# currency's name rather than the current ISO code, or links an older or
# unrelated "replaces" item. "reject" drops the fact; "year_only" keeps the
# year and drops the "replacing the ..." clause. A clause is also dropped
# automatically when the replaced currency has the same name (a
# redenomination, e.g. "Mexican peso replacing the Mexican peso").
FACT_REVIEW: dict[str, tuple[str, str]] = {
    "AFN": ("reject", "the afghani dates from 1925, not 1923"),
    "BGN": ("reject", "1880 is the first lev; the BGN lev dates from 1999"),
    "CNY": ("reject", "the renminbi was first issued in 1948 and did not replace the Mengjiang yuan alone"),
    "CUC": ("reject", "the convertible peso dates from 1994 and was withdrawn in 2021"),
    "DJF": ("reject", "1884 predates the Djiboutian franc (1949)"),
    "GBP": ("reject", "1694 is the founding of the Bank of England, not the pound"),
    "IDR": ("reject", "the rupiah dates from 1946-1949; 1952 is not its introduction"),
    "IQD": ("reject", "the Iraqi dinar replaced the Indian rupee in 1932, not 1972"),
    "KES": ("reject", "the Kenyan shilling dates from 1966"),
    "KRW": ("reject", "the current won replaced the hwan in 1962, not 1947"),
    "MAD": ("reject", "1882 is the Moroccan rial; the dirham dates from 1960"),
    "MGA": ("reject", "the ariary's introduction date is disputed (1961 unit, 2005 sole currency)"),
    "MRU": ("reject", "1973 is the first ouguiya; MRU dates from 2018"),
    "PYG": ("reject", "the guarani dates from 1943"),
    "SDG": ("reject", "the 1956 pound replaced the Egyptian pound; SDG replaced the dinar in 2007"),
    "SEK": ("reject", "the krona dates from 1873"),
    "SOS": ("reject", "the Somali shilling dates from 1962"),
    "STN": ("reject", "the dobra replaced the escudo in 1977; STN is the 2018 redenomination"),
    "THB": ("reject", "1400 is not a meaningful introduction date for the baht"),
    "XAF": ("reject", "the Central African CFA franc did not replace the British West African pound"),
    "BTN": ("year_only", "the ngultrum replaced the Indian rupee at par, not a 'Bhutanese rupee'"),
    "CHF": ("year_only", "the franc replaced many cantonal currencies, not only the Zurich thaler"),
    "DKK": ("year_only", "the krone replaced the rigsdaler"),
    "DOP": ("year_only", "the 1947 peso replaced the US dollar in circulation"),
    "ERN": ("year_only", "the nakfa replaced the Ethiopian birr; Wikidata's label is ambiguous"),
    "GNF": ("year_only", "the syli came after the 1959 franc, not before it"),
    "NOK": ("year_only", "the krone replaced the speciedaler"),
    "TMT": ("year_only", "Wikidata's label for the old manat is misspelt"),
}


def same_name(currency_name: str, replaced_label: str) -> bool:
    """True when the replaced currency has the same base name (a redenomination)."""
    last = currency_name.lower().split()[-1]
    return last in replaced_label.lower().split()


def build_code_index(country_currencies: dict[str, dict]) -> dict[str, dict]:
    """code -> {"name", "symbol", "countries": [cca3,...], "symbol_variants": {...}}."""
    codes: dict[str, dict] = {}
    for cca3, currencies in country_currencies.items():
        for code, info in currencies.items():
            entry = codes.setdefault(code, {
                "names": {},
                "symbols": {},
                "countries": set(),
                "basket": set(),
            })
            if cca3 in BASKET_COUNTRIES and code != BASKET_COUNTRIES[cca3]:
                entry["basket"].add(cca3)
                continue
            entry["countries"].add(cca3)
            entry["names"][cca3] = info.get("name")
            entry["symbols"][cca3] = info.get("symbol")
    return codes


def resolve_code_entries(codes: dict[str, dict]) -> tuple[dict[str, dict], list[str]]:
    """Resolve one name/symbol per code; collect name-mismatch problems (fatal if any)."""
    resolved: dict[str, dict] = {}
    name_problems: list[str] = []
    for code, entry in codes.items():
        if not entry["countries"]:
            continue  # only listed by a basket country; reported, not output
        names = {entry["names"][c] for c in entry["countries"]}
        if len(names) > 1:
            name_problems.append(f"{code}: mledoze gives different names across "
                                  f"countries: {entry['names']}")
            name = sorted(names)[0]
        else:
            name = names.pop()

        symbols = {c: entry["symbols"][c] for c in entry["countries"]}
        distinct_symbols = set(symbols.values())
        questionable = None
        note = None
        if len(distinct_symbols) > 1:
            override = SYMBOL_OVERRIDES.get(code)
            if override is None:
                name_problems.append(
                    f"{code}: mledoze gives different symbols across countries "
                    f"with no reviewed SYMBOL_OVERRIDES entry: {symbols}"
                )
                symbol = sorted(distinct_symbols)[0]
            else:
                symbol = override["symbol"]
                questionable = True
                note = (f"mledoze gives more than one symbol for {code} "
                         f"({dict(sorted(symbols.items()))}); using {symbol!r} "
                         f"from {override['from']}. {override['reason']}")
        else:
            symbol = distinct_symbols.pop()

        if code in QUESTIONABLE_CODES:
            questionable = True
            note = QUESTIONABLE_CODES[code] if note is None else note + " " + QUESTIONABLE_CODES[code]

        resolved[code] = {
            "code": code,
            "name": name,
            "symbol": symbol,
            "countries": sorted(entry["countries"]),
            "shared": len(entry["countries"]) > 1,
            "listedInBasketBy": sorted(entry["basket"]),
            "questionable": bool(questionable),
            "note": note,
        }
    return resolved, name_problems


def group_label(name: str) -> str:
    slug = []
    for ch in name.lower():
        if ch.isalnum():
            slug.append(ch)
        elif slug and slug[-1] != "-":
            slug.append("-")
    return "".join(slug).strip("-")


def build_groups(resolved: dict[str, dict]) -> list[dict]:
    groups = []
    for code, entry in sorted(resolved.items()):
        if not entry["shared"]:
            continue
        label = WELL_KNOWN_GROUPS.get(code) or group_label(entry["name"])
        groups.append({
            "group": label,
            "code": code,
            "countries": entry["countries"],
            "count": len(entry["countries"]),
        })
    return groups


def build_primary_currency(country_currencies: dict[str, dict]) -> dict[str, str | None]:
    primary: dict[str, str | None] = {}
    for cca3, currencies in sorted(country_currencies.items()):
        codes = sorted(currencies)
        if len(codes) == 1:
            primary[cca3] = codes[0]
            continue
        if cca3 not in PRIMARY_OVERRIDES:
            raise SystemExit(
                f"{cca3} has {len(codes)} currencies ({codes}) and no entry in "
                "PRIMARY_OVERRIDES -- investigate and add one."
            )
        chosen = PRIMARY_OVERRIDES[cca3]
        if chosen is not None and chosen not in codes:
            raise SystemExit(
                f"PRIMARY_OVERRIDES[{cca3!r}] = {chosen!r} is not one of mledoze's "
                f"codes for that country: {codes}"
            )
        primary[cca3] = chosen

    stale = set(PRIMARY_OVERRIDES) - {c for c, v in country_currencies.items() if len(v) > 1}
    if stale:
        raise SystemExit(
            f"PRIMARY_OVERRIDES entries for countries that no longer have "
            f"multiple currencies: {sorted(stale)}. Remove them."
        )
    return primary


# --------------------------------------------------------------------------
# Wikidata: snapshot fetch (--refresh only) and offline load
# --------------------------------------------------------------------------

MAIN_QUERY_TEMPLATE = """SELECT ?code ?item ?itemLabel ?inception ?start ?replaces ?replacesLabel ?replacesDate ?country ?symbol WHERE {{
  VALUES ?code {{ {values} }}
  ?item wdt:P498 ?code .
  OPTIONAL {{ ?item wdt:P571 ?inception }}
  OPTIONAL {{ ?item wdt:P580 ?start }}
  OPTIONAL {{
    ?item p:P1365 ?repStatement .
    ?repStatement ps:P1365 ?replaces .
    OPTIONAL {{ ?repStatement pq:P585 ?replacesDate }}
  }}
  OPTIONAL {{ ?item wdt:P17 ?country }}
  OPTIONAL {{ ?item wdt:P5061 ?symbol }}
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en,mul". }}
}}"""

DEMONYM_QUERY_TEMPLATE = """SELECT ?country ?countryLabel ?demonym WHERE {{
  VALUES ?country {{ {values} }}
  OPTIONAL {{ ?country wdt:P1549 ?demonym . FILTER(LANG(?demonym) = "en") }}
  SERVICE wikibase:label {{ bd:serviceParam wikibase:language "en,mul". }}
}}"""


def sparql(query: str) -> dict:
    url = WIKIDATA_ENDPOINT + "?" + urllib.parse.urlencode({"query": query, "format": "json"})
    req = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT, "Accept": "application/sparql-results+json"},
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def snapshot_hash(main: dict, demonyms: dict) -> str:
    canonical = json.dumps({"main": main, "demonyms": demonyms}, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def refresh_snapshot(codes: list[str]) -> dict:
    values = " ".join(f'"{c}"' for c in sorted(codes))
    main_query = MAIN_QUERY_TEMPLATE.format(values=values)
    print(f"Querying Wikidata for {len(codes)} currency codes...", file=sys.stderr)
    main = sparql(main_query)

    country_qids = sorted({
        b["country"]["value"].split("/")[-1]
        for b in main["results"]["bindings"]
        if "country" in b
    })
    demonym_values = " ".join(f"wd:{q}" for q in country_qids)
    demonym_query = DEMONYM_QUERY_TEMPLATE.format(values=demonym_values)
    print(f"Querying Wikidata for {len(country_qids)} country demonyms...", file=sys.stderr)
    demonyms = sparql(demonym_query)

    snapshot = {
        "retrievedAt": dt.datetime.now(dt.timezone.utc).date().isoformat(),
        "endpoint": WIKIDATA_ENDPOINT,
        "userAgent": USER_AGENT,
        "mainQuery": main_query,
        "demonymQuery": demonym_query,
        "codes": sorted(codes),
        "main": main,
        "demonyms": demonyms,
    }
    snapshot["sha256"] = snapshot_hash(main, demonyms)
    return snapshot


def load_snapshot() -> dict:
    if not SNAPSHOT_JSON.exists():
        raise SystemExit(
            f"{SNAPSHOT_JSON.relative_to(REPO_ROOT).as_posix()} is missing. Run "
            "with --refresh once (needs network access) to create it."
        )
    snapshot = json.loads(SNAPSHOT_JSON.read_text(encoding="utf-8"))
    expected = snapshot_hash(snapshot["main"], snapshot["demonyms"])
    if expected != snapshot.get("sha256"):
        raise SystemExit(
            f"{SNAPSHOT_JSON.relative_to(REPO_ROOT).as_posix()} failed its SHA-256 "
            f"check (expected {expected}, file says {snapshot.get('sha256')}). It "
            "may be corrupt or hand-edited; re-run with --refresh."
        )
    return snapshot


# --------------------------------------------------------------------------
# Facts: derive one short fact per code from the snapshot
# --------------------------------------------------------------------------

def parse_year(wd_time: str) -> int:
    return int(wd_time.lstrip("+-")[:4])


def build_demonym_index(demonyms: dict) -> dict[str, dict]:
    index: dict[str, dict] = {}
    for b in demonyms["results"]["bindings"]:
        qid = b["country"]["value"].split("/")[-1]
        label = b.get("countryLabel", {}).get("value")
        demonym = b.get("demonym", {}).get("value")
        entry = index.setdefault(qid, {"label": label, "demonyms": set()})
        if demonym:
            entry["demonyms"].add(demonym)
    return index


def build_fact_rows(main: dict) -> dict[str, list[dict]]:
    by_code: dict[str, list[dict]] = defaultdict(list)
    for row in main["results"]["bindings"]:
        by_code[row["code"]["value"]].append(row)
    return by_code


def choose_item(code: str, rows: list[dict]) -> tuple[str | None, str]:
    """Return (item_uri or None, status) where status is one of
    "unmatched", "unique", "disambiguated", "unresolved-multiple"."""
    items = {r["item"]["value"] for r in rows}
    if not items:
        return None, "unmatched"
    if len(items) == 1:
        return items.pop(), "unique"
    chosen = QID_DISAMBIGUATION.get(code)
    if chosen is not None and chosen in items:
        return chosen, "disambiguated"
    return None, "unresolved-multiple"


def generate_fact(code: str, name: str, rows: list[dict], item: str, demonym_index: dict) -> dict:
    item_rows = [r for r in rows if r["item"]["value"] == item]
    qid = item.rsplit("/", 1)[-1]

    inception = next((r["inception"]["value"] for r in item_rows if "inception" in r), None)
    start = next((r["start"]["value"] for r in item_rows if "start" in r), None)
    year = parse_year(inception) if inception else (parse_year(start) if start else None)
    year_property = "P571" if inception else ("P580" if start else None)

    replaces: dict[str, str] = {}
    for r in item_rows:
        if "replaces" in r:
            replaces[r["replaces"]["value"]] = r.get("replacesLabel", {}).get("value", "")
    replaces_label = next(iter(replaces.values())) if len(replaces) == 1 else None
    review = FACT_REVIEW.get(code, ("ok", ""))[0]
    if review == "reject":
        return {"fact": None, "fact_source": None, "fact_reveals_country": False}
    if replaces_label is not None and (review == "year_only" or same_name(name, replaces_label)):
        replaces_label = None

    properties = []
    if year_property:
        properties.append(year_property)
    if replaces_label is not None:
        properties.append("P1365")

    if year is not None and replaces_label:
        fact = f"Introduced in {year}, replacing the {replaces_label}."
    elif year is not None:
        fact = f"Introduced in {year}."
    elif replaces_label:
        fact = f"Replaced the {replaces_label}."
    else:
        fact = None

    reveals_country = False
    if fact:
        fact_lower = fact.lower()
        country_qids = {r["country"]["value"].split("/")[-1] for r in item_rows if "country" in r}
        for cq in country_qids:
            info = demonym_index.get(cq)
            if not info:
                continue
            terms = set(info["demonyms"])
            if info["label"]:
                terms.add(info["label"])
            for term in terms:
                if term and term.lower() in fact_lower:
                    reveals_country = True
                    break
            if reveals_country:
                break

    return {
        "fact": fact,
        "fact_source": {"wikidata": qid, "properties": properties} if fact else None,
        "fact_reveals_country": reveals_country,
    }


def build_facts(codes: list[str], names: dict[str, str], snapshot: dict) -> tuple[dict[str, dict], dict]:
    by_code = build_fact_rows(snapshot["main"])
    demonym_index = build_demonym_index(snapshot["demonyms"])

    facts: dict[str, dict] = {}
    report = {"unmatched": [], "unresolved_multiple": [], "disambiguated": [], "no_fact": [],
              "rejected": sorted(c for c, (v, _) in FACT_REVIEW.items() if v == "reject")}
    stale = set(FACT_REVIEW) - set(codes)
    if stale:
        raise SystemExit(f"FACT_REVIEW entries for codes no longer present: {sorted(stale)}")
    for code in codes:
        rows = by_code.get(code, [])
        item, status = choose_item(code, rows)
        if status == "unmatched":
            facts[code] = {"fact": None, "fact_source": None, "fact_reveals_country": False}
            report["unmatched"].append(code)
            continue
        if status == "unresolved-multiple":
            items = sorted({r["item"]["value"] for r in rows})
            facts[code] = {"fact": None, "fact_source": None, "fact_reveals_country": False}
            report["unresolved_multiple"].append((code, items))
            continue
        if status == "disambiguated":
            items = sorted({r["item"]["value"] for r in rows})
            report["disambiguated"].append((code, item, items))

        result = generate_fact(code, names[code], rows, item, demonym_index)
        facts[code] = result
        if result["fact"] is None and code not in report["rejected"]:
            report["no_fact"].append(code)
    return facts, report


# --------------------------------------------------------------------------
# Build
# --------------------------------------------------------------------------

def build(snapshot: dict):
    country_currencies, missing_notes = load_country_currencies()
    codes_index = build_code_index(country_currencies)
    resolved, name_problems = resolve_code_entries(codes_index)
    if name_problems:
        raise SystemExit("Currency data problems:\n  " + "\n  ".join(name_problems))

    primary_currency = build_primary_currency(country_currencies)
    groups = build_groups(resolved)
    facts, fact_report = build_facts(sorted(resolved), {c: e["name"] for c, e in resolved.items()}, snapshot)

    currencies = []
    for code in sorted(resolved):
        entry = dict(resolved[code])
        entry.update(facts[code])
        if not entry["listedInBasketBy"]:
            del entry["listedInBasketBy"]
        if not entry["questionable"]:
            del entry["questionable"]
            del entry["note"]
        else:
            if entry["note"] is None:
                del entry["note"]
        currencies.append(entry)

    multi_currency_countries = sorted(c for c, v in country_currencies.items() if len(v) > 1)
    stats = {
        "countryCount": len(country_currencies),
        "codeCount": len(resolved),
        "sharedCodeCount": sum(1 for e in resolved.values() if e["shared"]),
        "questionableCodeCount": sum(1 for e in resolved.values() if e["questionable"]),
        "multiCurrencyCountryCount": len(multi_currency_countries),
        "primaryUnresolvedCountryCount": sum(1 for v in primary_currency.values() if v is None),
        "factCount": sum(1 for f in facts.values() if f["fact"]),
        "noFactCount": sum(1 for f in facts.values() if not f["fact"]),
        "factRevealsCountryCount": sum(1 for f in facts.values() if f["fact_reveals_country"]),
    }

    data = {
        "version": VERSION,
        "licence": "ODbL-1.0",
        "licenceUrl": "https://opendatacommons.org/licenses/odbl/1-0/",
        "attribution": ATTRIBUTION,
        "sources": [
            SOURCE_MLEDOZE,
            {
                "name": "Wikidata",
                "url": "https://www.wikidata.org/",
                "file": "data/source/wikidata_currencies.json",
                "licence": "CC0-1.0",
                "role": "one short fact per currency code, from a pinned "
                        f"snapshot retrieved {snapshot['retrievedAt']}",
                "sha256": snapshot["sha256"],
            },
        ],
        "currencies": currencies,
        "primaryCurrency": primary_currency,
        "primaryCurrencyNotes": PRIMARY_OVERRIDE_NOTES,
        "groups": groups,
        "stats": stats,
    }

    context = {
        "basket_only": {
            b: sorted(c for c in country_currencies[b] if c not in resolved)
            for b in BASKET_COUNTRIES
        },
        "missing_notes": missing_notes,
        "multi_currency_countries": multi_currency_countries,
        "fact_report": fact_report,
        "resolved": resolved,
        "primary_currency": primary_currency,
    }
    return data, context


def render_json(data: dict) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def data_codes_basket(data: dict) -> dict[str, list[str]]:
    return {c["code"]: c["listedInBasketBy"] for c in data["currencies"] if "listedInBasketBy" in c}


def render_report(data: dict, ctx: dict) -> str:
    lines: list[str] = []
    add = lines.append

    add("# Currencies mode data build report")
    add("")
    add("Generated by `tools/data/build_currencies.py`. Do not edit by hand; rerun")
    add("the script (with `--refresh` to re-query Wikidata) after a source refresh.")
    add("")

    add("## Shared currencies (\"which of these uses...\" only)")
    add("")
    add("| Group | Code | Countries | Count |")
    add("|---|---|---|---|")
    for g in data["groups"]:
        add(f"| {g['group']} | {g['code']} | {', '.join(g['countries'])} | {g['count']} |")
    add("")

    add("## Countries with more than one currency")
    add("")
    add("mledoze marks no primary; each decision below is reviewed (see")
    add("PRIMARY_OVERRIDES and PRIMARY_OVERRIDE_NOTES in the script).")
    add("")
    add("| Country | mledoze codes | Primary chosen | Reasoning |")
    add("|---|---|---|---|")
    for cca3 in ctx["multi_currency_countries"]:
        codes = sorted(c for c, e in ctx["resolved"].items() if cca3 in e["countries"])
        primary = ctx["primary_currency"].get(cca3)
        note = PRIMARY_OVERRIDE_NOTES.get(cca3, "")
        add(f"| {cca3} | {', '.join(codes)} | {primary if primary else '(none -- unresolved)'} | {note} |")
    add("")

    add("## Overrides applied for a country mledoze lists with no currency")
    add("")
    if ctx["missing_notes"]:
        for cca3, note in sorted(ctx["missing_notes"].items()):
            add(f"- **{cca3}**: {note}")
    else:
        add("None.")
    add("")

    add("## Questionable codes")
    add("")
    add("Kept in the output exactly as mledoze gives them; never dropped or")
    add("silently fixed.")
    add("")
    for code in sorted(QUESTIONABLE_CODES):
        add(f"- **{code}**: {QUESTIONABLE_CODES[code]}")
    add("")
    add("### Symbol disagreements (resolved, documented)")
    add("")
    for code, override in sorted(SYMBOL_OVERRIDES.items()):
        add(f"- **{code}**: {override['reason']}")
    add("")
    add("### Checked against the brief's examples and found already current")
    add("")
    for cca3, note in sorted(VERIFIED_CURRENT.items()):
        add(f"- {note}")
    add("")

    add("## Wikidata QID matches")
    add("")
    fr = ctx["fact_report"]
    add(f"{len(data['currencies'])} codes looked up; "
        f"{len(fr['unmatched'])} with zero matches, "
        f"{len(fr['disambiguated'])} with more than one match (resolved manually), "
        f"{len(fr['unresolved_multiple'])} with more than one match left unresolved.")
    add("")
    add("### Zero matches (fact: null)")
    add("")
    add(", ".join(fr["unmatched"]) if fr["unmatched"] else "None.")
    add("")
    add("### Multiple matches, disambiguated manually (see QID_DISAMBIGUATION)")
    add("")
    if fr["disambiguated"]:
        for code, chosen, items in fr["disambiguated"]:
            add(f"- **{code}**: chose `{chosen}`; all candidates: {', '.join(items)}")
    else:
        add("None.")
    add("")
    add("### Multiple matches, unresolved (fact: null; needs a QID_DISAMBIGUATION entry)")
    add("")
    if fr["unresolved_multiple"]:
        for code, items in fr["unresolved_multiple"]:
            add(f"- **{code}**: {', '.join(items)}")
    else:
        add("None.")
    add("")

    add("## Facts with no usable statement (fact: null)")
    add("")
    no_fact_matched = sorted(set(fr["no_fact"]) - set(fr["unmatched"]))
    add(f"{len(no_fact_matched)} matched currencies had no usable inception/start or "
        "single-predecessor statement to build a fact from:")
    add("")
    add(", ".join(no_fact_matched) if no_fact_matched else "None.")
    add("")

    add("## Fact review (FACT_REVIEW)")
    add("")
    add("Every generated fact was reviewed. Rejected facts are null in the output; "
        "\"year only\" facts keep the year and drop the predecessor. Predecessors with "
        "the same name (redenominations) are dropped automatically.")
    add("")
    add("| Code | Decision | Reason |")
    add("|---|---|---|")
    for code, (verdict, reason) in sorted(FACT_REVIEW.items()):
        add(f"| {code} | {verdict.replace('_', ' ')} | {reason} |")
    add("")

    add("## Basket countries (BASKET_COUNTRIES)")
    add("")
    add("Only the primary currency counts as used by these countries; the others are "
        "recorded in `listedInBasketBy`. Codes listed only by a basket country are left out.")
    add("")
    for cca3, code in sorted(BASKET_COUNTRIES.items()):
        others = sorted(c for c, e in data_codes_basket(data).items() if cca3 in e)
        only = sorted(ctx["basket_only"].get(cca3, []))
        add(f"- {cca3}: counts as using {code}; other mledoze codes kept for their own countries: "
            f"{', '.join(others) or 'none'}; codes left out because only {cca3} lists them: "
            f"{', '.join(only) or 'none'}")
    add("")

    add("## Stats")
    add("")
    for k, v in sorted(data["stats"].items()):
        add(f"- `{k}`: {v}")
    add("")

    return "\n".join(lines) + "\n"


# --------------------------------------------------------------------------
# CLI
# --------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="Re-query Wikidata and overwrite data/source/wikidata_currencies.json "
             "before building (needs network access).",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="Do not write; exit 1 if data/currencies.json or "
             "data/currencies_report.md is stale. Offline: reads the existing "
             "snapshot, never --refresh.",
    )
    args = parser.parse_args(argv)
    if args.refresh and args.check:
        parser.error("--refresh and --check cannot be used together")

    if args.refresh:
        country_currencies, _ = load_country_currencies()
        codes_index = build_code_index(country_currencies)
        snapshot = refresh_snapshot(sorted(codes_index))
        SNAPSHOT_JSON.parent.mkdir(parents=True, exist_ok=True)
        SNAPSHOT_JSON.write_bytes(
            (json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")
        )
        print(f"Wrote {SNAPSHOT_JSON.relative_to(REPO_ROOT).as_posix()} "
              f"({len(snapshot['codes'])} codes, sha256 {snapshot['sha256']}).")

    snapshot = load_snapshot()
    data, ctx = build(snapshot)
    json_text = render_json(data)
    report_text = render_report(data, ctx)

    if args.check:
        stale = []
        for path, text in ((OUTPUT_JSON, json_text), (OUTPUT_REPORT, report_text)):
            current = path.read_bytes().decode("utf-8").replace("\r\n", "\n") if path.exists() else None
            if current != text:
                stale.append(path.relative_to(REPO_ROOT).as_posix())
        if stale:
            print(
                "Out of date: " + ", ".join(stale) + "\nRun: python tools/data/build_currencies.py",
                file=sys.stderr,
            )
            return 1
        print("data/currencies.json and data/currencies_report.md are up to date.")
        return 0

    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_JSON.write_bytes(json_text.encode("utf-8"))
    OUTPUT_REPORT.write_bytes(report_text.encode("utf-8"))
    print(
        f"Wrote {OUTPUT_JSON.relative_to(REPO_ROOT).as_posix()} "
        f"({data['stats']['codeCount']} currency codes, "
        f"{data['stats']['countryCount']} countries, "
        f"{data['stats']['factCount']} facts) and "
        f"{OUTPUT_REPORT.relative_to(REPO_ROOT).as_posix()}."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
