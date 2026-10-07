# GeoQuiz data exports

## `aliases.json`

`aliases.json` is the table of accepted answers GeoQuiz uses to recognise country
and capital names typed by players. It contains exactly the rows of the
`aliases` and `capital_aliases` tables in the database the app ships
(`app/src/main/assets/databases/static.db`, built by
`tools/data/build_static_db.py` with the same rules).

It is published here, separately from the app, because it is a Derivative
Database of [mledoze/countries](https://github.com/mledoze/countries), which is
made available under the Open Database License (ODbL) v1.0.

### Attribution

Contains information from mledoze/countries (https://github.com/mledoze/countries), made available under the Open Database License (ODbL) v1.0.

### Licence

`data/aliases.json` is itself made available under the
[Open Database License (ODbL) v1.0](https://opendatacommons.org/licenses/odbl/1-0/).
The full licence text is in [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt). If you use
or adapt this table publicly, keep the attribution above and offer your adapted
database under the ODbL as well.

The ODbL covers the database (the alias table). The GeoQuiz app code is not
covered by this licence, and flag images are not part of this export.

### Fields

Top level:

| Field | Meaning |
| --- | --- |
| `licence`, `licenceUrl` | Licence of this file (`ODbL-1.0`). |
| `attribution` | Required attribution text for the upstream source. |
| `source` | Upstream database: name, URL, licence and the path of the copy kept in this repository (`data/source/countries.json`). |
| `generatedBy` | Script that produced the file. |
| `rulesFrom` | The hand-written alias file and the Kotlin normaliser the script mirrors. |
| `countryCount`, `aliasCount`, `capitalAliasCount` | Totals, for quick sanity checks. |
| `countries` | One entry per country in the app, sorted by `cca3`. |

Each entry in `countries`:

| Field | Meaning |
| --- | --- |
| `cca3` | ISO 3166-1 alpha-3 code (as given by mledoze/countries; Kosovo uses `UNK`). |
| `name` | Common English name (`name.common`). |
| `officialName` | Official English name (`name.official`). |
| `capital` | First listed capital, or `""` if none. This is the capital shown in the app. |
| `aliases` | Accepted country answers. Each item has `alias` (the text as stored) and `normalized` (the form compared against player input). |
| `capitalAliases` | Accepted capital answers, same shape as `aliases`. |

Items in `aliases` and `capitalAliases` are sorted by `normalized`, then by
`alias`. Several aliases can share a normalised form (for example
`Côte d'Ivoire` and `Cote d'Ivoire`); the app stores each as its own row, and
so does this file.

### How it is derived

The steps below are implemented in `tools/data/export_aliases.py`, which
`tools/data/build_static_db.py` also uses to build the app's database. The
normalisation step mirrors `NormalizeInputUseCase` in
`app/src/main/java/com/geoquiz/app/domain/usecase/NormalizeInputUseCase.kt`; a
unit test checks every shipped alias against the Kotlin implementation.

1. **Load** `data/source/countries.json` (250 entries, mledoze/countries
   schema).
2. **Filter** to the countries used in the app: keep an entry if `unMember` is
   `true`, or its `cca3` is listed in `extraCountries` in
   `data/source/alias_overrides.json` (`VAT`, `PSE`, `TWN` and `UNK`). This
   gives 197 countries.
3. **Country aliases.** For each country, collect, in order and without exact
   duplicates:
   1. `name.common`
   2. `name.official`
   3. every entry of `altSpellings`
   4. the hand-written abbreviations and alternative names for that country in
      `abbreviations` in `data/source/alias_overrides.json` (for example
      `UK`, `Britain` and `Great Britain` for `GBR`; `Ivory Coast` for `CIV`).
4. **Drop** from that set:
   - blank strings;
   - short codes: any string of 3 or fewer characters that are all upper-case
     letters (for example `AU`, `AUS`, `GB`), **unless** the string appears in
     that country's `abbreviations` list (which is how `UK`, `US`, `USA`, `UAE`
     and `DRC` survive). Strings with lower-case or non-cased characters, such
     as `Lao` or Korean names, are not affected.
5. **Capital aliases.** For each country, collect every entry of `capital`
   followed by any hand-written extras for that country in the
   `capitalAliases` in `data/source/alias_overrides.json` (for example `Kotte`
   for `LKA`, or `Pretoria`, `Cape Town` and `Bloemfontein` for `ZAF`). Drop
   exact duplicates and blank strings. No short-code rule applies to capitals,
   so `KL` (Kuala Lumpur) is kept.
6. **Normalise** every alias for matching:
   1. if the string is blank, the result is empty;
   2. Unicode NFD decomposition;
   3. remove characters in the Combining Diacritical Marks block
      (U+0300 to U+036F), so `Côte` becomes `Cote`;
   4. lower-case;
   5. replace `-` (hyphen-minus only) with a space;
   6. remove apostrophes: `'`, `’` (U+2019), `‘` (U+2018) and `ʼ` (U+02BC);
   7. trim leading and trailing whitespace;
   8. collapse runs of ASCII whitespace into a single space.

   So `Côte d'Ivoire` becomes `cote divoire` and `Guinea-Bissau` becomes
   `guinea bissau`. Other punctuation is kept (`Washington D.C.` becomes
   `washington d.c.`), as are letters with no decomposition (`ß`, `ı`) and
   non-Latin scripts.
7. **Write** the result sorted by `cca3`, with aliases sorted as described
   above. The file has no timestamp so it only changes when the data or the
   rules change.

`data/source/alias_overrides.json` is the single source of the hand-written
aliases and extra countries. If you change it, the normalisation rules or
`countries.json`, regenerate this file and the app's `static.db` in the same
change (and bump `StaticDatabase.VERSION`).

### Regenerating

From the repository root (Python 3.9 or later, standard library only):

```
python tools/data/export_aliases.py           # rewrite data/aliases.json
python tools/data/export_aliases.py --check   # exit 1 if data/aliases.json is out of date
python tools/data/build_static_db.py          # rebuild app/src/main/assets/databases/static.db
python tools/data/build_static_db.py --check  # exit 1 if static.db is out of date
```

The `--check` modes write nothing; CI runs both.

### Upstream version

The copy of mledoze/countries in `data/source/countries.json` arrived
with the project's initial commit; the upstream release or commit it was taken
from was not recorded.
