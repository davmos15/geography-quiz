# Flag images

The app shows flags from bundled SVGs in `app/src/main/assets/flags/`, one per
country, named by lower-case ISO 3166-1 alpha-3 code (`aus.svg`, `unk.svg` for
Kosovo). They are rendered by `ui/components/FlagImage.kt` (Coil 3 + coil-svg).

## Source

| | |
|---|---|
| Project | flag-icons by Panayiotis Lipiridis (https://github.com/lipis/flag-icons) |
| Licence | MIT. Copyright and licence notice kept in `app/src/main/assets/flags/LICENSE` and `tools/flags/LICENSE.flag-icons` |
| Version | 7.5.0 (pinned) |
| Download | https://registry.npmjs.org/flag-icons/-/flag-icons-7.5.0.tgz |
| SHA-256 | `c0b80bf0e08006a60f56621d6bc49f8c7131f4d1fef6737a165a673431f4b518` |
| Files used | `package/flags/4x3/<cca2>.svg`, unmodified |

## Which countries

Every entry in `data/source/countries.json` the app shows: UN members
plus VAT, PSE, TWN and UNK (197 today). This mirrors the filter in
`CountryRepositoryImpl`; keep `EXTRA_CCA3` in `fetch_flags.py` in sync with it.
The script maps cca2 (flag-icons file name) to cca3 (asset name) at build time,
so no mapping is needed at runtime.

## Re-running

```
python tools/flags/fetch_flags.py
```

Python 3.9+, standard library only. The script downloads the pinned tarball,
fails if the SHA-256 does not match, fails (listing the countries) if any used
country has no SVG, then replaces `app/src/main/assets/flags/` entirely and
refreshes both LICENSE copies. Output is byte-for-byte reproducible.

To upgrade flag-icons, change `FLAG_ICONS_VERSION` and `TARBALL_SHA256` in the
script (get the hash with `sha256sum` on the downloaded `.tgz`), re-run, and
review the changed SVGs. `FlagAssetsPresentTest` checks every used country has
an asset.
