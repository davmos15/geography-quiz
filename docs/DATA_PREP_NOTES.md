# Data prep for Phases 6–7

Branch `upgrade/data-prep` (from `upgrade/p4-maps` at `b888ee1`), 2026-10-13.
Python only; no app code, no Gradle. `docs/UPGRADE_PLAN.md` is untouched: the
decisions below were confirmed by Dav on 2026-10-13 and are ready to copy into the
decisions log (proposed D34 for item 1).

## What was built

| Task | Output | Script (`--check` offline) | Licence | Detail |
|---|---|---|---|---|
| 6.1 Border hop | `data/borders.json` | `tools/data/build_borders.py` | ODbL 1.0 (from mledoze `borders`) | `data/borders_report.md` |
| 7.1 Currencies | `data/currencies.json` | `tools/data/build_currencies.py` (`--refresh` re-queries Wikidata) | ODbL 1.0; facts CC0 | `data/currencies_report.md` |
| 7.2 Rivers, mountains, lakes | `data/physical_features.json` | `tools/data/build_physical_features.py` (`--refresh`; build takes ~2 min) | Public domain + CC0 | `data/physical_features_report.md` |
| 7.3 Flag speed round | `data/flag_lookalikes.json` | `tools/data/validate_flag_lookalikes.py` | All rights reserved; ODbL if put in `static.db` | below |

Every file has a `data/SOURCES.md` row and Details section. Wikidata snapshots are
pinned in `data/source/wikidata_*.json` with query text, date and SHA-256, so builds
and CI need no network. `licence-auditor` ran on each of the four. After fixes it reports no unresolved
required items; the flags audit found one factual error, fixed (see 7.3).

None of these files is bundled in the APK yet. The phase that uses one decides how
it ships: a `static.db` table, or an asset.

## 6.1 Border hop: `data/borders.json`

The mledoze land borders for the 197 countries, made symmetric, then checked against
shared border segments in the bundled `admin0_50m.bin`. 318 candidate pairs; 5
disagreements, each resolved in the script's `OVERRIDES` table (the build fails on a
new or stale disagreement):

| Pair | mledoze | Natural Earth 50m | Resolution |
|---|---|---|---|
| Botswana–Zambia | yes | one shared point (Kazungula) | **keep**: a real border of about 150 m, too short for 50m geometry |
| Spain–Morocco | yes | none | **keep**: Ceuta and Melilla land borders are below 50m resolution |
| India–Sri Lanka | only Sri Lanka lists it | none | **remove**: no land border across the Palk Strait |
| France–Brazil | no | about 476 km shared | **add**: French Guiana is part of the app's France feature |
| France–Suriname | no | about 397 km shared | **add**: the same, via French Guiana |

Also dropped as not playable (not among the 197): French Guiana (resolved via France
above), Hong Kong and Macao (part of China), Western Sahara (D32) and Gibraltar.

**Result:** 317 edges and 43 components. The main component has 153 countries (Africa,
Eurasia and the Americas). There are two pairs (Haiti–Dominican Republic, United
Kingdom–Ireland) and 40 single islands. The diameter is 21 hops (Canada to Lesotho).
5,108 country pairs are 2–6 hops apart, which is plenty for puzzles.

| Hops | 2 | 3 | 4 | 5 | 6 |
|---|---|---|---|---|---|
| Pairs | 688 | 965 | 1,055 | 1,132 | 1,268 |

**Islands (no land border)**, never used as start or end: ATG, AUS, BHR, BHS,
BRB, COM, CPV, CUB, CYP, DMA, FJI, FSM, GRD, ISL, JAM, JPN, KIR, KNA, LCA, LKA, MDG,
MDV, MHL, MLT, MUS, NRU, NZL, PHL, PLW, SGP, SLB, STP, SYC, TON, TTO, TUV, TWN, VCT,
VUT, WSM.

