# Data and asset sources

Every data file and artwork asset bundled with GeoQuiz, or used to build its
store listing, has an entry here: where it came from, which version, its
licence, and the script that produces or transforms it.

**How to keep this current.** Any change that adds, replaces or regenerates a
bundled data or asset file must add or update its entry in the same change.
Phases 4 to 7 of the upgrade (map geometry, regions and new modes) will add
rows; a file without a row is not allowed to ship. Only use sources from the
"Approved sources" table in `docs/UPGRADE_PLAN.md` section 5 (anything else
needs a decisions-log entry first). Also add third-party sources to
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) and, where attribution
is required, to the in-app Credits and licences screen. Record a pinned version
or commit and, where practical, a checksum, so the file can be reproduced.

## Summary

| File | Source | Version / commit | Licence | Script |
|---|---|---|---|---|
| `app/src/main/assets/databases/static.db` | derived from `data/source/*.json` + GeoQuiz rules | regenerated per change (`StaticDatabase.VERSION`) | ODbL 1.0 | `tools/data/build_static_db.py` |
| `data/source/countries.json` (not shipped) | mledoze/countries | not recorded | ODbL 1.0 | none |
| `data/source/alias_overrides.json` (not shipped) | authored for GeoQuiz | moved from `CountryRepositoryImpl.kt` in Phase 2 | ODbL 1.0 as part of the derived database | none |
| `data/source/flag_colors.json` (not shipped) | authored for GeoQuiz (owner to confirm) | added `994ec27` | ODbL 1.0 (released as part of `static.db`, D13) | none |
| `data/source/flag_elements.json` (not shipped) | authored for GeoQuiz (owner to confirm) | added `23c81ba` | ODbL 1.0 (released as part of `static.db`, D13) | none |
| `app/src/main/assets/flags/*.svg`, `flags/LICENSE` | flag-icons (lipis) | 7.5.0 | MIT | `tools/flags/fetch_flags.py` |
| `app/src/main/assets/geo/admin0_110m.bin` | Natural Earth `ne_110m_admin_0_countries` (+ `ne_50m_admin_0_countries` for 29 small countries) | 5.1.2 (zip VERSION 5.1.1) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/admin0_50m.bin` | Natural Earth `ne_50m_admin_0_countries` | 5.1.2 (zip VERSION 5.1.1) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/tap_zones.bin` | Natural Earth `ne_50m_admin_0_tiny_countries` + `ne_50m_admin_0_countries` | 5.1.2 (zip VERSION 5.1.1) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/admin1_50m.bin` | Natural Earth `ne_50m_admin_1_states_provinces` (US and Canada only) | 5.1.2 (zip VERSION 5.1.1) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/rivers_50m.bin` | Natural Earth `ne_50m_rivers_lake_centerlines` | 5.1.2 (zip VERSION 5.0.0) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/lakes_50m.bin` | Natural Earth `ne_50m_lakes` | 5.1.2 (zip VERSION 5.0.0) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/regions_50m.bin` | Natural Earth `ne_50m_geography_regions_polys` | 5.1.2 (zip VERSION 5.0.0) | Public domain | `tools/geodata/build_geodata.py` |
| `app/src/main/assets/geo/geo_points_50m.bin` | Natural Earth `ne_50m_geography_regions_elevation_points` + `ne_50m_geography_regions_points` | 5.1.2 (zip VERSION 5.0.0) | Public domain | `tools/geodata/build_geodata.py` |
| `data/geo/build_log.txt`, `data/geo/FORMAT.md` (not shipped) | build log generated with the map layers; format contract authored for GeoQuiz | regenerated per change | Public domain inputs; authored text All rights reserved | `tools/geodata/build_geodata.py` |
| `data/aliases.json` | derived from `data/source/countries.json` + `alias_overrides.json` | regenerated per change | ODbL 1.0 | `tools/data/export_aliases.py` |
| `data/borders.json`, `data/borders_report.md` (report not shipped) | mledoze/countries `borders` field, cross-checked against `app/src/main/assets/geo/admin0_50m.bin` (Natural Earth) | regenerated per change | ODbL 1.0 (derived database) | `tools/data/build_borders.py` |
| `data/currencies.json`, `data/currencies_report.md` (report not shipped) | mledoze/countries `currencies` field, plus one short fact per code from Wikidata | regenerated per change | ODbL 1.0 (derived database); Wikidata facts are CC0 | `tools/data/build_currencies.py` |
| `data/source/wikidata_currencies.json` (not shipped) | Wikidata SPARQL endpoint, pinned snapshot | retrieved 2026-10-13 | CC0 | `tools/data/build_currencies.py --refresh` |
| `data/physical_features.json`, `data/physical_features_report.md` (report not shipped) | Natural Earth rivers/lakes/regions/geo-points layers already in `app/src/main/assets/geo/`, cross-checked against Wikidata | regenerated per change | Public domain (Natural Earth) and CC0 (Wikidata), confirmed 2026-10-13 | `tools/data/build_physical_features.py` |
| `data/source/wikidata_physical_features.json` (not shipped) | Wikidata SPARQL endpoint, pinned snapshot | retrieved 2026-10-13 | CC0 | `tools/data/build_physical_features.py --refresh` |
| `data/flag_lookalikes.json` | authored for GeoQuiz (no third-party source) | added on `upgrade/data-prep` | All rights reserved unless bundled into `static.db`, then ODbL 1.0 as for `flag_colors` (D13); confirmed 2026-10-13 | `tools/data/validate_flag_lookalikes.py` (validates, no generation) |
| `tools/fonts/Lato-*.ttf`, `OFL.txt` | Google Fonts (google/fonts) | `5d3b761` | SIL OFL 1.1 | `tools/fonts/fetch_fonts.py` |
| Launcher icon (`res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/`) | authored for GeoQuiz | `4694f86` | All rights reserved | none |
| Launcher icon PNGs (`res/mipmap-*dpi/`) | 1x1 transparent placeholders (70 bytes each, no artwork) | `40a5fd3` | n/a | none |
| `store_assets/*` | drawn in code for GeoQuiz | regenerated per change | All rights reserved | `generate_store_assets.py`, `generate_achievements_zip.py` |

