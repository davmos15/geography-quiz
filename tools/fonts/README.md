# Bundled fonts

Used by `generate_store_assets.py` and `generate_achievements_zip.py` (repo root)
to render text in the Play Store icon, feature graphic and achievement icons.
The scripts load these files by path and fail if they are missing; they never
fall back to system fonts.

| Item | Value |
|---|---|
| Font | Lato (Regular 400, Bold 700), by Łukasz Dziedzic |
| Licence | SIL Open Font License 1.1 (see `OFL.txt`, Reserved Font Name "Lato") |
| Source | Google Fonts repository, `ofl/lato/` |
| Pinned commit | [`5d3b76120a319730fda218cc7410174a462b32cb`](https://github.com/google/fonts/tree/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato) (2026-03-27) |

## Files and SHA-256 checksums

| File | Bytes | SHA-256 | URL |
|---|---|---|---|
| `Lato-Regular.ttf` | 656568 | `d636e4683231f931eda222d588e944d082bfd3bdba02f928bee461c0f185b251` | https://raw.githubusercontent.com/google/fonts/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato/Lato-Regular.ttf |
| `Lato-Bold.ttf` | 656544 | `8a0aace75d33794eece4b28187bfc1df0bbd2888b5d8a56e01788c8d65d16be1` | https://raw.githubusercontent.com/google/fonts/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato/Lato-Bold.ttf |
| `OFL.txt` | 4407 | `74ba064d03f1f1c4a952da936c3eb71866c34404916734de3cae73b34357e59e` | https://raw.githubusercontent.com/google/fonts/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato/OFL.txt |

## Re-fetching and verifying

```
python tools/fonts/fetch_fonts.py           # download missing or altered files, verify checksums
python tools/fonts/fetch_fonts.py --check   # verify only
```

`.gitattributes` in this folder stops Git from rewriting line endings in
`OFL.txt`, so the checksum holds on Windows checkouts too.

## Licence notes

- The OFL allows the fonts to be used, bundled and redistributed, including in
  commercial work, as long as `OFL.txt` travels with them and the fonts are not
  sold on their own.
- Rendered images (store graphics, achievement icons) are not subject to the OFL.
- If the font files are ever modified, the result must not be called "Lato"
  (Reserved Font Name).