**For Phase 6.** The puzzle generator must pick start and end in the same component.
Because France borders Brazil and Suriname, routes can run between South America and
Europe through France. The in-app "Land borders only" rule should say that overseas
land counts, or the lead removes those two edges (see below). The UK–Ireland and
Hispaniola pairs only give 1-hop puzzles, so they're excluded automatically by the
2–6 rule.

## 7.1 Currencies: `data/currencies.json`

**Contents.** 146 ISO 4217 codes and 13 shared codes:
- EUR (26 app countries) and USD (11)
- XOF (8), XAF (6) and XCD (6)
- AUD, CHF, EGP, ILS, INR, JOD, SGD and ZAR

There is a `primaryCurrency` map for every country, plus per-code `countries`, `shared`,
`group` and `listedInBasketBy`.

**Decisions I made while reviewing** (the agent's first pass didn't have these):
- **Zimbabwe's basket.** mledoze lists 9 currencies for Zimbabwe. Counted as-is,
  Zimbabwe became a second valid answer for the yen, yuan, pound, rupee, rand, pula
  and euro, which breaks the "exactly one valid option" rule. Now only USD counts
  for Zimbabwe, and the rest are recorded in `listedInBasketBy`. `ZWB` ("bonds",
  not an ISO code) is dropped.
- **Fact review.** I reviewed every Wikidata fact. 20 were rejected, mostly because
  Wikidata dates an older currency of the same name, for example "Bulgarian lev
  introduced 1880" or "ouguiya 1973" for MRU. Some also linked the wrong predecessor,
  such as "Central African CFA franc replacing the British West African pound".
  8 more were cut to the year only. "Replacing" clauses that only name an earlier
  issue of the same currency are dropped automatically. The result is 113 facts.
  30 of them name the country (`fact_reveals_country`), so show those only after
  answering.

**Other review details:**
- FSM had no currency in mledoze, so USD was added (like MHL and PLW).
- mledoze gives two different symbols for CHF, EGP and JOD; the issuing country's symbol is kept.
- No Wikidata match for KID, SLL or TVD; CUC and SLL are flagged as obsolete.

## 7.2 Rivers, mountains and lakes: `data/physical_features.json`

**Contents.** 101 features, all present in the bundled Natural Earth layers:
- 41 rivers
- 22 ranges and 8 peaks
- 30 lakes

Each has its NE ids, a verified QID (English label plus `P31/P279*` reaching river,
lake, mountain range or mountain), aliases (curated, each found in Wikidata's English
aliases), countries, continent, a framing bbox and a tier from 1 to 3.

**QID problems fixed:**
- Colorado: NE's record points to the Texas Colorado, so its sibling record is used.
- Paraná: NE points to an unlabelled stub item.
- Aral Sea: NE's two parts point to the part items; the whole-lake item is used.
- Scandinavian Mountains: Wikidata uses a sibling class, which is now accepted.
- Vinson Massif: Wikidata calls it a range, so its type is `range`.
- Lake Eyre: uses Lake Eyre North, because there is no item for the whole lake.

**Countries.** The agent first used geometry alone. That gave Everest = China only,
K2 = China only and Mont Blanc = Italy only, because a single point can't show a
border peak and 50m river lines stop short. I changed it to geometry ∪ Wikidata P17,
minus a reviewed `COUNTRY_EXCLUSIONS` table of 8 removals, each with a reason. For
example, the Appalachians lose France (Saint Pierre and Miquelon) and the Niger
loses Sierra Leone. All the differences are in the report.