## Details

### `app/src/main/assets/databases/static.db`

| | |
|---|---|
| Source | Derived from `data/source/countries.json` (mledoze/countries, ODbL 1.0), `data/source/alias_overrides.json`, `data/source/flag_colors.json` and `data/source/flag_elements.json` |
| Version | `StaticDatabase.VERSION` (stored as SQLite `user_version`); bump it whenever the content changes so installed copies are replaced |
| Licence | ODbL 1.0 as a whole (Derivative Database of mledoze/countries), including the `flag_colors` and `flag_elements` tables (decision D13). Full text: [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt). Offered and documented in [`README.md`](README.md). |
| Transformation | `tools/data/build_static_db.py`: keeps UN members plus `extraCountries` (197 of 250 entries), builds the `countries`, `aliases`, `capital_aliases`, `flag_colors` and `flag_elements` tables with the schema Room exports (`app/schemas/com.geoquiz.app.data.local.db.StaticDatabase/`). Alias rules are shared with `tools/data/export_aliases.py`. `--check` mode (run in CI) detects drift. |
| Notes | About 210 KB; 197 countries, 704 country aliases, 204 capital aliases, 628 flag colour rows, 136 flag element rows. Opened read-only by the app with Room `createFromAsset()`; replaces the first-launch JSON seeding used up to v2.7.x. The emoji `flag` field of `countries.json` is not included. The alias table is also published as `data/aliases.json`. |

### `data/source/countries.json`

