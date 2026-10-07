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
| `app/src/main/assets/countries.json` | mledoze/countries | not recorded | ODbL 1.0 | none |
| `app/src/main/assets/flag_colors.json` | authored for GeoQuiz (owner to confirm) | added `994ec27` | All rights reserved (see `LICENSE`) | none |
| `app/src/main/assets/flag_elements.json` | authored for GeoQuiz (owner to confirm) | added `23c81ba` | All rights reserved (see `LICENSE`) | none |
| `app/src/main/assets/flags/*.svg`, `flags/LICENSE` | flag-icons (lipis) | 7.5.0 | MIT | `tools/flags/fetch_flags.py` |
| `data/aliases.json` | derived from `countries.json` + GeoQuiz rules | regenerated per change | ODbL 1.0 | `tools/data/export_aliases.py` |
| `tools/fonts/Lato-*.ttf`, `OFL.txt` | Google Fonts (google/fonts) | `5d3b761` | SIL OFL 1.1 | `tools/fonts/fetch_fonts.py` |
| Launcher icon (`res/drawable/ic_launcher_*.xml`, `res/mipmap-anydpi-v26/`) | authored for GeoQuiz | `4694f86` | All rights reserved | none |
| Launcher icon PNGs (`res/mipmap-*dpi/`) | 1x1 transparent placeholders (70 bytes each, no artwork) | `40a5fd3` | n/a | none |
| `store_assets/*` | drawn in code for GeoQuiz | regenerated per change | All rights reserved | `generate_store_assets.py`, `generate_achievements_zip.py` |

## Details

### `app/src/main/assets/countries.json`

| | |
|---|---|
| Source | https://github.com/mledoze/countries (`countries.json`) |
| Version / commit | Not recorded. The file matches the mledoze v3-era schema and arrived in the project's initial commit `40a5fd3` (2026-02-12). |
| Size / SHA-256 | 1,294,191 bytes, `9c5b9005e0230a4bc218a9a2b3b9640fe25dc34ad774b2f13018284bba15528b` |
| Licence | Open Database License (ODbL) v1.0 (flags excluded upstream). Full text: [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt) |
| Attribution | "Contains information from mledoze/countries (https://github.com/mledoze/countries), made available under the Open Database License (ODbL) v1.0." Shown in the app's Credits screen. |
| Transformation | None to the file. At first launch `CountryRepositoryImpl.ensureSeeded()` parses it, keeps UN members plus VAT, PSE, TWN and UNK (197 of 250 entries) and builds the Room `countries`, `aliases` and `capital_aliases` tables. |
| Notes | 250 entries. The emoji `flag` field is no longer displayed (flags come from flag-icons). When this file is next refreshed, pin it to an upstream release or commit and record it here, then regenerate `data/aliases.json`. |

### `app/src/main/assets/flag_colors.json`

| | |
|---|---|
| Source | Authored for GeoQuiz; owner to confirm. No third-party source is documented. |
| Version / commit | Added in `994ec27` (2026-02-12) |
| Size / SHA-256 | 11,844 bytes, `3faaa0945acbc4109e983f652ceac0e0ec4acb85e373173afd5724f1fbbc6cba` |
| Licence | All rights reserved (see [`LICENSE`](../LICENSE)), pending owner confirmation of authorship |
| Transformation | None. Seeded into the Room `flag_colors` table. |
| Notes | Maps each of the 197 countries (by cca3) to the colours on its flag (for example `"AFG": ["black", "green", "red", "white"]`). These are facts about flags, not copied artwork. Used by the Flags mode colour categories. |

### `app/src/main/assets/flag_elements.json`

| | |
|---|---|
| Source | Authored for GeoQuiz; owner to confirm. No third-party source is documented. |
| Version / commit | Added in `23c81ba` (2026-02-25, v2.7.0) |
| Size / SHA-256 | 4,153 bytes, `418eb0478e2e3092ef2c0709d5999a78c626d94143e1c3e36553e2bea3b68758` |
| Licence | All rights reserved (see [`LICENSE`](../LICENSE)), pending owner confirmation of authorship |
| Transformation | None. Seeded into the Room `flag_elements` table. |
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

### `data/aliases.json`

| | |
|---|---|
| Source | Derived from `app/src/main/assets/countries.json` plus the hand-written `ABBREVIATIONS` and `CAPITAL_ALIASES` maps in `CountryRepositoryImpl.kt` |
| Version | Regenerated whenever the source data or matching rules change; `--check` mode detects drift |
| Licence | ODbL 1.0 (Derivative Database of mledoze/countries). Full text: [`LICENSE-ODbL.txt`](LICENSE-ODbL.txt) |
| Transformation | `tools/data/export_aliases.py` (mirrors the app's seeding and normalisation logic) |
| Notes | 197 countries, 704 country aliases, 204 capital aliases. Not bundled in the APK; published in the repo to meet the ODbL share-alike obligation. See [`README.md`](README.md). |

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

## Planned (not yet used)

- Natural Earth (public domain): map geometry, Phase 4.
- UN M49 (factual classification, credited): region groupings, if adopted.

Add a full entry above when either is bundled.
