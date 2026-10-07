# Third-party notices

GeoQuiz ("Geography Quiz") is proprietary software (see [`LICENSE`](LICENSE)).
It includes or is built with the third-party data, assets and libraries listed
below. Each keeps its own licence. File-level provenance (versions, commits,
checksums and the scripts that produce each file) is in
[`data/SOURCES.md`](data/SOURCES.md).

Inside the app, Settings → Credits and licences shows the data and flag
credits, and its "Open-source licences" screen (generated at build time by
AboutLibraries) is the runtime source of truth for library licences. If this
file and that screen ever disagree on a library version, the screen reflects
what actually shipped in that build.

## Data and assets bundled in the app

### mledoze/countries (country data)

| | |
|---|---|
| Source | https://github.com/mledoze/countries |
| Version | Not recorded (v3-era schema; arrived in the project's initial commit `40a5fd3`) |
| Licence | Open Database License (ODbL) v1.0, https://opendatacommons.org/licenses/odbl/1-0/ (full text: [`data/LICENSE-ODbL.txt`](data/LICENSE-ODbL.txt)) |
| Used in | `app/src/main/assets/countries.json` (names, official names, alternative spellings, capitals, regions, subregions, UN membership). The app seeds its Room database from it on first launch. The emoji `flag` field in the file is no longer displayed. |
| Derived database | [`data/aliases.json`](data/aliases.json), published under ODbL 1.0 (see [`data/README.md`](data/README.md)) |

Required attribution (shown in the app's Credits screen):

> Contains information from mledoze/countries (https://github.com/mledoze/countries), made available under the Open Database License (ODbL) v1.0.

mledoze/countries excludes its flag images from the ODbL; GeoQuiz does not use
them.

### flag-icons (flag images)

| | |
|---|---|
| Source | https://github.com/lipis/flag-icons (npm package `flag-icons`) |
| Version | 7.5.0, tarball SHA-256 `c0b80bf0e08006a60f56621d6bc49f8c7131f4d1fef6737a165a673431f4b518` |
| Licence | MIT |
| Used in | `app/src/main/assets/flags/<cca3>.svg` (4x3 SVGs, unmodified, renamed by ISO alpha-3 code), fetched by `tools/flags/fetch_flags.py` |
| Licence copies | `app/src/main/assets/flags/LICENSE` (ships in the APK), `tools/flags/LICENSE.flag-icons` |

Required notice:

```
The MIT License (MIT)

Copyright (c) 2013 Panayiotis Lipiridis

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Assets used only by build tools (not shipped in the app)

### Lato fonts

| | |
|---|---|
| Source | Google Fonts repository, https://github.com/google/fonts/tree/5d3b76120a319730fda218cc7410174a462b32cb/ofl/lato |
| Version | google/fonts commit `5d3b76120a319730fda218cc7410174a462b32cb` (2026-03-27) |
| Author | Łukasz Dziedzic |
| Licence | SIL Open Font License 1.1, Reserved Font Name "Lato" |
| Used in | `tools/fonts/Lato-Regular.ttf`, `tools/fonts/Lato-Bold.ttf`, loaded by `generate_store_assets.py` and `generate_achievements_zip.py` to draw text in the Play Store icon, feature graphic and achievement icons |

Required notice: the full copyright notice and licence text travel with the
fonts in [`tools/fonts/OFL.txt`](tools/fonts/OFL.txt). The rendered store
images are not subject to the OFL. See [`tools/fonts/README.md`](tools/fonts/README.md)
for checksums.

## Software libraries

Versions are from [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
Compose artefacts take their versions from the Compose BOM. Transitive
dependencies (for example AndroidX libraries pulled in by Compose, Okio pulled
in by Coil) are covered by the in-app Open-source licences screen.

### Shipped in the app

| Library | Maven coordinates | Version | Licence | URL |
|---|---|---|---|---|
| AndroidX Core KTX | `androidx.core:core-ktx` | 1.15.0 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/core |
| AndroidX Lifecycle (runtime-ktx, runtime-compose, viewmodel-compose) | `androidx.lifecycle:*` | 2.8.7 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| AndroidX Activity Compose | `androidx.activity:activity-compose` | 1.9.3 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| Jetpack Compose (ui, ui-graphics, ui-tooling-preview, material3, material-icons-extended) | `androidx.compose:compose-bom` | BOM 2024.12.01 | Apache 2.0 | https://developer.android.com/jetpack/compose |
| Navigation Compose | `androidx.navigation:navigation-compose` | 2.8.5 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/navigation |
| Hilt / Dagger | `com.google.dagger:hilt-android` | 2.54 | Apache 2.0 | https://github.com/google/dagger |
| Hilt Navigation Compose | `androidx.hilt:hilt-navigation-compose` | 1.2.0 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/hilt |
| Room (runtime, ktx) | `androidx.room:*` | 2.6.1 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/room |
| DataStore Preferences | `androidx.datastore:datastore-preferences` | 1.1.1 | Apache 2.0 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Kotlin standard library | `org.jetbrains.kotlin:kotlin-stdlib` | 2.1.0 | Apache 2.0 | https://kotlinlang.org |
| kotlinx.coroutines (core, android) | `org.jetbrains.kotlinx:kotlinx-coroutines-*` | 1.10.1 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| kotlinx.serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | Apache 2.0 | https://github.com/Kotlin/kotlinx.serialization |
| Coil (coil-compose, coil-svg) | `io.coil-kt.coil3:*` | 3.1.0 | Apache 2.0 | https://github.com/coil-kt/coil |
| AndroidSVG (transitive, via coil-svg) | `com.caverock:androidsvg-aar` | 1.4 | Apache 2.0 | https://github.com/BigBadaboom/androidsvg |
| Google Mobile Ads SDK (AdMob) | `com.google.android.gms:play-services-ads` | 23.6.0 | Google proprietary ([Android SDK licence](https://developer.android.com/studio/terms) and [Google Mobile Ads SDK terms](https://developers.google.com/admob/terms)) | https://developers.google.com/admob/android |
| Google User Messaging Platform (consent) | `com.google.android.ump:user-messaging-platform` | 3.2.0 | Google proprietary (Android SDK licence) | https://developers.google.com/admob/android/privacy |
| Google Play Games Services v2 | `com.google.android.gms:play-services-games-v2` | 21.0.0 | Google proprietary (Android SDK licence) | https://developer.android.com/games/pgs/overview |
| Google Play Billing Library | `com.android.billingclient:billing-ktx` | 8.3.0 | Google proprietary ([Play Billing Library terms](https://developer.android.com/google/play/billing/integrate)) | https://developer.android.com/google/play/billing |
| AboutLibraries (Open-source licences screen) | `com.mikepenz:aboutlibraries-compose-m3` (+ `aboutlibraries-core`) | 11.6.3 | Apache 2.0 | https://github.com/mikepenz/AboutLibraries |

### Build and test only (not shipped)

| Tool | Version | Licence | URL |
|---|---|---|---|
| Android Gradle Plugin | 8.9.3 | Apache 2.0 | https://developer.android.com/build |
| Kotlin Gradle plugins (android, compose, serialization) | 2.1.0 | Apache 2.0 | https://kotlinlang.org |
| AboutLibraries Gradle plugin | 11.6.3 | Apache 2.0 | https://github.com/mikepenz/AboutLibraries |
| KSP | 2.1.0-1.0.29 | Apache 2.0 | https://github.com/google/ksp |
| Hilt compiler / Room compiler | 2.54 / 2.6.1 | Apache 2.0 | see above |
| Compose UI Tooling (debug builds only) | BOM 2024.12.01 | Apache 2.0 | https://developer.android.com/jetpack/compose |
| JUnit | 4.13.2 | Eclipse Public License 1.0 | https://junit.org/junit4/ |
| MockK | 1.13.14 | Apache 2.0 | https://mockk.io |
| Turbine | 1.2.0 | Apache 2.0 | https://github.com/cashapp/turbine |
| kotlinx-coroutines-test | 1.10.1 | Apache 2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Pillow (store asset scripts) | not pinned | MIT-CMU (HPND) | https://python-pillow.org |

### Apache License 2.0 notice

The Apache 2.0 libraries above are used under the Apache License, Version 2.0.
You may obtain a copy of the licence at
https://www.apache.org/licenses/LICENSE-2.0. They are distributed on an "AS IS"
basis, without warranties or conditions of any kind. The full licence texts
and any NOTICE files are shown in the app's Open-source licences screen.

## Planned sources (not used yet)

Later phases plan to add Natural Earth (public domain) map data and UN M49
region classifications. They will be added here and to `data/SOURCES.md` when
they are bundled; neither is used today.