| | |
|---|---|
| Source | https://github.com/mledoze/countries (`countries.json`) |
| Version / commit | Not recorded. The file matches the mledoze v3-era schema and arrived in the project's initial commit `40a5fd3` (2026-02-12). |
| Size / SHA-256 | 1,294,191 bytes, `9c5b9005e0230a4bc218a9a2b3b9640fe25dc34ad774b2f13018284bba15528b` |
| Licence | Open Database License (ODbL) v1.0 (flags excluded upstream). Full text: [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt) |
| Attribution | "Contains information from mledoze/countries (https://github.com/mledoze/countries), made available under the Open Database License (ODbL) v1.0." Shown in the app's Credits screen. |
| Transformation | None to the file. `tools/data/build_static_db.py` builds `static.db` from it and `tools/data/export_aliases.py` builds `data/aliases.json`. Not shipped in the APK (moved from `app/src/main/assets/` in Phase 2). |
| Notes | 250 entries. The emoji `flag` field is no longer displayed (flags come from flag-icons). When this file is next refreshed, pin it to an upstream release or commit and record it here, then regenerate `data/aliases.json` and `static.db`. |

### `data/source/alias_overrides.json`

| | |
|---|---|
| Source | Authored for GeoQuiz. Extra answer aliases (`abbreviations`, for example "UK", "Ivory Coast"), extra capital aliases (`capitalAliases`, for example "KL") and the non-UN-member countries the app includes (`extraCountries`: VAT, PSE, TWN, UNK). |
| Version / commit | Moved out of the `ABBREVIATIONS`, `CAPITAL_ALIASES` and `extraCountries` constants in `CountryRepositoryImpl.kt` in Phase 2 |
| Licence | Published under ODbL 1.0 as part of the derived alias table |
| Transformation | Read by `tools/data/export_aliases.py` and `tools/data/build_static_db.py`. Not shipped. |

### `data/source/flag_colors.json`

| | |
|---|---|
| Source | Authored for GeoQuiz; owner to confirm. No third-party source is documented. |
| Version / commit | Added in `994ec27` (2026-02-12) |
| Size / SHA-256 | 11,844 bytes, `3faaa0945acbc4109e983f652ceac0e0ec4acb85e373173afd5724f1fbbc6cba` |
| Licence | ODbL 1.0, released as part of `static.db` (decision D13); authorship still to be confirmed by the owner |
| Transformation | None to the file. `tools/data/build_static_db.py` copies it into the `flag_colors` table of `static.db` (looked up by cca3, then cca2). Not shipped. |
| Notes | Maps each of the 197 countries (by cca3) to the colours on its flag (for example `"AFG": ["black", "green", "red", "white"]`). These are facts about flags, not copied artwork. Used by the Flags mode colour categories. |

### `data/source/flag_elements.json`

| | |
|---|---|
| Source | Authored for GeoQuiz; owner to confirm. No third-party source is documented. |
| Version / commit | Added in `23c81ba` (2026-02-25, v2.7.0) |
| Size / SHA-256 | 4,153 bytes, `418eb0478e2e3092ef2c0709d5999a78c626d94143e1c3e36553e2bea3b68758` |
| Licence | ODbL 1.0, released as part of `static.db` (decision D13); authorship still to be confirmed by the owner |
| Transformation | None to the file. `tools/data/build_static_db.py` copies it into the `flag_elements` table of `static.db`. Not shipped. |
| Notes | Maps 196 countries to the elements on their flag (`plant`, `animal`, `sun`, `union_jack`, `coat_of_arms`, `text` and so on). Facts about flags. Used by the Flags mode "Shapes and objects" categories. |

### `app/src/main/assets/flags/*.svg` and `app/src/main/assets/flags/LICENSE`