**Not in the 50m layers:** the Caspian Sea (it's sea geometry, not a lake), Mount
Fuji, the Matterhorn, Mount Ararat and Mount Olympus. Everest, Kilimanjaro,
Titicaca and the Alps are included. Antarctica's Transantarctic Mountains and
Vinson Massif touch no playable country (`countries: []`).

## 7.3 Flag speed round: `data/flag_lookalikes.json`

I wrote all 24 sets (56 countries) myself from the flags, with nothing copied
from any quiz or site. That covers the nine you listed, plus Andorra and Moldova
added to Chad/Romania, Indonesia/Poland as a separate clear pair, and Guinea added to
Senegal/Mali. It also covers:
- Italy–Mexico, Denmark–Norway, Qatar–Bahrain, Haiti–Liechtenstein
- Egypt–Iraq–Yemen, Jordan–Palestine
- Honduras–Nicaragua–El Salvador–Guatemala, Austria–Latvia, Belgium–Germany
- Bolivia–Ghana–Lithuania, Bulgaria–Hungary, Turkey–Tunisia, China–Vietnam
- United States–Liberia–Malaysia

Each set has an explanation (at most 320 characters), a cue per country, a tier and
an `in_app` rating. The app draws every flag at 4:3 from flag-icons, so proportion-only
differences are invisible:
- **Indonesia/Monaco** is `near_identical`: the bundled SVGs differ only in shade
  (`#e70011` vs `#f31830`). Don't ask it as a pair; show it in review only.
- **Chad/Romania, Netherlands/Luxembourg and Austria/Latvia** are `subtle`: the shade
  differs, and so does band width for Austria/Latvia. Use these for the top tier only.

The licence audit caught one error, now fixed: Syria was in the red-white-black set,
but the bundled `syr.svg` is the current green-white-black flag with three red stars.
Re-check the cues if flag-icons is upgraded, because a flag change makes a cue wrong
silently. The validator only checks codes and format.

## Decisions (confirmed by Dav, 2026-10-13: "happy with your suggestions")

1. **Licences.**
   - `borders.json` and `currencies.json` are ODbL derived databases. Each embeds a licence, licence URL and attribution, and has a `data/README.md` section.
   - `physical_features.json` is public domain + CC0, not ODbL. This extends D31's reasoning: the only mledoze input is the 197 cca3 ids. It needs a decisions-log entry (proposed D34).
   - The `flag_lookalikes.json` text is all rights reserved, or ODbL if copied into `static.db` (D13).
2. **Wikidata is a source of data, not only a cross-check.** It supplies the currency facts, feature countries and aliases. The "Use for" cell of the Approved sources table should say so.
3. **Border edge cases.** Keep or remove each of:
   - France–Brazil and France–Suriname (French Guiana)
   - Spain–Morocco (Ceuta and Melilla)
   - Botswana–Zambia (a border of about 150 m)

   Decided: keep all four, with the in-app rule worded as "land borders, including overseas territories".
4. **Currency answers.**
   - Panama → balboa (USD also circulates).
   - Zimbabwe → USD (its own current currency is not in this mledoze copy). The alternative is to leave Zimbabwe out of the mode.
   - Palestine has no primary currency, so it can only appear in "which of these uses…" questions.
   - Sierra Leone shows the old SLL, because mledoze has no SLE. Adding SLE would need a manual entry and a decision.
5. **Physical features.**
   - The tier rules: tier 1 is NE scalerank 1, plus Mont Blanc, the Pyrenees and the Carpathians bumped up by judgement; tier 2 is well known; tier 3 is less prominent.
   - "Lake Eyre" is shown for Lake Eyre North.
   - 41 rivers is one more than about 40 (fine as is, in my view).
6. **Done after Phase 4 finished:** the mledoze entry in `THIRD_PARTY_NOTICES.md` lists the `borders` and `currencies` uses and the new derived databases; the root README's "Data and licences" names the new files; CI runs the four `--check` scripts. Still open: credit Wikidata on the Credits screen if wanted (CC0 needs no credit).
7. **Email.** The Wikidata queries on 2026-10-13 sent the owner's personal email in the
   User-Agent; it has been replaced with the repository URL, and the branch history was
   rewritten so no commit contains it. Commits in this repo now use the GitHub noreply
   address. Older commits on `main` still carry the personal address as author.

## Re-running

```
python tools/data/build_borders.py --check
python tools/data/build_currencies.py --check
python tools/data/build_physical_features.py --check   # ~2 minutes
python tools/data/validate_flag_lookalikes.py --check
```

CI runs all four next to `export_aliases.py --check`.