| | |
|---|---|
| Source | flag-icons by Panayiotis Lipiridis, https://github.com/lipis/flag-icons |
| Version | 7.5.0, from https://registry.npmjs.org/flag-icons/-/flag-icons-7.5.0.tgz |
| SHA-256 | `c0b80bf0e08006a60f56621d6bc49f8c7131f4d1fef6737a165a673431f4b518` (tarball) |
| Licence | MIT. The licence notice ships in the APK as `flags/LICENSE`; a second copy is `tools/flags/LICENSE.flag-icons`. |
| Transformation | `tools/flags/fetch_flags.py`: verifies the tarball hash, copies `package/flags/4x3/<cca2>.svg` unmodified, renamed to lower-case cca3 (`aus.svg`, `unk.svg` for Kosovo), for the 197 countries the app shows. Output is reproducible. |
| Notes | 197 SVGs plus `LICENSE`. Rendered with Coil 3 + coil-svg (`ui/components/FlagImage.kt`). See [`tools/flags/README.md`](../tools/flags/README.md). Replaced the earlier Unicode emoji flags (decision D7). |

### `app/src/main/assets/geo/*.bin` (map layers)

| | |
|---|---|
| Files | `admin0_110m.bin`, `admin0_50m.bin`, `tap_zones.bin`, `admin1_50m.bin`, `rivers_50m.bin`, `lakes_50m.bin`, `regions_50m.bin`, `geo_points_50m.bin` (one layer each; format in [`geo/FORMAT.md`](geo/FORMAT.md)) |
| Source | Natural Earth, https://www.naturalearthdata.com/, downloaded from the versioned path https://naciscdn.org/naturalearth/5.1.2/ |
| Version | Natural Earth release 5.1.2. The zips' `VERSION.txt` reads 5.1.1 (cultural layers) and 5.0.0 (physical layers), which is what that release ships; the script checks it. |
| SHA-256 (zips) | `110m/cultural/ne_110m_admin_0_countries.zip` `0f243aeac8ac6cf26f0417285b0bd33ac47f1b5bdb719fd3e0df37d03ea37110`<br>`50m/cultural/ne_50m_admin_0_countries.zip` `5fed433373581fa648920435f937d95f2d3c0200e067409c6478dcdf1b853139`<br>`50m/cultural/ne_50m_admin_0_tiny_countries.zip` `e6b63e61220a65a78dfddd67cdec3aadd1e335e904de12c98442e693e62ce9d2`<br>`50m/cultural/ne_50m_admin_1_states_provinces.zip` `61f79e6705e62a55d6bcf698394295a589af5e24a4b2684c6519dd35c1300bf6`<br>`50m/physical/ne_50m_rivers_lake_centerlines.zip` `c607d9d7e7702827a7996fff6dc17b87a338c5ed3b52d12c402e0c9669cc7b56`<br>`50m/physical/ne_50m_lakes.zip` `f28d42c286d96b57a17aac2cbeb432f8c65532c20063495711fbc64e24666df3`<br>`50m/physical/ne_50m_geography_regions_polys.zip` `a6e7ac257f6f0847ed80e6dc6e776441456652c093cfe90c7bf26dc8069f0d03`<br>`50m/physical/ne_50m_geography_regions_points.zip` `c4498acc334dc84209bdac0f3262a1762a7983ed66d50073cab17bd3435cdfb9`<br>`50m/physical/ne_50m_geography_regions_elevation_points.zip` `b23b3f62c1a003ea733e2cc161148d7d4f8726462f0b45592b7ca3b52cafe6c7` |
| Licence | Public domain (Natural Earth terms of use: no permission needed, credit appreciated). Credit used: "Made with Natural Earth." |
| Transformation | `tools/geodata/build_geodata.py` (see [`tools/geodata/README.md`](../tools/geodata/README.md)): maps Natural Earth country codes to the app's 197 cca3 codes (Somaliland merged into Somalia, Northern Cyprus into Cyprus, Kosovo as `UNK`; other land kept as non-playable), simplifies polygon layers with shared-arc Douglas–Peucker so borders stay identical, quantises to int16 per layer, flags remote and antimeridian-wrap parts, and writes the GQGM binary format. Each build decodes and validates every file and enforces the size budgets (D3: 1.5 MB in total). `--check` detects drift; the output is byte-for-byte reproducible. |
| Notes | 605,778 bytes in total (per-file sizes and SHA-256 in [`geo/build_log.txt`](geo/build_log.txt)). Natural Earth's default (de facto) point of view on disputed areas. Feature ids are app cca3 codes (the list of 197 comes from `data/source/countries.json` via `tools/data/export_aliases.py`; no other mledoze/countries data is included), ISO 3166-2 codes or Natural Earth `ne_id`; names, properties and Wikidata ids are Natural Earth's. |

### `data/aliases.json`

| | |
|---|---|
| Source | Derived from `data/source/countries.json` plus the hand-written aliases in `data/source/alias_overrides.json` |
| Version | Regenerated whenever the source data or matching rules change; `--check` mode detects drift |
| Licence | ODbL 1.0 (Derivative Database of mledoze/countries). Full text: [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt) |
| Transformation | `tools/data/export_aliases.py` (same rules as `static.db`; the normaliser is checked against the app's by a unit test) |
| Notes | 197 countries, 704 country aliases, 204 capital aliases. Not bundled in the APK as a file (the same table ships inside `static.db`); published in the repo to meet the ODbL share-alike obligation. See [`README.md`](README.md). |

### `data/borders.json`

| | |
|---|---|
| Source | Primary: `data/source/countries.json` (mledoze/countries, ODbL 1.0) `borders` field, restricted to the app's 197 countries (`tools/data/export_aliases.py` `derive_countries()`) and symmetrised. Cross-checked against `app/src/main/assets/geo/admin0_50m.bin` (Natural Earth 5.1.2 `ne_50m_admin_0_countries`, public domain, already bundled): two countries are adjacent if their polygons share at least one identical quantised border segment. |
| Version | Regenerated whenever `data/source/countries.json` or the map asset changes; `--check` mode detects drift |
| Licence | ODbL 1.0 (Derivative Database of mledoze/countries); the Natural Earth cross-check contributes no data to the graph beyond confirming/disputing mledoze's land borders, as agreed for the map layers (decision D31) |
| Transformation | `tools/data/build_borders.py`: builds the mledoze adjacency graph for the app's 197 countries, builds a second adjacency graph from shared border segments in `admin0_50m.bin`, and resolves every disagreement between them with an explicit, reviewed, commented override table in the script (five disagreements found: Botswana-Zambia kept as the Kazungula quadripoint; Spain-Morocco kept, the Ceuta/Melilla border is too small for 50m-scale geometry; India-Sri Lanka removed, no land border exists across the Palk Strait and the mledoze entry was asymmetric; France-Brazil and France-Suriname added, real land borders via French Guiana that mledoze's France entry omits). Full evidence and the resulting BFS statistics are in `data/borders_report.md` (not shipped, build output only). |
| Notes | 197 countries, 317 land-border edges, 40 islands (no land border in the final graph), 43 connected components (largest 153 countries, diameter 21 hops). Built for the Border hop mode (Phase 6). Feature ids are the app's cca3 codes. |

### `data/currencies.json`

| | |
|---|---|
| Source | Primary: `data/source/countries.json` (mledoze/countries, ODbL 1.0) `currencies` field, restricted to the app's 197 countries (`tools/data/export_aliases.py` `derive_countries()`). One short fact per ISO 4217 code comes from Wikidata (CC0), queried once and pinned as `data/source/wikidata_currencies.json`. |
| Version | Regenerated whenever `data/source/countries.json` changes or the Wikidata snapshot is refreshed; `--check` mode detects drift (offline, reads only the pinned snapshot) |
| Licence | ODbL 1.0 (Derivative Database of mledoze/countries) as a whole; the per-currency `fact` and `fact_source` fields are drawn from Wikidata under CC0 |
| Transformation | `tools/data/build_currencies.py`: one entry per ISO 4217 code used by an app country (146 codes), with `countries` (sorted cca3), `shared` and a `group` label for currencies used by two or more app countries (the well-known unions named in the task brief -- euro, US dollar, the two CFA francs, East Caribbean dollar -- plus every other shared code found automatically, e.g. the Australian dollar, South African rand, Indian rupee). A separate `primaryCurrency` map gives one currency per country; for the 13 countries mledoze lists with more than one currency, the choice is made by an explicit, reviewed, commented override table in the script (`PRIMARY_OVERRIDES`/`PRIMARY_OVERRIDE_NOTES`), or left `null` where there's no official currency to decide between (the Palestinian territories). Zimbabwe's entry is a basket of foreign currencies (`BASKET_COUNTRIES`): only its primary currency (USD) counts as used by Zimbabwe, the others are recorded in `listedInBasketBy`, and the non-ISO `ZWB` ("Zimbabwean bonds"), which only Zimbabwe lists, is left out. Other anomalies are kept and flagged with `questionable`/`note`: two currencies obsolete in practice (Cuba's `CUC`, Sierra Leone's pre-2022 `SLL`) and three codes where mledoze gives more than one symbol (`CHF`, `EGP`, `JOD`). Every generated fact was reviewed (`FACT_REVIEW`): 20 rejected because Wikidata dates an older currency of the same name or links the wrong predecessor, 8 kept as the year only. Full evidence for every decision is in `data/currencies_report.md` (not shipped, build output only). |
| Notes | 146 currency codes, 197 countries, 113 one-line facts (20 rejected in review, the rest have no usable Wikidata statement or no Wikidata match for their ISO code). A fact is marked `fact_reveals_country: true` (30 of the 113) when it names the demonym or label of the country the player is asked to guess, so the app can withhold it until after answering. `--refresh` re-queries Wikidata; the default build and `--check` are offline. |

### `data/source/wikidata_currencies.json`

| | |
|---|---|
| Source | Wikidata SPARQL endpoint, https://query.wikidata.org/sparql |
| Retrieved | 2026-10-13 |
| SHA-256 | `5c48f5dee2de8eae0ab6ec4bebda71ed420923e48aec289960bbc84d78894a35` (of the pinned `main` and `demonyms` query results; checked on every load) |
| Licence | CC0 (Wikidata) |
| Transformation | `tools/data/build_currencies.py --refresh`: two SPARQL queries (`mainQuery`, `demonymQuery`, both saved verbatim in the file) -- one matching each ISO 4217 code (`P498`) used by an app country to its Wikidata item and reading `P571` inception, `P580` start time, `P1365` replaces (with its `P585` point-in-time qualifier) and `P17` country; the other reading `P1549` demonym for every country found by the first query, used only to detect when a generated fact would name the country the player is asked to guess. Not shipped; `data/currencies.json` is built from it offline. |
| Notes | 147 codes, 1,379,799 bytes. 4 codes (`KID`, `SLL`, `TVD`, `ZWB`) have no Wikidata item with that ISO 4217 code. 3 codes (`GBP`, `INR`, `SYP`) matched more than one Wikidata item; each is disambiguated by a reviewed, commented table in the script (`QID_DISAMBIGUATION`) after checking the candidates' own labels (for example `INR`'s two matches are "Indian rupee" and "Digital Rupee", the central bank digital currency pilot -- the latter is rejected). |

### `data/physical_features.json`

| | |
|---|---|
| Source | Candidates come only from features already shipped in `app/src/main/assets/geo/rivers_50m.bin`, `lakes_50m.bin`, `regions_50m.bin` and `geo_points_50m.bin` (Natural Earth 5.1.2, public domain, decoded with `tools/geodata/geoformat.py`). Each feature's Wikidata QID is read from Natural Earth's own `wikidata` property and verified against `data/source/wikidata_physical_features.json` (CC0). |
| Version | Regenerated whenever the map `.bin` files or the Wikidata snapshot change; `--check` mode detects drift (offline, reads only the pinned snapshot) |
| Licence | **Public domain (Natural Earth) and CC0 (Wikidata)** -- this file is not ODbL. It carries no mledoze/countries content beyond the 197 app cca3 ids used to key the `countries` field (decision D31: that id list is not a substantial extract of mledoze's database). Confirmed by the owner on 2026-10-13 (proposed decision D34, extending D31); see `data/physical_features_report.md` "Licence note". |
| Transformation | `tools/data/build_physical_features.py`: a reviewed, commented selection table (`SELECTION`, ~40 rivers, ~30 mountain ranges/peaks, ~30 lakes, grouping Natural Earth `ne_id`s where one river or lake is split across several records) with a `QID_OVERRIDES` table for the handful of cases where Natural Earth's own `wikidata` property is missing, wrong or the wrong type, and a small curated `ALIASES_CURATED` table of common alternative English names (each checked against the Wikidata snapshot's own English alias list). `countries` is computed geometrically by testing each feature's vertices against `app/src/main/assets/geo/admin0_50m.bin`'s polygons (point-in-polygon with holes subtracted) and combined with Wikidata P17 (restricted to the app's 197): the final list is the union of the two minus a reviewed `COUNTRY_EXCLUSIONS` table (8 exclusions across 7 features, each with a reason), because a single point cannot show a peak on a border and 50m river lines stop short, while P17 sometimes lists a whole basin or an overseas territory. Every difference is listed in the report. Every QID is verified against the snapshot for an English label and for `wdt:P31/wdt:P279*` reaching the expected Wikidata class (river `Q4022`, lake `Q23397`, mountain range `Q46831`, mountain `Q8502`); the build stops if one fails. Full evidence for every decision, the layer inventory, and near-misses (well-known features not in the 50m layers) are in `data/physical_features_report.md` (not shipped, build output only). |
| Notes | 101 features: 41 rivers, 22 mountain ranges, 8 peaks, 30 lakes, covering every inhabited continent plus Antarctica (Transantarctic Mountains, Vinson Massif -- both have an empty `countries` list, correctly: Antarctica is not one of the app's 197 countries). `--refresh` re-queries Wikidata; the default build and `--check` are offline. |

### `data/source/wikidata_physical_features.json`

| | |
|---|---|
| Source | Wikidata SPARQL endpoint, https://query.wikidata.org/sparql |
| Retrieved | 2026-10-13 |
| SHA-256 | `8144884cd8f1114913314a2292cd46f846e36717e54e70270bb75d1b97c58310` (of the pinned per-item data and country-ISO3 map; checked on every load) |
| Licence | CC0 (Wikidata) |
| Transformation | `tools/data/build_physical_features.py --refresh`: five SPARQL queries (English label + `P2044` elevation, `wdt:P31/wdt:P279*` against the four target classes, `P17` country, English `skos:altLabel` aliases, and `P298` ISO 3166-1 alpha-3 for every P17 country found; all saved verbatim in the file) against the ~100 QIDs used by `data/physical_features.json`. Reduced to a lean per-QID structure (label, elevation, instance-of classes, P17 countries, English aliases) rather than the raw SPARQL JSON. Not shipped; `data/physical_features.json` is built from it offline. |
| Notes | 101 QIDs. Two data-quality issues found in Natural Earth's own `wikidata` property during verification (not in this snapshot's own data, but discovered using it) are documented in `data/physical_features_report.md`: a Colorado River record pointing to the Texas Colorado River's QID, and a Paraná River record pointing to an unlabelled Wikidata stub typed only as "watercourse". |

### `data/flag_lookalikes.json`

| | |
|---|---|
| Source | Authored for GeoQuiz by the lead (2026-10-13): 24 sets of flags that are easy to confuse, each with a short original explanation of the difference and a one-line cue per country. Written from the flags themselves (as drawn by the bundled flag-icons SVGs); no list, wording or grouping was taken from any quiz, website or other source. |
| Version / commit | Added on `upgrade/data-prep` |
| Licence | All rights reserved (authored text). If the sets are copied into `static.db`, they are released under ODbL 1.0 with it, as for `flag_colors` and `flag_elements` (decision D13). Confirmed by the owner on 2026-10-13. |
| Transformation | None. `tools/data/validate_flag_lookalikes.py` checks every code is one of the 197 app countries with a bundled flag, ids are unique, each set has 2-4 countries and a cue per country, tiers and the `in_app` rating are valid, text is short and uses Australian spelling, and the file is in canonical format. `--check` exits 1 on any problem. |
| Notes | Flags are drawn at 4:3 in the app, so a difference only in official proportions is not visible; `in_app` records what a player can actually see (`clear`, `subtle`, `near_identical`). Indonesia/Monaco is `near_identical` and should not be asked as a pair; Poland is a separate `clear` set with Indonesia. Built for the Flag speed round (Phase 7.3). |

### `tools/fonts/Lato-Regular.ttf`, `tools/fonts/Lato-Bold.ttf`, `tools/fonts/OFL.txt`

| | |
|---|---|
| Source | Google Fonts, https://github.com/google/fonts/tree/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato |
| Version / commit | google/fonts `5d3b76120a319730fda218cc7410174a462b32cb` (2026-03-27) |
| Licence | SIL Open Font License 1.1, Reserved Font Name "Lato" (`OFL.txt`) |
| Transformation | `tools/fonts/fetch_fonts.py` downloads and verifies SHA-256 checksums; files are unmodified |
| Notes | Build-tool use only (store graphics and achievement icons); not shipped in the app. Checksums in [`tools/fonts/README.md`](../tools/fonts/README.md). |

### Launcher icon

| | |
|---|---|
| Files | `app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `ic_launcher_round.xml` |
| Source | Hand-written adaptive vector drawable (globe and question mark) authored for GeoQuiz; no third-party source |
| Version / commit | `4694f86` (2026-02-12) |
| Licence | All rights reserved (see [`LICENSE`](../LICENSE)) |
| Transformation | None |
| Notes | With minSdk 26 the adaptive icon is always used. The raster `res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.png` files from the initial commit `40a5fd3` are 1x1 transparent placeholders (70 bytes each) with no artwork, so there is nothing to license. minSdk 26 devices never display them. |

### Store assets

| | |
|---|---|
| Files | `store_assets/play_store_icon_512.png`, `store_assets/feature_graphic_1024x500.png`, `store_assets/achievements/*.png` (38 icons), `store_assets/achievements/*.csv`, `store_assets/achievements_import.zip` |
| Source | Drawn in code with Pillow by `generate_store_assets.py` and `generate_achievements_zip.py` (repo root); no external images |
| Licence | All rights reserved (see [`LICENSE`](../LICENSE)). The scripts render text with Lato (OFL 1.1); rendered images are not subject to the OFL. The PNGs currently committed were rendered with system Arial before commit 089299d and will be regenerated with Lato in Phase 9. |
| Transformation | The two scripts above; rerun after changing achievements or artwork |
| Notes | Achievement names and descriptions come from the list in `generate_achievements_zip.py`, which duplicates `domain/model/Achievement.kt`. `AchievementsIconMappings.csv` is a byte-identical stale copy of `AchievementsIconsMappings.csv`, which is the file the script writes and puts in the ZIP. Check which name the Play Games bulk import expects before removing either. |

## Credited, not bundled

- Wikidata (CC0): facts, Wikidata ids, countries and aliases in `data/currencies.json` and `data/physical_features.json` come from or are checked against Wikidata (pinned snapshots in `data/source/wikidata_*.json`). CC0 needs no credit; it is credited on the Credits screen anyway ("Facts"). Move this entry to the bundled files once Phases 6–7 ship these data files in the app.
- UN M49 (factual classification): the map's continent views follow the UN M49 continents and are credited on the Credits screen (since Phase 4). No UN M49 file is bundled; groupings use mledoze `region`/`subregion` in `static.db`.
